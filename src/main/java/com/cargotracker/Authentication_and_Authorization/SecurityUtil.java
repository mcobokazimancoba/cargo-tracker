package com.cargotracker.Authentication_and_Authorization;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security utilities for token generation and validation.
 *
 * <p>In production, replace this with a proper JWT library like JJWT.
 * This implementation follows the same architectural patterns but is
 * simplified for demonstration.
 *
 * <p>Note: This class belongs in the same package as AuthFilter and RoleFilter.
 */
@ApplicationScoped
public class SecurityUtil {

    // Simple in-memory token store (in production, use JWT with signatures)
    private static final ConcurrentHashMap<String, TokenInfo> validTokens = new ConcurrentHashMap<>();

    private static final int TOKEN_EXPIRY_HOURS = 24;

    /**
     * Token information stored in memory.
     * In production with JWT, this would be unnecessary as the token itself
     * contains all claims and is cryptographically signed.
     */
    private record TokenInfo(String username, String role, LocalDateTime expiry) {}

    /**
     * Generates a token for an authenticated user.
     *
     * Format: username|role|expiry (base64 encoded)
     * In production: use JJWT to generate signed JWTs.
     *
     * @param username the authenticated user's username
     * @param role the user's role (ADMIN, OPERATOR, CUSTOMER)
     * @return a Base64-encoded token string
     */
    public String generateToken(String username, String role) {
        LocalDateTime expiry = LocalDateTime.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS);
        String rawToken = username + "|" + role + "|" + expiry.toString();

        // Simple encoding (not secure - use real JWT in production)
        String token = Base64.getEncoder().encodeToString(rawToken.getBytes());

        TokenInfo info = new TokenInfo(username, role, expiry);
        validTokens.put(token, info);

        return token;
    }

    /**
     * Validates a token and returns the username if valid.
     *
     * @param token the token to validate
     * @return the username if valid, null otherwise
     */
    public String validateToken(String token) {
        TokenInfo info = validTokens.get(token);
        if (info == null) {
            return null;
        }

        if (LocalDateTime.now().isAfter(info.expiry())) {
            validTokens.remove(token);
            return null;
        }

        return info.username();
    }

    /**
     * Invalidates a token (logout).
     *
     * @param token the token to invalidate
     */
    public void invalidateToken(String token) {
        validTokens.remove(token);
    }

    /**
     * Extracts role from a valid token.
     *
     * @param token the token
     * @return the role if the token exists, null otherwise
     */
    public String getRoleFromToken(String token) {
        TokenInfo info = validTokens.get(token);
        return info != null ? info.role() : null;
    }

    /**
     * Checks if a token is valid and not expired.
     *
     * @param token the token to check
     * @return true if valid, false otherwise
     */
    public boolean isValidToken(String token) {
        return validateToken(token) != null;
    }

    /**
     * Clears all tokens (useful for testing or emergency logout of all users).
     */
    public void clearAllTokens() {
        validTokens.clear();
    }
}