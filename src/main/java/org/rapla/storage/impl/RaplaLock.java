package org.rapla.storage.impl;

import org.jetbrains.annotations.NotNull;
import org.rapla.framework.RaplaException;

public interface RaplaLock
{
    WriteLock writeLockIfAvaliable();

    WriteLock writeLock() throws RaplaException;

    WriteLock writeLock(int seconds) throws RaplaException;

    ReadLock readLock() throws RaplaException;

    ReadLock readLock(int seconds) throws RaplaException;

    boolean isWriteLocked();

    boolean isReadLocked();

    void unlock(ReadLock lock);

    void unlock(WriteLock lock);

    interface LockInfo
    {
        StackTraceElement[] getStackTrace();
        long getLockTime();
    }

    class ReadLock implements LockInfo
    {
        final Object lock;
        final StackTraceElement[] stackTrace;
        final long lockTime;
        public ReadLock(Object lock,StackTraceElement[] stackTrace, long lockTime)
        {
            this.stackTrace = stackTrace;
            this.lock = lock;
            this.lockTime = lockTime;

        }

        public StackTraceElement[] getStackTrace()
        {
            return stackTrace;
        }

        public long getLockTime()
        {
            return lockTime;
        }

        @Override
        public String toString()
        {
            return Tools.toString("Readlock", stackTrace);
        }


    }

    class WriteLock implements LockInfo
    {
        final Object lock;
        final StackTraceElement[] stackTrace;
        final long lockTime;

        public WriteLock(Object lock,StackTraceElement[] stackTrace, long lockTime)
        {
            this.stackTrace = stackTrace;
            this.lock = lock;
            this.lockTime = lockTime;
        }

        public StackTraceElement[] getStackTrace()
        {
            return stackTrace;
        }

        public long getLockTime()
        {
            return lockTime;
        }

        @Override
        public String toString()
        {
            return Tools.toString("Writelock", stackTrace);
        }
    }

    class Tools
    {
        static String toString(String message, StackTraceElement[] stackTrace)
        {
            StringBuilder builder = new StringBuilder();
            if (stackTrace != null)
            {
                for (StackTraceElement element : stackTrace)
                {
                    builder.append("\n\tat ");
                    builder.append(element.toString());
                }
            }
            builder.append(message);
            return builder.toString();
        }
    }
}
