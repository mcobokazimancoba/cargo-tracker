package com.cargotracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Single-use token a user clicks to prove they own the email address they
 * registered with.
 *
 * <p>Mirrors {@link PasswordResetToken} on purpose — both are short-lived
 * bearer credentials with the same lifecycle (created on demand, consumed
 * once, deleted on use). They are kept as separate entities rather than
 * a unified {@code AuthToken(type)} because:
 * <ul>
 *   <li>The TTLs differ (15 min for reset, 24 h for verification —
 *       onboarding needs more time than a panicky "I forgot it" click).</li>
 *   <li>The downstream consequences differ (a leaked reset token lets an
 *       attacker take over an existing account; a leaked verify token
 *       merely confirms an email the attacker chose). Treating them as
 *       distinct types makes it impossible to accidentally accept the
 *       wrong one in either flow.</li>
 * </ul>
 */
@Entity
@Table(
    name = "email_verification_tokens",
    indexes = @Index(name = "idx_evt_token", columnList = "token", unique = true)
)
public class EmailVerificationToken {

    /** Time-to-live — generous enough that someone can verify after a meeting. */
    public static final long TTL_HOURS = 24L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // Explicit name="created_at" because the DB column is snake_case (per
    // db/schema.sql) but the field is camelCase. JPA's default mapping uses
    // the field name verbatim, so without this annotation EclipseLink would
    // generate INSERT INTO ... (createdAt) which PostgreSQL folds to
    // "createdat" (no underscore) and rejects with "column does not exist".
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean used = false;

    protected EmailVerificationToken() {}

    public EmailVerificationToken(String token, AppUser user) {
        this.token     = token;
        this.user      = user;
        this.createdAt = LocalDateTime.now();
        this.used      = false;
    }

    public Long          getId()        { return id; }
    public String        getToken()     { return token; }
    public AppUser       getUser()      { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean       isUsed()       { return used; }
    public void          markUsed()     { this.used = true; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(createdAt.plusHours(TTL_HOURS));
    }
}
