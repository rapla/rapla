package org.rapla.server.spring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory exponential-backoff throttle for the credential login paths (H2):
 * form login ({@code /login}) and the OAuth password grant ({@code /oauth2/token}).
 *
 * <p>Keyed by client-IP + username, so an attacker can never lock out a legitimate
 * user (that would be a DoS) — only its own IP gets slowed. The first {@value #FREE}
 * failures are free (normal typos); after that the wait before the next accepted
 * attempt doubles (1s, 2s, 4s, …) up to a {@value #MAX_MS}ms cap, and a successful
 * login (or {@value #TTL_MS}ms of inactivity) resets the counter.
 *
 * <p><b>Per-pod by design.</b> State lives in this JVM only; with rapla's small pod
 * count (2) and source-IP stickiness at the load balancer, each client IP always
 * hits the same pod, so the per-pod counter is effectively global for that IP. Even
 * without stickiness the exponential backoff throttles each pod independently — at
 * most a constant-factor (pod-count) speed-up, still far below a useful brute-force
 * rate. No shared store / database is needed.
 */
public class LoginAttemptTracker
{
    static final int FREE = 3;                 // free attempts before throttling
    static final long BASE_MS = 1_000;         // first throttled wait
    static final long MAX_MS = 5 * 60_000;     // cap: 5 minutes
    static final long TTL_MS = 60 * 60_000;    // idle reset: 1 hour
    static final int SWEEP_CAP = 100_000;      // map-size guard before a sweep

    private record Attempt(int fails, long blockedUntilMs, long lastSeenMs) {}

    private final Map<String, Attempt> map = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginAttemptTracker()
    {
        this(System::currentTimeMillis);
    }

    LoginAttemptTracker(LongSupplier clock)
    {
        this.clock = clock;
    }

    /** @return seconds the caller must wait before another attempt, or 0 if allowed. */
    public long retryAfterSeconds(String key)
    {
        long now = clock.getAsLong();
        Attempt a = map.get(key);
        if (a == null)
        {
            return 0;
        }
        if (now - a.lastSeenMs() > TTL_MS)
        {
            map.remove(key, a);
            return 0;
        }
        return now < a.blockedUntilMs() ? (a.blockedUntilMs() - now + 999) / 1000 : 0;
    }

    public void onFailure(String key)
    {
        long now = clock.getAsLong();
        if (map.size() > SWEEP_CAP)
        {
            map.entrySet().removeIf(e -> now - e.getValue().lastSeenMs() > TTL_MS);
        }
        map.compute(key, (k, prev) -> {
            boolean fresh = prev != null && now - prev.lastSeenMs() <= TTL_MS;
            int fails = (fresh ? prev.fails() : 0) + 1;
            return new Attempt(fails, now + backoffMs(fails), now);
        });
    }

    public void onSuccess(String key)
    {
        map.remove(key);
    }

    static long backoffMs(int fails)
    {
        if (fails <= FREE)
        {
            return 0;
        }
        int n = fails - FREE - 1;
        if (n > 20) // guard against shift overflow; the cap dominates long before this
        {
            return MAX_MS;
        }
        return Math.min(MAX_MS, BASE_MS << n);
    }
}
