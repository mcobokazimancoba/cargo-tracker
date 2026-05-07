package com.cargotracker.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS application entry point.
 *
 * <p>Extending {@link Application} and annotating with {@code @ApplicationPath}
 * is the complete replacement for {@code web.xml} in Jakarta EE 10.
 * GlassFish 7 scans the WAR for this class and registers all {@code @Path}
 * and {@code @Provider} annotated classes automatically.
 *
 * <p>{@code /api} is the base path. All resource endpoints are relative to it:
 * <pre>
 *   GET  /api/cargos
 *   POST /api/cargos
 *   GET  /api/cargos/{trackingNumber}
 *   GET  /api/locations
 *   GET  /api/analytics/summary
 * </pre>
 *
 * <p>No {@code getClasses()} or {@code getSingletons()} overrides are needed
 * when relying on container scanning — the empty subclass is intentional.
 */
@ApplicationPath("/api")
public class JakartaRestConfig extends Application {
    // Empty body is correct and intentional.
    // GlassFish scans the WAR for @Path and @Provider classes automatically.
}