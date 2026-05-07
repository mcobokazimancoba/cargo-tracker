package com.cargotracker.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Adds CORS headers to every HTTP response so the browser allows
 * JavaScript running on the same origin (GlassFish) to call the API.
 *
 * <p>Why is this needed?
 * Modern browsers enforce the Same-Origin Policy. Even when the HTML page
 * and the REST API are served by the same GlassFish server, if the browser
 * considers them different "origins" (different port, different path prefix)
 * it will block the fetch() call with a CORS error before the response body
 * is ever read. The fix is to return Access-Control-Allow-* headers so the
 * browser permits the request.
 *
 * <p>This filter implements BOTH {@link ContainerRequestFilter} AND
 * {@link ContainerResponseFilter}:
 * <ul>
 *   <li>Request filter  — handles browser preflight OPTIONS requests.
 *       The browser sends OPTIONS before any non-simple request (POST with JSON,
 *       PUT, DELETE, or any request with an Authorization header) to ask the server
 *       "do you allow this?". We must respond 200 OK immediately — if we let the
 *       request continue to the resource, JAX-RS will return 405 Method Not Allowed
 *       because no resource method maps to OPTIONS, and the browser will block.</li>
 *   <li>Response filter — adds CORS headers to every response, including the
 *       200 OK we return from the preflight and all real API responses.</li>
 * </ul>
 *
 * <p>{@code @Priority(500)} — runs BEFORE AuthFilter (1000) and RoleFilter (2000).
 * CORS must be handled first. If AuthFilter aborts with 401 but the 401 response
 * has no CORS headers, the browser reports a CORS error instead of a 401, which
 * makes debugging impossible.
 */
@Provider
@Priority(500)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    /**
     * Handles preflight OPTIONS requests.
     * The browser sends these automatically before any request that carries
     * an Authorization header or uses a non-GET/POST method.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            requestContext.abortWith(
                jakarta.ws.rs.core.Response.ok().build()
            );
        }
    }

    /**
     * Adds CORS headers to every outbound response — success, error, and preflight.
     *
     * <p>Header explanations:
     * <ul>
     *   <li>{@code Access-Control-Allow-Origin: *}
     *       Allow requests from any origin. For a production system replace * with
     *       the specific origin (e.g. https://yourapp.com). * cannot be used when
     *       credentials (cookies) are involved, but we use Bearer tokens in headers
     *       so * is correct here.</li>
     *   <li>{@code Access-Control-Allow-Headers: ...}
     *       The browser's preflight asks if Authorization and Content-Type are
     *       permitted. We must explicitly list them here.</li>
     *   <li>{@code Access-Control-Allow-Methods: ...}
     *       List every HTTP method the API uses so the preflight succeeds for all.</li>
     *   <li>{@code Access-Control-Max-Age: 86400}
     *       Cache the preflight result for 24 hours. Without this, the browser
     *       sends a preflight before EVERY API call, doubling your request count.</li>
     * </ul>
     */
    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {

        responseContext.getHeaders().putSingle(
                "Access-Control-Allow-Origin", "*");
        responseContext.getHeaders().putSingle(
                "Access-Control-Allow-Headers",
                "Authorization, Content-Type, Accept, Origin, X-Requested-With");
        responseContext.getHeaders().putSingle(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        responseContext.getHeaders().putSingle(
                "Access-Control-Max-Age", "86400");
        responseContext.getHeaders().putSingle(
                "Access-Control-Expose-Headers",
                "Location, Content-Type");
    }
}