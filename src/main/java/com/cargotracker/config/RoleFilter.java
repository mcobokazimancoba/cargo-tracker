package com.cargotracker.config;

import com.cargotracker.entity.AppUser;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enforces role-based access control (RBAC) after {@link AuthFilter} has
 * resolved and attached the authenticated user to the request context.
 *
 * <p>Filter execution order:
 * JAX-RS does not guarantee filter execution order by annotation alone.
 * GlassFish 7 respects {@code @Priority} on filters. AuthFilter runs first
 * (priority 1000), RoleFilter second (priority 2000). If AuthFilter aborts the
 * request, RoleFilter never executes.
 *
 * <p>Role matrix (enforced below):
 * <pre>
 *  Path pattern                       Method   Minimum role
 *  ─────────────────────────────────────────────────────────
 *  /api/cargos                        POST     CUSTOMER
 *  /api/cargos/{tn}                   GET      PUBLIC (no auth)
 *  /api/cargos/{tn}                   PUT      OPERATOR
 *  /api/cargos/{tn}                   DELETE   ADMIN
 *  /api/cargos/{tn}/events            POST     OPERATOR
 *  /api/analytics/*                   GET      OPERATOR
 *  /api/admin/*                       *        ADMIN
 * </pre>
 */
@Provider
@Priority(2000)
public class RoleFilter implements ContainerRequestFilter {

    /*
     * Role hierarchy: ADMIN > OPERATOR > CUSTOMER
     * A higher ordinal means more permissions.
     * ADMIN can do everything OPERATOR can, and OPERATOR can do everything CUSTOMER can.
     */
    private static final Map<AppUser.Role, Integer> ROLE_LEVEL = Map.of(
            AppUser.Role.CUSTOMER, 1,
            AppUser.Role.OPERATOR, 2,
            AppUser.Role.ADMIN,    3
    );

    /**
     * Route rules as a simple ordered list.
     * Evaluated top-to-bottom; first match wins.
     */
    private static final RouteRule[] RULES = {
        // Analytics — OPERATOR and above
        new RouteRule("GET",    Pattern.compile("analytics.*"),          AppUser.Role.OPERATOR),
        // Cargo creation — any authenticated user
        new RouteRule("POST",   Pattern.compile("cargos"),               AppUser.Role.CUSTOMER),
        // Tracking event recording — OPERATOR and above
        new RouteRule("POST",   Pattern.compile("cargos/.+/events"),     AppUser.Role.OPERATOR),
        // Cargo update — OPERATOR and above
        new RouteRule("PUT",    Pattern.compile("cargos/.+"),            AppUser.Role.OPERATOR),
        // Cargo cancellation / deletion — ADMIN only
        new RouteRule("DELETE", Pattern.compile("cargos/.+"),            AppUser.Role.ADMIN),
        // User management — ADMIN only
        new RouteRule(".*",     Pattern.compile("admin/.*"),             AppUser.Role.ADMIN),
    };

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path   = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        // If no user is attached, AuthFilter already aborted — nothing to do here
        Object userProp = requestContext.getProperty(AuthFilter.AUTHENTICATED_USER);
        if (userProp == null) {
            return;
        }

        AppUser user = (AppUser) userProp;

        for (RouteRule rule : RULES) {
            if (rule.matches(method, path)) {
                if (!hasRequiredRole(user.getRole(), rule.minimumRole())) {
                    abortForbidden(requestContext,
                        "Role " + user.getRole() + " is not permitted to " +
                        method + " /" + path);
                }
                return; // first match, stop evaluating
            }
        }
        // No rule matched — allow by default (public or auth endpoints already handled)
    }

    private boolean hasRequiredRole(AppUser.Role userRole, AppUser.Role required) {
        return ROLE_LEVEL.getOrDefault(userRole, 0) >= ROLE_LEVEL.getOrDefault(required, 0);
    }

    private void abortForbidden(ContainerRequestContext ctx, String message) {
        ctx.abortWith(
            Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"" + message + "\"}")
                    .build()
        );
    }

    // ── Route rule record ─────────────────────────────────────────────────────

    private record RouteRule(String methodPattern, Pattern pathPattern, AppUser.Role minimumRole) {
        boolean matches(String method, String path) {
            return (methodPattern.equals(".*") || methodPattern.equals(method))
                    && pathPattern.matcher(path).matches();
        }
    }
}