package com.cargotracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LoginThrottleService}.
 *
 * <p>The service has no injected dependencies and no persistence — its
 * state lives in an in-memory map. Construct it directly, configure tight
 * bounds via system properties so tests run fast, and exercise the
 * sliding-window behaviour.
 */
class LoginThrottleServiceTest {

    private LoginThrottleService throttle;

    @BeforeEach
    void setUp() {
        // Tight bounds so we don't sleep for 15 minutes in test.
        // Window = 1 second is enough; we never sleep > 1.5s in any single test.
        System.setProperty("login.throttle.max.failures",   "3");
        System.setProperty("login.throttle.window.seconds", "1");

        throttle = new LoginThrottleService();
        throttle.init();
    }

    // ── core lockout ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("locks after exactly N failures, not before")
    void locksAfterMaxFailures() {
        String ip = "10.0.0.1";
        String username = "alice";

        // First (max - 1) failures: still allowed.
        throttle.recordFailure(ip, username);
        throttle.recordFailure(ip, username);
        assertFalse(throttle.isLocked(ip, username),
                "two failures should not trip a 3-failure threshold");

        // Third failure trips the lock.
        throttle.recordFailure(ip, username);
        assertTrue(throttle.isLocked(ip, username),
                "third failure should put the bucket at the cap");
    }

    // ── isolation between (ip, username) pairs ────────────────────────────────

    @Test
    @DisplayName("locking (ip-A, alice) does not lock (ip-B, alice) or (ip-A, bob)")
    void bucketsAreIsolated() {
        // Lock alice from IP A.
        throttle.recordFailure("10.0.0.1", "alice");
        throttle.recordFailure("10.0.0.1", "alice");
        throttle.recordFailure("10.0.0.1", "alice");
        assertTrue(throttle.isLocked("10.0.0.1", "alice"));

        // Different IP, same user → not locked. (Legit user moves networks.)
        assertFalse(throttle.isLocked("10.0.0.2", "alice"));

        // Same IP, different user → not locked. (Shared NAT.)
        assertFalse(throttle.isLocked("10.0.0.1", "bob"));
    }

    // ── username case-insensitivity ───────────────────────────────────────────

    @Test
    @DisplayName("case-rotation does not bypass the limit (Alice / ALICE / alice share a bucket)")
    void caseRotationDoesNotBypass() {
        String ip = "10.0.0.1";

        throttle.recordFailure(ip, "Alice");
        throttle.recordFailure(ip, "ALICE");
        throttle.recordFailure(ip, "alice");

        assertTrue(throttle.isLocked(ip, "AlIcE"),
                "throttle key normalises to lowercase, so case rotation lands in one bucket");
    }

    // ── successful login resets the counter ───────────────────────────────────

    @Test
    @DisplayName("clearFailures resets the bucket so a fat-fingered user is not one typo from lockout")
    void clearFailuresResets() {
        String ip = "10.0.0.1";
        String username = "alice";

        throttle.recordFailure(ip, username);
        throttle.recordFailure(ip, username);
        // Successful login wipes prior failures.
        throttle.clearFailures(ip, username);

        // Two more failures should now be safe (still under cap of 3).
        throttle.recordFailure(ip, username);
        throttle.recordFailure(ip, username);
        assertFalse(throttle.isLocked(ip, username),
                "post-clear, accumulating up to (cap - 1) failures must not lock");
    }

    // ── sliding window — old failures age out ─────────────────────────────────

    @Test
    @DisplayName("failures older than the window are evicted, allowing fresh attempts")
    void slidingWindowEvictsOldFailures() throws InterruptedException {
        String ip = "10.0.0.1";
        String username = "alice";

        throttle.recordFailure(ip, username);
        throttle.recordFailure(ip, username);
        throttle.recordFailure(ip, username);
        assertTrue(throttle.isLocked(ip, username));

        // Wait past the 1-second window. After eviction the bucket is empty.
        Thread.sleep(1_200);

        assertFalse(throttle.isLocked(ip, username),
                "after the window passes, prior failures should age out");
        assertEquals(0, throttle.retryAfterSeconds(ip, username));
    }
}
