package org.rapla.storage.impl;

import org.rapla.framework.RaplaException;

public interface StorageLockManager
{
    WriteLock writeLockIfAvaliable();

    WriteLock shortWriteLock() throws RaplaException;

    WriteLock longWriteLock() throws RaplaException;

    ReadLock readLock() throws RaplaException;

    boolean isWriteLocked();

    void unlock(ReadLock lock);

    void unlock(WriteLock lock);

    class ReadLock
    {
        final Object lock;

        public ReadLock(Object lock)
        {
            this.lock = lock;
        }

    }

    class WriteLock
    {
        final Object lock;

        public WriteLock(Object lock)
        {
            this.lock = lock;
        }
    }
}
