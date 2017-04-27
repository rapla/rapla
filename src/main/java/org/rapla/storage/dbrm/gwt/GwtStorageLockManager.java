package org.rapla.storage.dbrm.gwt;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.impl.StorageLockManager;

import javax.inject.Inject;

@DefaultImplementation(of = StorageLockManager.class, context = { InjectionContext.gwt})
public class GwtStorageLockManager implements StorageLockManager
{

    @Inject
    public GwtStorageLockManager()
    {

    }
    @Override
    public WriteLock writeLockIfAvaliable()
    {
        return new WriteLock(null);
    }

    @Override
    public WriteLock shortWriteLock() throws RaplaException
    {
        return new WriteLock(null);
    }

    @Override
    public WriteLock longWriteLock() throws RaplaException
    {
        return new WriteLock(null);
    }

    @Override
    public ReadLock readLock() throws RaplaException
    {
        return new ReadLock(null);
    }

    @Override
    public boolean isWriteLocked()
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
