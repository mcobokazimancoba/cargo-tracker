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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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

    /**
     * GET /api/auth/verify?token=...
     *
     * Consumes the email-verification token mailed at registration. Returns
     * a small HTML page (not JSON) because users land here by clicking a
     * link in their inbox — they expect a human-readable result, not raw
     * JSON. The page contains no app-specific styling on purpose: keeping
     * it self-contained means the link works even if the SPA is unreachable
     * at the time of click.
     */
    @GET
    @Path("verify")
    @Produces(MediaType.TEXT_HTML)
    public Response verifyEmail(@QueryParam("token") String token) {
        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(htmlPage("Verification failed",
                            "The verification link is missing its token. " +
                            "Try clicking the link in your email again."))
                    .build();
        }
        try {
            String username = authService.verifyEmail(token);
            return Response.ok(htmlPage("Email verified",
                    "Welcome, " + escape(username) + ". You can now log in."))
                    .build();
        } catch (RuntimeException e) {
            // verifyEmail throws a generic AppException for any failure cause
            // (unknown / used / expired) — pass that message straight through
            // since it has already been sanitised to be enumeration-safe.
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(htmlPage("Verification failed", escape(e.getMessage())))
                    .build();
        }
    }

    /**
     * POST /api/auth/resend-verification
     * Always returns 200 — never confirms whether the address is registered
     * or whether it's still unverified. Same enumeration-defence pattern as
     * forgot-password.
     */
    @POST
    @Path("resend-verification")
    public Response resendVerification(@Valid Requests.ResendVerification request) {
        authService.resendVerification(request);
        return Response.ok(Map.of(
                "message",
                "If that address belongs to an unverified account, "
              + "a new verification link has been sent."
        )).build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Tiny self-contained HTML page used by the email-verify endpoint. */
    private static String htmlPage(String heading, String body) {
        return """
            <!doctype html>
            <html lang="en">
            <head><meta charset="utf-8"><title>%s — Cargo Tracker</title></head>
            <body style="font-family: sans-serif; max-width: 480px; margin: 4rem auto;
                         padding: 2rem; border: 1px solid #ddd; border-radius: 8px;">
              <h1 style="font-size: 1.4rem; margin: 0 0 0.5rem;">%s</h1>
              <p style="color: #444;">%s</p>
              <p><a href="/Cargo_Tracker_System/index.html">Return to Cargo Tracker</a></p>
            </body>
            </html>
            """.formatted(heading, heading, body);
    }

    /**
     * Minimal HTML escaping for the ONE place we render user input on the
     * verify page (the username after success). Five chars cover every XSS
     * vector that matters for an attribute-free, script-free template.
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}   // ← closing brace — everything is inside the class