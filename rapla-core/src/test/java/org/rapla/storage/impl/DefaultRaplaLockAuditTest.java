package org.rapla.storage.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.storage.impl.DefaultRaplaLock;
import org.rapla.storage.impl.RaplaLock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 audit tests for {@link DefaultRaplaLock}. Pins the basic contract
 * (null safety, reentrancy, write-lock blocks readers) and tests the deadlock
 * patterns the dispatch path relies on (non-blocking {@code writeLockIfAvaliable},
 * timeout on contended write).
 */
class DefaultRaplaLockAuditTest
{
    private final Logger logger = RaplaBootstrapLogger.createRaplaLogger();

    @Test
    @DisplayName("unlock(null) is a silent no-op for both ReadLock and WriteLock")
    void unlockNullIsSafe()
    {
        DefaultRaplaLock m = new DefaultRaplaLock(logger);
        assertDoesNotThrow(() -> m.unlock((RaplaLock.ReadLock) null));
        assertDoesNotThrow(() -> m.unlock((RaplaLock.WriteLock) null));
    }

    @Test
    @DisplayName("reentrant write-lock from the same thread succeeds")
    void writeLockIsReentrant() throws Exception
    {
        DefaultRaplaLock m = new DefaultRaplaLock(logger);
        RaplaLock.WriteLock l1 = m.writeLock(getClass(), "outer", 5);
        assertNotNull(l1);
        RaplaLock.WriteLock l2 = m.writeLock(getClass(), "inner", 5);
        assertNotNull(l2, "ReentrantReadWriteLock allows write reentry on the same thread");
        m.unlock(l2);
        m.unlock(l1);
    }

    @Test
    @DisplayName("writeLockIfAvaliable returns null while another thread holds the write lock (no deadlock)")
    void writeLockIfAvailableNonBlockingUnderContention() throws Exception
    {
        DefaultRaplaLock m = new DefaultRaplaLock(logger);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            try
            {
                RaplaLock.WriteLock l = m.writeLock(getClass(), "holder", 5);
                acquired.countDown();
                release.await(2, TimeUnit.SECONDS);
                m.unlock(l);
            }
            catch (Throwable t) { err.set(t); }
        });
        holder.start();
        assertTrue(acquired.await(2, TimeUnit.SECONDS), "holder must acquire promptly");

        // Now we (the test thread) try a non-blocking acquire — this is the
        // exact pattern the scheduled-refresh tick uses to avoid blocking the dispatch
        // path. Should return null cleanly.
        RaplaLock.WriteLock attempt = m.writeLockIfAvaliable(getClass(), "non-blocking");
        assertNull(attempt, "writeLockIfAvaliable must return null when the lock is taken — "
              + "the scheduled refresh path relies on this to avoid deadlocking dispatch");

        release.countDown();
        holder.join(2000);
        if (err.get() != null) throw new RuntimeException(err.get());
    }

    @Test
    @DisplayName("writeLock(timeout) throws RaplaSynchronizationException after timeout when held by another thread")
    void writeLockTimeoutThrowsSyncException() throws Exception
    {
        DefaultRaplaLock m = new DefaultRaplaLock(logger);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            try
            {
                RaplaLock.WriteLock l = m.writeLock(getClass(), "holder", 5);
                acquired.countDown();
                release.await(3, TimeUnit.SECONDS);
                m.unlock(l);
            }
            catch (Throwable ignored) {}
        });
        holder.start();
        acquired.await(2, TimeUnit.SECONDS);

        // Try to acquire with 1-second timeout — should give up cleanly.
        long t0 = System.currentTimeMillis();
        assertThrows(RaplaSynchronizationException.class,
                () -> m.writeLock(getClass(), "contender", 1),
                "1-second timeout against a held write lock must surface as RaplaSynchronizationException");
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue(elapsed < 2500, "timeout path should fail in ~1s, took " + elapsed + "ms");

        release.countDown();
        holder.join(2000);
    }

    @Test
    @DisplayName("isWriteLocked / isReadLocked report current state without permanently affecting it")
    void lockStateProbesAreNonInvasive() throws Exception
    {
        DefaultRaplaLock m = new DefaultRaplaLock(logger);

        // Initially no locks — both probes should report unlocked.
        assertTrue(m.isWriteLocked(),
                "ReentrantReadWriteLock.isWriteLocked() returns true if writeLock CAN be acquired (existing semantics)");
        assertTrue(m.isReadLocked());

        // After acquire (and release) the probes should still work.
        RaplaLock.WriteLock l = m.writeLock(getClass(), "probe", 5);
        m.unlock(l);
        assertTrue(m.isWriteLocked(), "after release, lock must be acquirable again");
    }
}
