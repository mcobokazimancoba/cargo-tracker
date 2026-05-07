package com.cargotracker.service;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.entity.AppUser;
import com.cargotracker.entity.PasswordResetToken;
import com.cargotracker.exception.Exceptions;
import com.cargotracker.repository.AppUserRepository;
import com.cargotracker.repository.PasswordResetTokenRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@ApplicationScoped
public class AuthService {

    public static final long SESSION_SECONDS = 8L * 60 * 60;

    private static final int    SALT_BYTES = 16;
    private static final int    ITERATIONS = 310_000;
    private static final int    KEY_BITS   = 256;
    private static final String ALGORITHM  = "PBKDF2WithHmacSHA256";
    private static final String HASH_SEP   = ":";
    private static final String TOKEN_SEP  = ".";

    @Inject
    private AppUserRepository userRepository;

    @Inject
    private PasswordResetTokenRepository resetTokenRepository;

    @Inject
    private MailService mailService;

    @Transactional
    public Responses.User register(@NotNull @Valid Requests.Register request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw Exceptions.duplicate("User", "username", request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw Exceptions.duplicate("User", "email", request.getEmail());
        }

        String passwordHash = hashPassword(request.getPassword());

        AppUser user = new AppUser(
                request.getUsername(),
                request.getEmail().toLowerCase(),
                passwordHash,
                request.getFullName(),
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

        System.out.println("[AuthService] resetPassword called with token: " + request.getToken());

        PasswordResetToken prt = resetTokenRepository
                .findByToken(request.getToken())
                .orElseThrow(() -> {
                    System.out.println("[AuthService] Token NOT found in DB");
                    return Exceptions.badRequest("Invalid or expired reset token");
                });

        System.out.println("[AuthService] Token found, expired=" + prt.isExpired() + ", used=" + prt.isUsed());

        if (prt.isUsed() || prt.isExpired()) {
            throw Exceptions.badRequest("Invalid or expired reset token");
        }

        // Fetch user directly to ensure it is a managed entity in this transaction
        AppUser user = userRepository.findById(prt.getUser().getId())
                .orElseThrow(() -> {
                    System.out.println("[AuthService] User NOT found for token");
                    return Exceptions.badRequest("User not found");
                });

        System.out.println("[AuthService] Updating password for user: " + user.getUsername());

        user.setPasswordHash(hashPassword(request.getNewPassword()));

        userRepository.update(user);  // explicitly persist the change

        resetTokenRepository.deleteAllForUser(user.getId());

        System.out.println("[AuthService] Password updated and token deleted successfully");
    }

    // ── Token generation ──────────────────────────────────────────────────────

    private String generateToken(String username) {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);

        long issuedAt = System.currentTimeMillis() / 1000L;

        String payload = username
                + TOKEN_SEP + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
                + TOKEN_SEP + issuedAt;

        return Base64.getUrlEncoder().withoutPadding()
                     .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public String validateToken(@NotBlank String token) {
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);

            int lastDot = payload.lastIndexOf(TOKEN_SEP);
            if (lastDot < 0) throw Exceptions.unauthorized("Malformed token");

            int secondLastDot = payload.lastIndexOf(TOKEN_SEP, lastDot - 1);
            if (secondLastDot < 0) throw Exceptions.unauthorized("Malformed token");

            String username    = payload.substring(0, secondLastDot);
            String issuedAtStr = payload.substring(lastDot + 1);

            if (username.isBlank()) throw Exceptions.unauthorized("Malformed token");

            long issuedAt   = Long.parseLong(issuedAtStr);
            long nowSeconds = System.currentTimeMillis() / 1000L;

            if (nowSeconds - issuedAt > SESSION_SECONDS) {
                throw Exceptions.unauthorized("Token has expired — please log in again");
            }

            return username;

        } catch (IllegalArgumentException e) {
            throw Exceptions.unauthorized("Invalid token format");
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