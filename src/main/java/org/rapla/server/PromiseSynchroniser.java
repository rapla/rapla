package org.rapla.server;

import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PromiseSynchroniser
{
    public static <T> T waitForWithRaplaException(Promise<T> promise, int timeout) throws RaplaException
    {
        try
        {
            return waitFor(promise, timeout);
        }
        catch (RaplaException ex)
        {
            throw ex;
        }
        catch (Throwable throwable)
        {
            throw new RaplaException(throwable.getMessage(), throwable);
        }
    }

    public static <T> T waitFor(Promise<T> promise, int timeout) throws Throwable
    {
        Semaphore semaphore = new Semaphore(0);
        AtomicReference<T> atomicReference = new AtomicReference<>();
        AtomicReference<Throwable> atomicReferenceE = new AtomicReference<>();
        promise.whenComplete((t, ex) -> {
            atomicReferenceE.set(ex);
            atomicReference.set(t);
            semaphore.release();
        });
        semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        final Throwable throwable = atomicReferenceE.get();
        if (throwable != null)
        {
            throw throwable;
        }
        final T t = atomicReference.get();
        return t;
    }
}
