package com.cargotracker.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Single public access point for all request DTOs.
 *
 * Usage:
 *   public Response register(@Valid Requests.Register request) { ... }
 */
public final class Requests {

    private Requests() {}

    public static final class Booking      extends BookingRequest      {}
    public static final class CargoUpdate  extends CargoUpdateRequest  {}
    public static final class TrackingEvent extends TrackingEventRequest {}
    public static final class Location     extends LocationRequest     {}
    public static final class Register     extends RegisterRequest     {}
    public static final class Login        extends LoginRequest        {}

    // ── POST /api/auth/forgot-password ────────────────────────────────────
    public static class ForgotPassword {

        @NotBlank
        @Email
        private String email;

        public ForgotPassword() {}

        public String getEmail()             { return email; }
        public void   setEmail(String email) { this.email = email; }
    }

    // ── POST /api/auth/reset-password ─────────────────────────────────────
    public static class ResetPassword {

        @NotBlank
        private String token;

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;

        public ResetPassword() {}

        public String getToken()                 { return token; }
        public void   setToken(String token)     { this.token = token; }
        public String getNewPassword()           { return newPassword; }
        public void   setNewPassword(String pw)  { this.newPassword = pw; }
    }
}