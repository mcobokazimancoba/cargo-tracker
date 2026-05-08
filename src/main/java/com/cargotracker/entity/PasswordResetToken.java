package com.cargotracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persists a short-lived, single-use token for password reset.
 *
 * <p>One row is created per reset request. The token is a 32-byte
 * cryptographically random value stored as a URL-safe Base64 string.
 * It is valid for {@value #TTL_MINUTES} minutes and is deleted on use.
 *
 * <p>A unique index on {@code token} allows AuthService to look up the
 * row in O(1) without scanning the table.
 */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = @Index(name = "idx_prt_token", columnList = "token", unique = true)
)
public class PasswordResetToken {

    /** Time-to-live for every reset token — 15 minutes. */
    public static final long TTL_MINUTES = 15L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * URL-safe Base64 representation of 32 random bytes.
     * Never reused — a new token is generated on every request.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** The user this token belongs to. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /**
     * When this token was created — used to enforce TTL_MINUTES.
     *
     * Explicit name="created_at" — see the same note on
     * {@link EmailVerificationToken#createdAt}: snake-case DB column,
     * camelCase Java field, must be mapped explicitly so EclipseLink
     * generates the right SQL.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Whether this token has already been consumed. */
    @Column(nullable = false)
    private boolean used = false;

    protected PasswordResetToken() {}

    public PasswordResetToken(String token, AppUser user) {
        this.token     = token;
        this.user      = user;
        this.createdAt = LocalDateTime.now();
        this.used      = false;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long          getId()        { return id; }
    public String        getToken()     { return token; }
    public AppUser       getUser()      { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean       isUsed()       { return used; }
    public void          markUsed()     { this.used = true; }

    /** Convenience — has the TTL window elapsed? */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(createdAt.plusMinutes(TTL_MINUTES));
    }
}