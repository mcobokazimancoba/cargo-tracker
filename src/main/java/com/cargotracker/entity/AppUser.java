package com.cargotracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * A system user — a booking agent, carrier operator, or administrator.
 *
 * <p>Named {@code AppUser} to avoid collision with PostgreSQL's reserved
 * keyword {@code USER} and the common JPA pitfall of naming an entity "User"
 * which maps to the DB keyword in some dialects.
 *
 * <p>Password storage:
 * This entity stores only a BCrypt hash. The raw password never touches the
 * entity. Hashing happens in the service layer before the entity is constructed.
 * This is the correct professional pattern — entities must never hold plaintext
 * credentials even transiently.
 */
@Entity
@Table(
    name = "app_users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_email",    columnNames = "email"),
        @UniqueConstraint(name = "uq_user_username", columnNames = "username")
    },
    indexes = {
        @Index(name = "idx_user_role",   columnList = "role"),
        @Index(name = "idx_user_active", columnList = "active")
    }
)
public class AppUser {

    public enum Role {
        /** Can book cargo, view own shipments. */
        CUSTOMER,
        /** Can update tracking events, manage all cargos. */
        OPERATOR,
        /** Full access including user management and analytics. */
        ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "app_user_id_seq", allocationSize = 10)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(name = "username", nullable = false, length = 50, updatable = false)
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * PBKDF2WithHmacSHA256 hash — format: base64(salt) + ":" + base64(key).
     * base64(16 bytes) = 24 chars, base64(32 bytes) = 44 chars, separator = 1.
     * Total = 69 chars. Length 128 gives ample headroom for any future algorithm change.
     * Never a plaintext password — hashing occurs in AuthService before this is set.
     */
    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    private void onPrePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    protected AppUser() {}

    public AppUser(String username, String email, String passwordHash,
                   String fullName, Role role) {
        this.username     = username;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.fullName     = fullName;
        this.role         = role;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long getId()                       { return id; }
    public String getUsername()               { return username; }
    public String getEmail()                  { return email; }
    public String getPasswordHash()           { return passwordHash; }
    public String getFullName()               { return fullName; }
    public Role getRole()                     { return role; }
    public boolean isActive()                 { return active; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public LocalDateTime getLastLoginAt()     { return lastLoginAt; }

    public void setEmail(String email)                    { this.email = email; }
    public void setPasswordHash(String passwordHash)      { this.passwordHash = passwordHash; }
    public void setFullName(String fullName)              { this.fullName = fullName; }
    public void setRole(Role role)                        { this.role = role; }
    public void setActive(boolean active)                 { this.active = active; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    // ── Object contract ───────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUser other)) return false;
        return username != null && username.equals(other.username);
    }

    @Override
    public int hashCode() {
        return username == null ? 0 : username.hashCode();
    }

    @Override
    public String toString() {
        return "AppUser{id=" + id + ", username='" + username + "', role=" + role + "}";
    }
}