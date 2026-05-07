package org.rapla.scheduler.sync;

import org.rapla.logger.Logger;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class SynchronizedCompletablePromise<T> extends  SynchronizedPromise<T> implements CompletablePromise<T>
{
    public SynchronizedCompletablePromise(Executor executor)
    {
        super(executor, new CompletableFuture<T>());
    }

    @Override
    public void complete(T value)
    {
        ((CompletableFuture<T>)f).complete( value );
    }

    @Override
    public boolean isDone()
    {
        return ((CompletableFuture<T>)f).isDone();
    }

    @Override
    public void completeExceptionally(Throwable ex)
    {
        ((CompletableFuture<T>)f).completeExceptionally( ex);
    }

    /** waits until the promise completes or the timeout has Passed. Pass -1 if you want to wait without timeout*/
    public static  <T> T waitFor(Promise<T> promise, int timeout,Logger logger) throws Exception
    {
        final CompletableFuture<T> future = getCompletableFuture(promise, logger, null);
        final boolean isDebugEnabled = logger.isDebugEnabled();
        long index = isDebugEnabled ? System.currentTimeMillis() : 0;
        try
        {
            if (isDebugEnabled)
            {
                logger.debug("Aquire lock " + index);
            }
            T t;
            if ( timeout >=0)
            {
                t = future.get(timeout, TimeUnit.MILLISECONDS);
            }
            else
            {
                t = future.get();
            }
            if (isDebugEnabled)
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

    public static <T> CompletableFuture<T> getCompletableFuture(Promise<T> promise, Logger logger, Function<Throwable,Throwable> exceptionMapper) {
        CompletableFuture<T> future;
        final boolean isDebugEnabled = logger.isDebugEnabled();
        long index = isDebugEnabled ? System.currentTimeMillis() : 0;
        future = new CompletableFuture<>();
        promise.handle((t, ex) ->
        {
            if (isDebugEnabled) {
                logger.debug("promise complete " + index);
            }
            if (ex != null) {
                if ( exceptionMapper != null)
                {
                    ex = exceptionMapper.apply( ex );
                }
                future.completeExceptionally(ex);
            } else {
                future.complete(t);
            }
            if (isDebugEnabled) {
                logger.debug("Release lock  " + index);
            }
            return t;
        });
        return future;
    }

}
