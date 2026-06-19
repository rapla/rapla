package org.rapla.server.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * H2: exponential-backoff login throttle, in-memory (per-pod). Time is injected
 * so the windows can be exercised without sleeping.
 */
class LoginAttemptTrackerTest
{
    private final AtomicLong now = new AtomicLong(0);
    private LoginAttemptTracker tracker;

    @BeforeEach
    void setUp()
    {
        tracker = new LoginAttemptTracker(now::get);
    }

    private void fail(int n, String key)
    {
        for (int i = 0; i < n; i++) tracker.onFailure(key);
    }

    @Test
    void backoffDoublesAfterFreeAttempts()
    {
        assertEquals(0, LoginAttemptTracker.backoffMs(3));
        assertEquals(1000, LoginAttemptTracker.backoffMs(4));
        assertEquals(2000, LoginAttemptTracker.backoffMs(5));
        assertEquals(4000, LoginAttemptTracker.backoffMs(6));
        assertEquals(LoginAttemptTracker.MAX_MS, LoginAttemptTracker.backoffMs(100));
    }

    @Test
    void firstThreeFailuresAreFree()
    {
        fail(3, "ip|bob");
        assertEquals(0, tracker.retryAfterSeconds("ip|bob"));
    }

    @Test
    void fourthFailureBlocks()
    {
        fail(4, "ip|bob");
        assertEquals(1, tracker.retryAfterSeconds("ip|bob")); // 1000ms -> 1s
    }

    @Test
    void blockClearsAfterWaitButCountPersists()
    {
        fail(4, "ip|bob");
        now.addAndGet(1000);
        assertEquals(0, tracker.retryAfterSeconds("ip|bob")); // window elapsed
        tracker.onFailure("ip|bob");                          // 5th fail -> 2s
        assertEquals(2, tracker.retryAfterSeconds("ip|bob"));
    }

    @Test
    void successResetsTheCounter()
    {
        fail(5, "ip|bob");
        tracker.onSuccess("ip|bob");
        assertEquals(0, tracker.retryAfterSeconds("ip|bob"));
        fail(3, "ip|bob");                                    // free again
        assertEquals(0, tracker.retryAfterSeconds("ip|bob"));
    }

    @Test
    void idleTtlResetsTheCounter()
    {
        fail(5, "ip|bob");
        now.addAndGet(LoginAttemptTracker.TTL_MS + 1);
        assertEquals(0, tracker.retryAfterSeconds("ip|bob"));
        tracker.onFailure("ip|bob");                          // fresh #1 -> free
        assertEquals(0, tracker.retryAfterSeconds("ip|bob"));
    }

    @Test
    void keysAreIsolated()
    {
        fail(4, "ip1|bob");
        assertEquals(0, tracker.retryAfterSeconds("ip2|bob"));   // other IP unaffected
        assertEquals(0, tracker.retryAfterSeconds("ip1|alice")); // other user unaffected
    }
}
