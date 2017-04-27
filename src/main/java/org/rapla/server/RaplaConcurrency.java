package org.rapla.server;

import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class RaplaConcurrency
{
    public static void unlock(Lock lock)
    {
        if ( lock != null)
        {
            lock.unlock();
        }
    }

    public static Lock lock(Lock lock, int seconds) throws RaplaException
    {
        try
        {
            if ( lock.tryLock())
            {
                return lock;
            }
            if (lock.tryLock(seconds, TimeUnit.SECONDS))
            {
                return lock;
            }
            else
            {
                throw new RaplaSynchronizationException("Someone is currently writing. Please try again! Can't acquire lock " + lock );
            }
        }
        catch (InterruptedException ex)
        {
            throw new RaplaSynchronizationException( ex);
        }
    }
}
