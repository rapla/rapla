package org.rapla.client.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SwingSafeTest
{
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender()
    {
        logger = (Logger) LoggerFactory.getLogger(SwingSafe.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender()
    {
        logger.detachAppender(appender);
    }

    @Test
    void runnableExceptionRoutedToLoggerNotStderr() throws Exception
    {
        CountDownLatch done = new CountDownLatch(1);
        RuntimeException boom = new RuntimeException("boom");

        SwingSafe.invokeLater(() -> {
            try { throw boom; }
            finally { done.countDown(); }
        });

        assertTrue(done.await(5, TimeUnit.SECONDS), "EDT runnable should have run");
        // Flush the EDT: any error logging happens synchronously inside the wrapper, but
        // we still need to make sure the EDT has finished before we observe the counter.
        SwingUtilities.invokeAndWait(() -> {});

        long errorCount = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).count();
        assertEquals(1, errorCount, "Logger should have received exactly one error");
        ILoggingEvent errorEvent = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertEquals(boom, errorEvent.getThrowableProxy() == null ? null
                : ((ch.qos.logback.classic.spi.ThrowableProxy) errorEvent.getThrowableProxy()).getThrowable(),
                "Logger should receive the same throwable");
    }

    @Test
    void normalRunnableRunsWithoutLogging() throws Exception
    {
        CountDownLatch done = new CountDownLatch(1);

        SwingSafe.invokeLater(done::countDown);

        assertTrue(done.await(5, TimeUnit.SECONDS), "EDT runnable should have run");
        SwingUtilities.invokeAndWait(() -> {});

        long errorCount = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).count();
        assertEquals(0, errorCount, "Logger should not be invoked on a clean runnable");
    }
}
