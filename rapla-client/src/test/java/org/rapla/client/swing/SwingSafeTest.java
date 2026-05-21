package org.rapla.client.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.rapla.logger.AbstractLogger;
import org.rapla.logger.Logger;

class SwingSafeTest
{
    /** Hand-rolled capture logger — no mocks per PRD 027 / AGENTS.md §13. */
    static final class CapturingLogger extends AbstractLogger
    {
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Throwable> lastError = new AtomicReference<>();

        CapturingLogger() { super(LEVEL_INFO); }

        @Override protected void write(int level, String message, Throwable cause)
        {
            if (level == LEVEL_ERROR)
            {
                errorCount.incrementAndGet();
                lastError.set(cause);
            }
        }

        @Override public Logger getChildLogger(String name) { return this; }
    }

    @Test
    void runnableExceptionRoutedToLoggerNotStderr() throws Exception
    {
        CapturingLogger logger = new CapturingLogger();
        CountDownLatch done = new CountDownLatch(1);
        RuntimeException boom = new RuntimeException("boom");

        SwingSafe.invokeLater(logger, () -> {
            try { throw boom; }
            finally { done.countDown(); }
        });

        assertTrue(done.await(5, TimeUnit.SECONDS), "EDT runnable should have run");
        // Flush the EDT: any error logging happens synchronously inside the wrapper, but
        // we still need to make sure the EDT has finished before we observe the counter.
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(1, logger.errorCount.get(), "Logger should have received exactly one error");
        assertEquals(boom, logger.lastError.get(), "Logger should receive the same throwable");
    }

    @Test
    void normalRunnableRunsWithoutLogging() throws Exception
    {
        CapturingLogger logger = new CapturingLogger();
        CountDownLatch done = new CountDownLatch(1);

        SwingSafe.invokeLater(logger, done::countDown);

        assertTrue(done.await(5, TimeUnit.SECONDS), "EDT runnable should have run");
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(0, logger.errorCount.get(), "Logger should not be invoked on a clean runnable");
    }
}
