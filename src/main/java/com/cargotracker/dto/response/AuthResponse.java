package com.cargotracker.dto.response;

public class AuthResponse {
    private final String token;
    private final String username;
    private final String role;
    private final long expiresInSeconds;

    public AuthResponse(String token, String username, String role, long expiresInSeconds) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
}