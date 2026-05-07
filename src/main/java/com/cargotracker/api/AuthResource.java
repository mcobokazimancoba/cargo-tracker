package com.cargotracker.api;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.exception.AppException;
import com.cargotracker.service.AuthService;
import com.cargotracker.service.LoginThrottleService;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private AuthService authService;

    @Inject
    private LoginThrottleService loginThrottle;

    @Context
    private HttpServletRequest httpRequest;

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
     * HTTP 429 Too Many Requests once the (ip, username) brute-force budget
     * is exhausted; the Retry-After header advises when to retry.
     *
     * Order of operations matters: the throttle check happens BEFORE we hit
     * AuthService, so an attacker probing an account doesn't get a timing
     * signal from password verification ("known account: 200ms reply, unknown
     * account: 5ms reply"). Locked buckets all reply at the same speed.
     */
    @POST
    @Path("login")
    public Response login(@Valid Requests.Login request) {
        String ip = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
        String username = request.getUsername();

        if (loginThrottle.isLocked(ip, username)) {
            long retryAfter = loginThrottle.retryAfterSeconds(ip, username);
            return Response.status(429)
                    .header("Retry-After", retryAfter)
                    .entity(new Responses.Error(429, "Too Many Requests",
                            "Too many failed login attempts — retry in "
                            + retryAfter + " seconds."))
                    .build();
        }

        try {
            Responses.Auth auth = authService.login(request);
            // Successful login: clear past failures so a user who fat-fingered
            // their password three times then got it right isn't one typo
            // away from being locked out next time.
            loginThrottle.clearFailures(ip, username);
            return Response.ok(auth).build();
        } catch (AppException e) {
            // Only count failures from invalid credentials / disabled account
            // (401). Validation failures (400) and other exceptions are not
            // brute-force signals and shouldn't fill the bucket.
            if (e.getHttpStatusCode() == 401) {
                loginThrottle.recordFailure(ip, username);
            }
            throw e;  // let AppExceptionMapper render the response
        }
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