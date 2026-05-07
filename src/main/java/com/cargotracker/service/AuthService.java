package com.cargotracker.service;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.entity.AppUser;
import com.cargotracker.entity.PasswordResetToken;
import com.cargotracker.exception.Exceptions;
import com.cargotracker.repository.AppUserRepository;
import com.cargotracker.repository.PasswordResetTokenRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.logging.Logger;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    public static final long SESSION_SECONDS = 8L * 60 * 60;

    private static final int    SALT_BYTES = 16;
    private static final int    ITERATIONS = 310_000;
    private static final int    KEY_BITS   = 256;
    private static final String ALGORITHM  = "PBKDF2WithHmacSHA256";
    private static final String HASH_SEP   = ":";

    /**
     * HS256 requires a key of at least 256 bits = 32 bytes. JJWT will throw
     * WeakKeyException if we feed it a shorter one, but checking ourselves at
     * startup turns "first login fails in production" into "deploy fails fast".
     */
    private static final int    JWT_MIN_KEY_BYTES = 32;
    private static final String JWT_ISSUER        = "cargo-tracker";
    private static final String JWT_SECRET_PROP   = "jwt.secret";
    private static final String JWT_SECRET_ENV    = "JWT_SECRET";

    /** HMAC key derived from the configured secret. Resolved once at bean init. */
    private SecretKey jwtKey;

    @Inject
    private AppUserRepository userRepository;

    @Inject
    private PasswordResetTokenRepository resetTokenRepository;

    @Inject
    private MailService mailService;

    /**
     * Resolves the JWT signing secret once, at bean construction.
     *
     * Resolution order:
     *   1. -Djwt.secret=...   (GlassFish JVM option)
     *   2. JWT_SECRET=...     (process environment)
     *   3. randomly generated 32-byte key for THIS JVM (dev fallback only)
     *
     * Why a random dev fallback rather than a hard-coded default?
     * A hard-coded default would ship with every clone of this repo and be
     * the same on every developer's machine — anyone could forge a token
     * for any deployment that forgot to set the env var. A random per-JVM
     * key means the worst case in dev is "tokens stop working after a
     * GlassFish restart", which is a noisy, obvious failure mode rather
     * than a silent vulnerability. We log a stern WARNING so it cannot be
     * missed.
     */
    @PostConstruct
    void initJwtKey() {
        String configured = System.getProperty(JWT_SECRET_PROP);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(JWT_SECRET_ENV);
        }

        if (configured != null && !configured.isBlank()) {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < JWT_MIN_KEY_BYTES) {
                throw new IllegalStateException(
                    "JWT secret too short: HS256 requires >= " + JWT_MIN_KEY_BYTES
                    + " bytes (got " + bytes.length + "). "
                    + "Set " + JWT_SECRET_ENV + " to a long random string.");
            }
            this.jwtKey = Keys.hmacShaKeyFor(bytes);
            LOG.info("JWT signing key loaded from configuration.");
        } else {
            byte[] random = new byte[JWT_MIN_KEY_BYTES];
            new SecureRandom().nextBytes(random);
            this.jwtKey = Keys.hmacShaKeyFor(random);
            LOG.warning("==========================================================");
            LOG.warning(" JWT_SECRET is not set. Using a random per-JVM dev key.");
            LOG.warning(" Tokens issued by this instance will be invalidated on");
            LOG.warning(" every GlassFish restart. DO NOT run like this in prod.");
            LOG.warning("==========================================================");
        }
    }

    @Transactional
    public Responses.User register(@NotNull @Valid Requests.Register request) {

        // Normalise BEFORE the duplicate check.
        // Why: the entity is persisted with a lowercased email, but if we check
        // `existsByEmail` against the raw input then "Foo@bar.com" and
        // "foo@bar.com" would both pass the uniqueness check and create two
        // accounts that collide on the unique index at insert time
        // (or worse, get inserted on different DBs that fold case differently).
        String normalisedEmail = request.getEmail().trim().toLowerCase();
        String normalisedUsername = request.getUsername().trim();

        if (userRepository.existsByUsername(normalisedUsername)) {
            throw Exceptions.duplicate("User", "username", normalisedUsername);
        }
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw Exceptions.duplicate("User", "email", normalisedEmail);
        }

        String passwordHash = hashPassword(request.getPassword());

        AppUser user = new AppUser(
                normalisedUsername,
                normalisedEmail,
                passwordHash,
                request.getFullName().trim(),
                AppUser.Role.CUSTOMER
        );

        userRepository.save(user);
        return Responses.User.from(user);
    }

    @Transactional
    public Responses.Auth login(@NotNull @Valid Requests.Login request) {

        AppUser user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> Exceptions.unauthorized("Invalid credentials"));

        if (!user.isActive()) {
            throw Exceptions.unauthorized("Account is deactivated — contact an administrator");
        }

        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw Exceptions.unauthorized("Invalid credentials");
        }

        user.setLastLoginAt(LocalDateTime.now());

        String token = generateToken(user.getUsername());

        return new Responses.Auth(token, user.getUsername(), user.getRole().name(), SESSION_SECONDS);
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    @Transactional
    public void forgotPassword(@NotNull @Valid Requests.ForgotPassword request) {

        userRepository.findByEmail(request.getEmail().toLowerCase()).ifPresent(user -> {

            if (!user.isActive()) {
                return;
            }

            resetTokenRepository.deleteAllForUser(user.getId());

            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding()
                                    .encodeToString(randomBytes);

            resetTokenRepository.save(new PasswordResetToken(rawToken, user));

            mailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    rawToken
            );
        });
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(@NotNull @Valid Requests.ResetPassword request) throws Exception {

        // Note: deliberately NOT logging the raw token. A reset token is a
        // bearer credential — anyone holding it can change the password —
        // so it must never end up in log files. We log the user ID once we
        // have one, since that's safe and useful for support.
        PasswordResetToken prt = resetTokenRepository
                .findByToken(request.getToken())
                .orElseThrow(() -> Exceptions.badRequest("Invalid or expired reset token"));

        if (prt.isUsed() || prt.isExpired()) {
            // Same generic message as "token not found" — don't leak whether
            // the failure was unknown vs. consumed vs. expired.
            throw Exceptions.badRequest("Invalid or expired reset token");
        }

        // Fetch user directly to ensure it is a managed entity in this transaction
        AppUser user = userRepository.findById(prt.getUser().getId())
                .orElseThrow(() -> Exceptions.badRequest("Invalid or expired reset token"));

        user.setPasswordHash(hashPassword(request.getNewPassword()));
        userRepository.update(user);  // explicitly persist the change
        resetTokenRepository.deleteAllForUser(user.getId());

        LOG.info(() -> "Password reset completed for user id=" + user.getId());
    }

    // ── Token generation ──────────────────────────────────────────────────────

    /**
     * Issues a signed JWT for a successfully authenticated user.
     *
     * Algorithm:    HS256 (HMAC-SHA-256, symmetric)
     * Payload:      sub=<username>, iss=cargo-tracker, iat, exp
     * Lifetime:     {@link #SESSION_SECONDS}
     *
     * The username goes in the standard `sub` claim rather than a custom one
     * so any standard JWT inspector (jwt.io, libraries) shows it correctly.
     * Roles are NOT embedded — we look up the live AppUser on every request
     * in AuthFilter, so a role change takes effect on the next request without
     * waiting for the token to expire.
     */
    private String generateToken(String username) {
        long nowMillis = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(JWT_ISSUER)
                .subject(username)
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(nowMillis + SESSION_SECONDS * 1000L))
                .signWith(jwtKey, Jwts.SIG.HS256)
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Verifies the JWT signature and returns the {@code sub} (username) claim.
     * Throws an unauthorized exception with a non-leaky message on any failure
     * (bad signature, malformed token, wrong issuer, expired, etc.).
     *
     * Why narrow the message? An attacker probing the auth endpoint should
     * learn only "your token is bad", not which specific check rejected it.
     * The detailed cause is logged at FINE level for debugging.
     */
    public String validateToken(@NotBlank String token) {
        try {
            Claims claims = Jwts.parser()
                    .requireIssuer(JWT_ISSUER)
                    .verifyWith(jwtKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                throw Exceptions.unauthorized("Invalid token");
            }
            return username;

        } catch (ExpiredJwtException e) {
            // Distinct message because clients legitimately need to know to re-login.
            throw Exceptions.unauthorized("Token has expired — please log in again");
        } catch (JwtException | IllegalArgumentException e) {
            LOG.fine(() -> "JWT rejected: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            throw Exceptions.unauthorized("Invalid token");
        }
    }

    // ── Password hashing ──────────────────────────────────────────────────────

    String hashPassword(String plaintext) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);

            javax.crypto.spec.PBEKeySpec spec =
                    new javax.crypto.spec.PBEKeySpec(plaintext.toCharArray(), salt, ITERATIONS, KEY_BITS);

            javax.crypto.SecretKeyFactory factory =
                    javax.crypto.SecretKeyFactory.getInstance(ALGORITHM);

            byte[] derivedKey = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();

            return Base64.getEncoder().encodeToString(salt)
                    + HASH_SEP
                    + Base64.getEncoder().encodeToString(derivedKey);

        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    boolean verifyPassword(String plaintext, String storedHash) {
        try {
            String[] parts = storedHash.split(HASH_SEP, 2);
            if (parts.length != 2) return false;

            byte[] salt     = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);

            javax.crypto.spec.PBEKeySpec spec =
                    new javax.crypto.spec.PBEKeySpec(plaintext.toCharArray(), salt, ITERATIONS, KEY_BITS);

            javax.crypto.SecretKeyFactory factory =
                    javax.crypto.SecretKeyFactory.getInstance(ALGORITHM);

            byte[] actual = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();

            return MessageDigest.isEqual(expected, actual);

        } catch (Exception e) {
            return false;
        }
    }
}