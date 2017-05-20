package org.rapla.server.internal;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.server.PromiseWait;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@DefaultImplementation(of = PromiseWait.class,context = InjectionContext.server)
public class PromiseWaitImpl implements PromiseWait
{
    private final Logger logger;

    @Inject
    public PromiseWaitImpl(Logger logger)
    {
        this.logger = logger;
    }

    public<T> T  waitForWithRaplaException(Promise<T> promise, int millis) throws RaplaException
    {
        try
        {
            return waitFor(promise, millis);
        }
        catch (Exception ex)
        {
            if ( ex instanceof RaplaException)
            {
                throw (RaplaException)ex;
            }
            throw new RaplaException(ex);
        }
    }

    public <T> T waitFor(Promise<T> promise, int timeout) throws Exception
    {
        long index = System.currentTimeMillis();
        final CompletableFuture<T> future = new CompletableFuture<>();
        promise.whenComplete((t, ex) ->
        {
            if ( logger.isDebugEnabled())
            {
                logger.debug("promise complete " + index);
            }
            if (ex != null)
            {
                future.completeExceptionally(ex);
            }
            else
            {
                future.complete(t);
            }
            if ( logger.isDebugEnabled())
            {
                logger.debug("Release lock  " + index);
            }
        });
        try
        {
            if ( logger.isDebugEnabled())
            {
                logger.debug("Aquire lock " + index);
            }
            T t = future.get(timeout, TimeUnit.MILLISECONDS);
            if ( logger.isDebugEnabled())
            {
                logger.debug("SwingUtilities waitFor " + index);
            }
            return t;

        }
        catch (ExecutionException ex)
        {
            final Throwable cause = ex.getCause();
            if ( cause instanceof Exception)
            {
                throw (Exception)cause;
            }
            if ( cause instanceof Error)
            {
                throw (Error)cause;
            }
            throw ex;
        }
    }
}
