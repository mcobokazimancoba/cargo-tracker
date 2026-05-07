package com.cargotracker.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {  // ← Must be PUBLIC

    // Username whitelist: letters, digits, dot, underscore, hyphen.
    // Why a whitelist not a blacklist: a blacklist ("no spaces, no <") inevitably
    // misses something (zero-width unicode, control chars, RTL overrides). A
    // whitelist of safe characters is unambiguous and easy to defend in code review.
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
             message = "Username may only contain letters, digits, dot, underscore or hyphen")
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    @Size(max = 100)
    private String fullName;

    public RegisterRequest() {}

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }

    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}