package org.rapla.storage.dbrm.gwt;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.impl.RaplaLock;

import javax.inject.Inject;

@DefaultImplementation(of = RaplaLock.class, context = { InjectionContext.gwt})
public class GwtRaplaLock implements RaplaLock
{

    @Inject
    public GwtRaplaLock()
    {

    }
    @Override
    public WriteLock writeLockIfAvaliable()
    {
        return writeLock(0);
    }

    @Override
    public WriteLock writeLock() throws RaplaException
    {
        return writeLock(60);
    }

    @Override
    public WriteLock writeLock(int seconds)
    {
        return new WriteLock(null, new StackTraceElement[] {}, System.currentTimeMillis());
    }

    @Override
    public ReadLock readLock() throws RaplaException
    {
        return readLock( 20);
    }

    @Override
    public ReadLock readLock(int seconds) throws RaplaException
    {
        return new ReadLock(null, new StackTraceElement[] {},System.currentTimeMillis());
    }

    @Override
    public boolean isWriteLocked()
    {
        return false;
    }

    @Override
    public boolean isReadLocked()
    {
        return false;
    }

    @Override
    public void unlock(ReadLock lock)
    {

    }

    @Override
    public void unlock(WriteLock lock)
    {

    }
}
