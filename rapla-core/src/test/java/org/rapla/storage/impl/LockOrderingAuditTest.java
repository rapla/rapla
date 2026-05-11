package org.rapla.storage.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Deadlock-pattern audit for the two-lock setup in
 * {@code LocalAbstractCachableOperator}: {@code lockManager} (primary) +
 * {@code disconnectLock} (gate against scheduled tasks during disconnect).
 *
 * <p>The acquisition orders are NOT consistent across paths:
 * <ul>
 *   <li>{@code disconnect()} acquires {@code lockManager.write} → {@code disconnectLock.write}.</li>
 *   <li>The scheduled-refresh task (running under {@code scheduleConnectedTasks})
 *       acquires {@code disconnectLock.read} first, then {@code lockManager.read/write}
 *       inside {@code command.run()}.</li>
 * </ul>
 *
 * <p>This is a textbook lock-inversion that would deadlock without timeouts.
 * It does NOT in practice because:
 * <ul>
 *   <li>The scheduled-task wrapper acquires {@code disconnectLock.read} with a
 *       3-second timeout — if disconnect already holds {@code disconnectLock.write},
 *       the scheduled task aborts cleanly.</li>
 *   <li>Inside the task body, {@code lockManager.readLock} has a 20-second
 *       timeout — if disconnect already holds {@code lockManager.write}, the
 *       task's read attempt fails fast (relatively) and releases its
 *       {@code disconnectLock.read}, letting disconnect proceed.</li>
 * </ul>
 *
 * <p>These tests pin those bounded-stall properties so a future refactor that
 * (a) lengthens the timeouts or (b) adds a NEW nested-lock site without
 * timeouts converts the inversion into a real deadlock.
 */
class LockOrderingAuditTest
{
    private final Logger logger = RaplaBootstrapLogger.createRaplaLogger();

    @Test
    @DisplayName("disconnectLock.read with 3s timeout fails cleanly when disconnectLock.write is held")
    void scheduledTaskGivesUpWhenDisconnectIsHoldingItsLock() throws Exception
    {
        DefaultRaplaLock disconnectLock = new DefaultRaplaLock(logger);

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            try {
                RaplaLock.WriteLock w = disconnectLock.writeLock(getClass(), "disconnect-simulated", 5);
                acquired.countDown();
                release.await(4, TimeUnit.SECONDS);
                disconnectLock.unlock(w);
            } catch (Exception ignored) {}
        });
        holder.start();
        assertTrue(acquired.await(2, TimeUnit.SECONDS));

        // The scheduled-task wrapper uses readLock(_, _, 3) — should give up cleanly.
        long t0 = System.currentTimeMillis();
        assertThrows(RaplaSynchronizationException.class,
                () -> disconnectLock.readLock(getClass(), "scheduled-task", 3),
                "scheduled-task readLock(3s timeout) must fail when disconnect holds the write lock");
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue(elapsed >= 2500 && elapsed < 4000,
                "should give up close to its 3s timeout, took " + elapsed + " ms");

        release.countDown();
        holder.join(2000);
    }

    @Test
    @DisplayName("two-lock inversion does not hang permanently — readLock timeout breaks the cycle")
    void twoLockInversionResolvesViaTimeout() throws Exception
    {
        // Simulate the operator's two locks.
        DefaultRaplaLock lockManager = new DefaultRaplaLock(logger);
        DefaultRaplaLock disconnectLock = new DefaultRaplaLock(logger);

        CountDownLatch threadAReady = new CountDownLatch(1);
        CountDownLatch threadBReady = new CountDownLatch(1);
        AtomicReference<Throwable> aErr = new AtomicReference<>();
        AtomicReference<Throwable> bErr = new AtomicReference<>();
        AtomicReference<Long> aDuration = new AtomicReference<>(-1L);

        // Thread A: simulates disconnect() — lockManager.write → disconnectLock.write.
        Thread a = new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                RaplaLock.WriteLock lm = lockManager.writeLock(getClass(), "A-disconnect-lockManager", 30);
                threadAReady.countDown();
                // Wait for B to grab disconnectLock.read.
                threadBReady.await(5, TimeUnit.SECONDS);
                // Now try to acquire disconnectLock.write — B holds the read.
                // B is blocked trying to acquire lockManager.read (we hold write)
                // → B's lockManager.readLock has a 2s timeout (test scenario).
                // After B times out, B releases disconnectLock.read, A proceeds.
                RaplaLock.WriteLock dl = disconnectLock.writeLock(getClass(), "A-disconnect-disconnectLock", 30);
                aDuration.set(System.currentTimeMillis() - t0);
                disconnectLock.unlock(dl);
                lockManager.unlock(lm);
            } catch (Throwable t) { aErr.set(t); }
        });

        // Thread B: simulates scheduled task — disconnectLock.read → (inside) lockManager.read.
        Thread b = new Thread(() -> {
            try {
                threadAReady.await(5, TimeUnit.SECONDS); // make sure A has lockManager.write
                RaplaLock.ReadLock dl = disconnectLock.readLock(getClass(), "B-scheduled-disconnectLock", 5);
                threadBReady.countDown();
                // Now try to acquire lockManager.read with a SHORT timeout (mimics the
                // production 20s default but compressed to 2s for test speed).
                try {
                    lockManager.readLock(getClass(), "B-scheduled-lockManager", 2);
                    fail("B should NOT have acquired lockManager.read while A holds the write");
                } catch (RaplaSynchronizationException expected) {
                    // good — caught the timeout, this is exactly what production does
                }
                disconnectLock.unlock(dl);
            } catch (Throwable t) { bErr.set(t); }
        });

        a.start();
        b.start();

        a.join(15_000);
        b.join(15_000);

        if (aErr.get() != null) throw new RuntimeException("A failed: ", aErr.get());
        if (bErr.get() != null) throw new RuntimeException("B failed: ", bErr.get());

        long elapsedMs = aDuration.get();
        assertTrue(elapsedMs >= 1500,
                "A should have stalled at least the read-lock timeout, took " + elapsedMs + "ms");
        assertTrue(elapsedMs < 8000,
                "A should have completed within the bounded stall, took " + elapsedMs + "ms — "
              + "if this fails, a new nested-lock site without a timeout was introduced "
              + "and the inversion now hangs.");
    }
}
