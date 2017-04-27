package org.rapla.storage.impl;

import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.server.RaplaConcurrency;
import org.rapla.storage.dbrm.RestartServer;

import javax.inject.Inject;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@DefaultImplementation(of = StorageLockManager.class, context = {InjectionContext.server,InjectionContext.swing})
public class DefaultStorageLockManager implements StorageLockManager
{
    final protected ReadWriteLock lock = new ReentrantReadWriteLock();

    @Inject
    public DefaultStorageLockManager()
    {

    }

    public WriteLock shortWriteLock() throws RaplaException
    {
        return new WriteLock(RaplaConcurrency.lock(this.lock.writeLock(), 10));
    }

    public WriteLock longWriteLock() throws RaplaException
    {
        final WriteLock lock = new WriteLock(RaplaConcurrency.lock(this.lock.writeLock(), 60));
        return lock;
    }

    public WriteLock writeLockIfAvaliable()
    {
        Lock writeLock = lock.writeLock();
        // dispatch also does an refresh without lock so we get the new data each time a store is called
        boolean tryLock = writeLock.tryLock();
        if (tryLock)
        {
            return new WriteLock(writeLock);
        }
        else
        {
            return null;
        }
    }


    public ReadLock readLock() throws RaplaException
    {
        final Lock lock = RaplaConcurrency.lock(this.lock.readLock(), 20);
        /*
        try
        {
            checkLoaded();
        }
        catch (Throwable ex)
        {
            lockManager.unlock( lock);
            if ( ex instanceof  RaplaException)
            {
                throw ex;
            }
            else
            {
                throw new RaplaException( ex);
            }
        }*/
        return new ReadLock(lock);
    }

    public boolean isWriteLocked()
    {
        Lock writeLock = lock.writeLock();
        boolean tryLock = writeLock.tryLock();
        if (tryLock)
        {
            writeLock.unlock();
        }
        return tryLock;
    }

    public void unlock(ReadLock lock)
    {
        if (lock == null)
        {
            return;
        }
        RaplaConcurrency.unlock((Lock) lock.lock);
    }

    public void unlock(WriteLock lock)
    {
        if (lock == null)
        {
            return;
        }
        RaplaConcurrency.unlock((Lock) lock.lock);
    }
}
