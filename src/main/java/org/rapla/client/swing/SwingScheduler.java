package org.rapla.client.swing;

import java.util.concurrent.atomic.AtomicLong;

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

@DefaultImplementation(of = CommandScheduler.class,context = InjectionContext.swing)
@Singleton
public class SwingScheduler extends DefaultScheduler
{
    Logger logger;
    @Inject
    public SwingScheduler(Logger logger)
    {
        super(logger, 3);
        this.logger = logger;
    }


    
    @Override
    public <T> Promise<T> synchronizeTo(Promise<T> promise)
    {
        AtomicLong longTest = new AtomicLong();
        final CompletablePromise<T> completablePromise = new UnsynchronizedCompletablePromise<>();
        promise.whenComplete((t, ex) ->
        {
            Runnable runnable= () ->
            {
                long timeForRunnable =System.currentTimeMillis() - longTest.get();
                logger.debug("SwingUtilities invoke later took " + timeForRunnable + " ms");
                if (ex != null)
                {
                    completablePromise.completeExceptionally(ex);
                }
                else
                {
                    completablePromise.complete(t);
                }
            };
            longTest.set(System.currentTimeMillis());
            javax.swing.SwingUtilities.invokeLater(runnable);
        });
        return completablePromise;
    }
    

    @Override
    public Cancelable scheduleSynchronized(Object synchronizationObject, Runnable task, long delay)
    {
        Runnable swingTask = new Runnable()
        {
            @Override
            public void run()
            {
                javax.swing.SwingUtilities.invokeLater(task);
            }
        };
        return super.scheduleSynchronized(synchronizationObject, swingTask, delay);
    }
}
