package org.rapla.server.internal;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.PromiseWait;

import javax.inject.Inject;

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
            return SynchronizedCompletablePromise.waitFor(promise, millis, logger);
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


}
