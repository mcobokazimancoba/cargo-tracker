package com.cargotracker.dto.response;

import com.cargotracker.entity.AppUser;
import java.time.LocalDateTime;

public class UserResponse {
    private final Long id;
    private final String username;
    private final String email;
    private final String fullName;
    private final String role;
    private final boolean active;
    private final LocalDateTime createdAt;

    public UserResponse(Long id, String username, String email, String fullName,
                        String role, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static UserResponse from(AppUser u) {
        return new UserResponse(
            u.getId(), u.getUsername(), u.getEmail(),
            u.getFullName(), u.getRole().name(), u.isActive(), u.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}