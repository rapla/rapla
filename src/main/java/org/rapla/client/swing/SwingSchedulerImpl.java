package org.rapla.client.swing;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Cancelable;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.UnsynchronizedCompletablePromise;
import org.rapla.scheduler.client.swing.SwingScheduler;

@DefaultImplementation(of = CommandScheduler.class, context = InjectionContext.swing)
@Singleton
public class SwingSchedulerImpl extends SwingScheduler
{
    @Inject
    public SwingSchedulerImpl(Logger logger)
    {
        super(logger);
    }
}
