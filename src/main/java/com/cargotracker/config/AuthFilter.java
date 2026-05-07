package com.cargotracker.config;

import com.cargotracker.entity.AppUser;
import com.cargotracker.repository.AppUserRepository;
import com.cargotracker.service.AuthService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Optional;

@Provider
@Priority(1000)
public class AuthFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION = "Authorization";
    public static final  String AUTHENTICATED_USER = "authenticatedUser";

    @Inject
    private AuthService authService;

    @Inject
    private AppUserRepository userRepository;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {

        String path   = ctx.getUriInfo().getPath();
        String method = ctx.getMethod();          // GET, POST, PUT, DELETE …

        // Strip leading slash (defensive — some containers include it)
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        boolean publicEndpoint = isPublicEndpoint(path, method);

        String authHeader = ctx.getHeaderString(AUTHORIZATION);
        boolean tokenPresent = authHeader != null && authHeader.startsWith(BEARER_PREFIX);

        // Private endpoint with no token → 401 immediately. Nothing else to do.
        if (!publicEndpoint && !tokenPresent) {
            abort401(ctx, "Missing or invalid Authorization header");
            return;
        }

        // Public endpoint with no token → genuine anonymous access. Allow.
        if (publicEndpoint && !tokenPresent) {
            return;
        }

        // From here on, a token IS present. Try to validate it.
        // Why bother on a public endpoint? So that a logged-in caller can be
        // identified for fine-grained checks downstream — e.g. "this cargo is
        // public for guests, but if a CUSTOMER is logged in they must be the
        // owner". Without this, AuthFilter would return early and hide the
        // caller's identity from CargoResource.
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            if (publicEndpoint) return;       // empty token + public path = anonymous
            abort401(ctx, "Empty token");
            return;
        }

        String username;
        try {
            username = authService.validateToken(token);
        } catch (Exception e) {
            // Bad/expired token on a public endpoint is NOT an error — treat
            // the caller as anonymous so the existing guest-tracking flow
            // doesn't break for users whose session lapsed in another tab.
            if (publicEndpoint) return;
            abort401(ctx, e.getMessage());
            return;
        }

        Optional<AppUser> userOpt = userRepository.findActiveByUsername(username);
        if (userOpt.isEmpty()) {
            if (publicEndpoint) return;       // unknown account on public path = anonymous
            abort401(ctx, "Invalid or inactive account");
            return;
        }

        ctx.setProperty(AUTHENTICATED_USER, userOpt.get());
    }

    /**
     * Returns true only for endpoints that genuinely require no authentication.
     *
     * THE BUG THAT WAS HERE:
     *   if (path.startsWith("cargos/CGO-")) return true;
     *
     * That made PUT /cargos/CGO-1001 and DELETE /cargos/CGO-1001 public too,
     * so the filter returned early without ever setting AUTHENTICATED_USER.
     * CargoResource.getUser() then found null and threw 401 — even for admins.
     *
     * FIX: cargo lookup is public only for GET requests. Every other HTTP method
     * (PUT, DELETE, POST) must carry a valid token.
     */
    private boolean isPublicEndpoint(String path, String method) {

        // Health check — always public
        if (path.equals("health")) return true;

        // Auth: login and register — always public
        if (path.startsWith("auth/")) return true;

        // Cargo tracking — PUBLIC for GET only (guests can track their shipment).
        // PUT / DELETE / POST on the same path still require a token.
        if ("GET".equalsIgnoreCase(method) && path.startsWith("cargos/CGO-")) return true;

        // Locations autocomplete — public for GET (used on the booking form)
        if ("GET".equalsIgnoreCase(method) &&
                (path.equals("locations") || path.startsWith("locations/"))) return true;

        return false;
    }

    private void abort401(ContainerRequestContext ctx, String message) {
        String safe = message == null
                ? "Unauthorized"
                : message.replace("\"", "'").replace("\n", " ").replace("\r", "");

        ctx.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + safe + "\"}")
                    .build()
        );
    }
}