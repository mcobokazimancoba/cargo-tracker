package com.cargotracker.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Base DTO for POST /api/auth/forgot-password.
 * Exposed via {@link Requests.ForgotPassword}.
 */
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    private String email;

    public ForgotPasswordRequest() {}

    public String getEmail()           { return email; }
    public void setEmail(String email) { this.email = email; }
}