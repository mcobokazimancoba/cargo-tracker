package com.cargotracker.api;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.service.AuthService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private AuthService authService;

    /**
     * POST /api/auth/register
     * HTTP 201 Created on success.
     * HTTP 409 Conflict if username or email is already taken.
     */
    @POST
    @Path("register")
    public Response register(@Valid Requests.Register request) {
        Responses.User created = authService.register(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * POST /api/auth/login
     * HTTP 200 OK on success.
     * HTTP 401 Unauthorized on invalid credentials or deactivated account.
     */
    @POST
    @Path("login")
    public Response login(@Valid Requests.Login request) {
        Responses.Auth auth = authService.login(request);
        return Response.ok(auth).build();
    }

    /**
     * POST /api/auth/forgot-password
     * Always returns HTTP 200 regardless of whether the address exists
     * to prevent account enumeration.
     */
    @POST
    @Path("forgot-password")
    public Response forgotPassword(@Valid Requests.ForgotPassword request) {  // ← FIXED
        authService.forgotPassword(request);
        return Response.ok(
            Map.of("message", "If that address is registered, a reset link has been sent.")
        ).build();
    }

    /**
     * POST /api/auth/reset-password
     * HTTP 200 on success.
     * HTTP 400 if the token is missing, already used, or expired.
     */
    @POST
    @Path("reset-password")
    public Response resetPassword(@Valid Requests.ResetPassword request) throws Exception {
        authService.resetPassword(request);
        return Response.ok(
            Map.of("message", "Password updated successfully. Please log in.")
        ).build();
    }
}   // ← closing brace — everything is inside the class