package com.cargotracker.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Per-IP request-rate limiter.
 *
 * <p>Caps incoming requests at a configurable threshold per rolling window.
 * Defaults to 100 requests / 60 seconds, matching the spec for this project.
 * Limits are tunable at runtime without redeploying:
 * <pre>
 *   -Dratelimit.requests=100   (or env RATELIMIT_REQUESTS)
 *   -Dratelimit.window.seconds=60   (or env RATELIMIT_WINDOW_SECONDS)
 * </pre>
 *
 * <h3>Algorithm — sliding window over a per-IP timestamp deque</h3>
 * For each IP we keep a deque of millisecond timestamps. On every request:
 * <ol>
 *   <li>Drop entries older than the window from the head.</li>
 *   <li>If the deque size has reached the cap, reject with 429.</li>
 *   <li>Otherwise, append the current timestamp.</li>
 * </ol>
 * This is more accurate than a fixed window (which permits a 2× burst at
 * window boundaries) and easier to reason about than a token bucket.
 *
 * <h3>Filter ordering</h3>
 * {@code @Priority(100)} runs before CorsFilter (500), AuthFilter (1000), and
 * RoleFilter (2000). The reason for "first": rate limits should reject before
 * we burn CPU on signature verification or DB lookups for an attacker.
 * CorsFilter still adds CORS headers to the 429 response — JAX-RS response
 * filters run on aborted responses too, so the browser displays the 429
 * properly instead of a confusing "CORS error".
 *
 * <h3>Trade-offs accepted for portfolio scope</h3>
 * <ul>
 *   <li><b>In-memory only.</b> A multi-node cluster would need Redis or a
 *       gateway-level limit (Cloudflare, nginx). For a single GlassFish
 *       instance this is correct.</li>
 *   <li><b>Map grows unbounded.</b> One entry per IP that has ever connected.
 *       Acceptable for a portfolio piece. A periodic sweeper or LRU cap
 *       would harden this for production.</li>
 *   <li><b>Uses {@code getRemoteAddr()}, not X-Forwarded-For.</b> If we
 *       trusted XFF without a known proxy in front, any client could spoof
 *       its IP and bypass the limit. If/when this is deployed behind nginx
 *       or a load balancer, switch to reading the first XFF entry — but only
 *       after confirming the proxy is the only path to the app.</li>
 * </ul>
 */
@Provider
@Priority(100)
@ApplicationScoped
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class.getName());

    private int  maxRequests;
    private long windowMillis;

    /**
     * One deque per IP. ConcurrentHashMap allows safe lazy creation without
     * a global lock; the per-deque mutation is then synchronised on the
     * deque itself, so contention is per-IP rather than global.
     */
    private final ConcurrentMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    /**
     * Injected by the JAX-RS runtime; per-thread proxy resolves to the
     * current request, so it's safe to keep this field on a singleton.
     */
    @Context
    private HttpServletRequest httpRequest;

    @PostConstruct
    void init() {
        this.maxRequests  = readInt("ratelimit.requests",       "RATELIMIT_REQUESTS",        100);
        this.windowMillis = readInt("ratelimit.window.seconds", "RATELIMIT_WINDOW_SECONDS",  60) * 1000L;
        LOG.info(() -> "Rate limit configured: " + maxRequests + " req per "
                + (windowMillis / 1000L) + "s per IP");
    }

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {

        // CORS preflights are sent automatically by browsers before any
        // cross-origin request that uses an Authorization header or non-simple
        // method. They carry no app payload and the browser reuses the result
        // for Access-Control-Max-Age. Counting them would penalise legitimate
        // users running a SPA against this API.
        if ("OPTIONS".equalsIgnoreCase(ctx.getMethod())) {
            return;
        }

        String ip = clientIp();
        long now  = System.currentTimeMillis();

        Deque<Long> deque = buckets.computeIfAbsent(ip, k -> new ArrayDeque<>());

        long retryAfterSeconds;
        synchronized (deque) {
            evictOldEntries(deque, now);

            if (deque.size() >= maxRequests) {
                long oldest = deque.peekFirst();         // earliest of the live entries
                retryAfterSeconds = Math.max(1, (oldest + windowMillis - now + 999) / 1000);
            } else {
                deque.addLast(now);
                return;                                  // request allowed — fast path exit
            }
        }

        // Fall-through means the request was rejected. Build the 429 outside
        // the synchronized block so we don't hold the lock across response
        // construction.
        LOG.fine(() -> "Rate limit hit for IP " + ip);
        ctx.abortWith(
            Response.status(429)
                    .type(MediaType.APPLICATION_JSON)
                    .header("Retry-After", retryAfterSeconds)
                    .entity("{\"status\":429,\"error\":\"Too Many Requests\","
                          + "\"message\":\"Rate limit exceeded — retry in "
                          + retryAfterSeconds + " seconds.\"}")
                    .build()
        );
    }

    private void evictOldEntries(Deque<Long> deque, long now) {
        long cutoff = now - windowMillis;
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
            deque.pollFirst();
        }
    }

    private String clientIp() {
        // See class Javadoc for the deliberate choice not to honour
        // X-Forwarded-For without a trusted proxy in front.
        return httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
    }

    /** System property → env var → default, same pattern as MailService. */
    private static int readInt(String propKey, String envKey, int defaultValue) {
        String raw = System.getProperty(propKey);
        if (raw == null || raw.isBlank()) raw = System.getenv(envKey);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOG.warning(() -> "Invalid value for " + propKey + " — falling back to " + defaultValue);
            return defaultValue;
        }
    }
}
