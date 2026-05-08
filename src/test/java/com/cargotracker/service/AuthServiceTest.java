package com.cargotracker.service;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.entity.AppUser;
import com.cargotracker.entity.EmailVerificationToken;
import com.cargotracker.exception.AppException;
import com.cargotracker.repository.AppUserRepository;
import com.cargotracker.repository.EmailVerificationTokenRepository;
import com.cargotracker.repository.PasswordResetTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}'s registration and login flows.
 *
 * <p>Why unit tests, not integration tests:
 * AuthService has clean dependency injection points (repositories, mail
 * service) that we can mock. The behaviour we want to verify — hashing,
 * normalisation, JWT round-trip, error mapping — is pure business logic
 * that doesn't need a JTA transaction or a database to exercise. Spinning
 * up Arquillian for this would be a 10× slowdown for no extra coverage.
 *
 * <p>The PBKDF2 hash takes ~100ms per call, so each login-related test
 * pays that cost twice (once to set up the stored hash, once to verify).
 * Acceptable at this scale; if the test suite ever grows we'd lower the
 * iteration count via a test-only constant.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private EmailVerificationTokenRepository verificationTokenRepository;
    @Mock private MailService mailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Force a known JWT secret so token round-trip is deterministic and
        // we don't fall into the random-per-JVM dev fallback (which would
        // still work, but the warning log is noisy and the secret would
        // change between test classes).
        System.setProperty("jwt.secret",
                "test-jwt-secret-must-be-at-least-thirty-two-bytes-long-yes");
        authService.initJwtKey();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: persists a CUSTOMER with hashed password, lowercased email, and triggers verification mail")
    void register_success() {
        Requests.Register req = new Requests.Register();
        req.setUsername("alice");
        req.setEmail("Alice@Example.COM");          // mixed case on input
        req.setPassword("supersecretpw");
        req.setFullName("Alice Andersson");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        Responses.User result = authService.register(req);

        assertNotNull(result);
        assertEquals("alice", result.getUsername());

        // Verify the entity persisted: password hashed (NOT plaintext), email
        // lowercased before storage, role defaulted to CUSTOMER.
        verify(userRepository).save(argThat(u ->
                u.getUsername().equals("alice")
             && u.getEmail().equals("alice@example.com")
             && !u.getPasswordHash().equals("supersecretpw")    // i.e. hashing happened
             && u.getPasswordHash().contains(":")               // PBKDF2 format: salt:key
             && u.getRole() == AppUser.Role.CUSTOMER
             && !u.isEmailVerified()                            // step 9: starts unverified
        ));

        // Step 9: a verification email must be sent as part of registration.
        verify(verificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(mailService).sendVerificationEmail(eq("alice@example.com"),
                                                  eq("Alice Andersson"),
                                                  anyString());
    }

    @Test
    @DisplayName("register: 409 on duplicate username")
    void register_duplicateUsername_throws() {
        Requests.Register req = new Requests.Register();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("supersecretpw");
        req.setFullName("Alice");

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.register(req));
        assertEquals(409, ex.getHttpStatusCode());
        verify(userRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: issues a JWT that the same service round-trips back to the same username")
    void login_success_jwtRoundtrips() {
        // Set up a real PBKDF2 hash via the service so verify() goes through
        // the same code path that production does.
        AppUser user = new AppUser(
                "alice",
                "alice@example.com",
                authService.hashPassword("supersecretpw"),
                "Alice",
                AppUser.Role.CUSTOMER);
        user.setEmailVerified(true);            // step 9: login requires this

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Requests.Login req = new Requests.Login();
        req.setUsername("alice");
        req.setPassword("supersecretpw");

        Responses.Auth result = authService.login(req);

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
        assertEquals("CUSTOMER", result.getRole());
        assertNotNull(result.getToken());

        // The token is a real JWT — three dot-separated base64url segments —
        // not a single base64 blob like the broken pre-step-2 format.
        assertEquals(3, result.getToken().split("\\.").length,
                "expected three JWT segments (header.payload.signature)");

        // And it actually verifies against the same secret/issuer.
        assertEquals("alice", authService.validateToken(result.getToken()));
    }

    @Test
    @DisplayName("login: 401 on wrong password (and no token issued)")
    void login_wrongPassword_throws() {
        AppUser user = new AppUser(
                "alice",
                "alice@example.com",
                authService.hashPassword("supersecretpw"),
                "Alice",
                AppUser.Role.CUSTOMER);
        user.setEmailVerified(true);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Requests.Login req = new Requests.Login();
        req.setUsername("alice");
        req.setPassword("WRONG-password");

        AppException ex = assertThrows(AppException.class, () -> authService.login(req));
        assertEquals(401, ex.getHttpStatusCode());
        // Generic message — must NOT distinguish "unknown user" from "wrong password",
        // otherwise the response is an account-enumeration oracle.
        assertEquals("Invalid credentials", ex.getMessage());
    }

    // ── step 9: email verification ────────────────────────────────────────────

    @Test
    @DisplayName("login: 403 with verify-email message when password is correct but email not verified")
    void login_unverifiedEmail_blocked() {
        AppUser user = new AppUser(
                "alice",
                "alice@example.com",
                authService.hashPassword("supersecretpw"),
                "Alice",
                AppUser.Role.CUSTOMER);
        // emailVerified left at the default (false)

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Requests.Login req = new Requests.Login();
        req.setUsername("alice");
        req.setPassword("supersecretpw");

        AppException ex = assertThrows(AppException.class, () -> authService.login(req));
        assertEquals(403, ex.getHttpStatusCode());
        assertEquals("Email not verified — check your inbox for the confirmation link",
                ex.getMessage());
    }

    @Test
    @DisplayName("login: unverified user with WRONG password gets the same generic 401, not the verify-email leak")
    void login_unverifiedEmail_wrongPassword_keepsGenericError() {
        // Threat model: if we returned "please verify" before checking the
        // password, an attacker could submit any password and learn that the
        // account exists in an unverified state — turning login into an
        // enumeration oracle. Password check MUST come first.
        AppUser user = new AppUser(
                "alice",
                "alice@example.com",
                authService.hashPassword("supersecretpw"),
                "Alice",
                AppUser.Role.CUSTOMER);
        // emailVerified left at the default (false)

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Requests.Login req = new Requests.Login();
        req.setUsername("alice");
        req.setPassword("WRONG-password");

        AppException ex = assertThrows(AppException.class, () -> authService.login(req));
        assertEquals(401, ex.getHttpStatusCode(),
                "wrong-password failure must not leak the unverified-account state");
        assertEquals("Invalid credentials", ex.getMessage());
    }

    // ── token forgery defence ─────────────────────────────────────────────────

    @Test
    @DisplayName("validateToken: rejects a token signed with a different secret (signature forgery)")
    void validateToken_wrongSignature_rejected() {
        // Issue a token under the current secret.
        AppUser user = new AppUser(
                "alice", "a@b.com",
                authService.hashPassword("pw-twelve-characters"),
                "Alice", AppUser.Role.CUSTOMER);
        user.setEmailVerified(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Requests.Login req = new Requests.Login();
        req.setUsername("alice");
        req.setPassword("pw-twelve-characters");
        String legitimateToken = authService.login(req).getToken();

        // Now rotate the secret as if a deployment had restarted with new config.
        System.setProperty("jwt.secret",
                "completely-different-secret-also-at-least-thirty-two-bytes");
        authService.initJwtKey();

        AppException ex = assertThrows(AppException.class,
                () -> authService.validateToken(legitimateToken));
        assertEquals(401, ex.getHttpStatusCode());
        assertNotEquals("Token has expired — please log in again", ex.getMessage(),
                "non-expired but invalid signature should not pretend to be expired");
    }
}
