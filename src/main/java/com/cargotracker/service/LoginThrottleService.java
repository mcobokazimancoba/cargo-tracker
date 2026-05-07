package com.cargotracker.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Brute-force protection on the login endpoint.
 *
 * <p>{@link com.cargotracker.config.RateLimitFilter} caps total request rate
 * per IP; this is a stricter, login-specific budget aimed at credential
 * stuffing and password-spraying. Defaults: 5 failed attempts per 15 minutes
 * for a given (IP, username) pair. Once tripped, every login attempt for
 * that pair is rejected with 429 until the window clears.
 *
 * <h3>Why per-(IP, username) and not just per-IP or just per-username</h3>
 * <ul>
 *   <li>Per-IP only — locks out an entire NAT'd network if one user typo'd.</li>
 *   <li>Per-username only — an attacker spraying one password against
 *       millions of usernames would trip every account's lock from one IP.
 *       Worse: one attacker can mass-deny-service every user by trying their
 *       names from anywhere.</li>
 *   <li>Per-(IP, username) — an attacker hammering one account from one IP
 *       is throttled; legitimate users from other IPs are unaffected; mass
 *       spraying still requires either many IPs or many usernames per IP.</li>
 * </ul>
 *
 * <h3>Username normalisation</h3>
 * The throttle key uses {@code username.trim().toLowerCase()}, so attempts
 * with rotated case (Alice / ALICE / alice) all count toward the same bucket.
 * This is consistent with how {@code AuthService.register()} normalises and
 * stores usernames, so attackers can't dodge the limit by changing case.
 *
 * <h3>Tuning</h3>
 * Configurable at runtime without redeploy:
 * <pre>
 *   -Dlogin.throttle.max.failures   / LOGIN_THROTTLE_MAX_FAILURES
 *   -Dlogin.throttle.window.seconds / LOGIN_THROTTLE_WINDOW_SECONDS
 * </pre>
 *
 * <h3>Trade-offs accepted for portfolio scope</h3>
 * In-memory only — same caveats as RateLimitFilter. A multi-node deployment
 * would put this in Redis or move it to the API gateway.
 */
@ApplicationScoped
public class LoginThrottleService {

    private static final Logger LOG = Logger.getLogger(LoginThrottleService.class.getName());

    private int  maxFailures;
    private long windowMillis;

    /** One deque per (ip, username) bucket. */
    private final ConcurrentMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        this.maxFailures  = readInt("login.throttle.max.failures",   "LOGIN_THROTTLE_MAX_FAILURES",   5);
        this.windowMillis = readInt("login.throttle.window.seconds", "LOGIN_THROTTLE_WINDOW_SECONDS", 900) * 1000L;
        LOG.info(() -> "Login throttle: " + maxFailures + " failures per "
                + (windowMillis / 1000L) + "s per (ip, username)");
    }

    /**
     * Returns true if the (ip, username) bucket is currently at or above the
     * failure cap. The caller should reject the login attempt with 429 BEFORE
     * doing the password check, so we don't help the attacker measure timing.
     */
    public boolean isLocked(String ip, String username) {
        Deque<Long> deque = buckets.get(key(ip, username));
        if (deque == null) return false;
        synchronized (deque) {
            evictOldEntries(deque, System.currentTimeMillis());
            return deque.size() >= maxFailures;
        }
    }

    /**
     * Returns the number of seconds until the oldest live failure ages out
     * of the window — i.e. how long a 429 should advise the client to wait.
     * If the bucket is not full, returns 0.
     */
    public long retryAfterSeconds(String ip, String username) {
        Deque<Long> deque = buckets.get(key(ip, username));
        if (deque == null) return 0;
        long now = System.currentTimeMillis();
        synchronized (deque) {
            evictOldEntries(deque, now);
            if (deque.size() < maxFailures) return 0;
            long oldest = deque.peekFirst();
            return Math.max(1, (oldest + windowMillis - now + 999) / 1000);
        }
    }

    /** Record a failed login attempt for this (ip, username) pair. */
    public void recordFailure(String ip, String username) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = buckets.computeIfAbsent(key(ip, username), k -> new ArrayDeque<>());
        synchronized (deque) {
            evictOldEntries(deque, now);
            deque.addLast(now);
        }
    }

    /**
     * Clear the failure history for a successful login. Without this, a user
     * who fat-fingers their password four times then succeeds on the fifth
     * would still be one slip away from being locked out.
     */
    public void clearFailures(String ip, String username) {
        buckets.remove(key(ip, username));
    }

    private void evictOldEntries(Deque<Long> deque, long now) {
        long cutoff = now - windowMillis;
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
            deque.pollFirst();
        }
    }

    private static String key(String ip, String username) {
        String safeIp = ip == null ? "unknown" : ip;
        String safeUser = username == null ? "" : username.trim().toLowerCase();
        return safeIp + "|" + safeUser;
    }

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
