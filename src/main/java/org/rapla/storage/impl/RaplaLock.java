package org.rapla.storage.impl;

import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaException;

import java.util.Date;

public interface RaplaLock
{
    WriteLock writeLockIfAvaliable(Class callerClass,String name);
    WriteLock writeLock(Class callerClass,String name) throws RaplaException;
    WriteLock writeLock(Class callerClass,String name,int seconds) throws RaplaException;

    ReadLock readLock(Class callerClass, String name) throws RaplaException;
    ReadLock readLock(Class callerClass, String name,int seconds) throws RaplaException;

    boolean isWriteLocked();
    boolean isReadLocked();

    void unlock(ReadLock lock);
    void unlock(WriteLock lock);

    interface LockInfo
    {
        StackTraceElement[] getStackTrace();
        long getLockTime();
    }

    class ReadLock extends AbstractLock
    {
        public ReadLock(Object lock, Class lockClazz, String lockname, StackTraceElement[] stackTrace, long lockTime)
        {
            super(lock, lockClazz, lockname, stackTrace, lockTime, "Readlock");
        }
    }
    class WriteLock extends AbstractLock
    {
        public WriteLock(Object lock, Class lockClazz, String lockname, StackTraceElement[] stackTrace, long lockTime)
        {
            super(lock, lockClazz, lockname, stackTrace, lockTime, "Writelock");
        }
    }

    class AbstractLock implements LockInfo
    {
        final Object lock;
        final StackTraceElement[] stackTrace;
        final long lockTime;
        final Class lockClazz;
        final String lockname;
        final private String lockType;

        public AbstractLock(Object lock, Class lockClazz, String lockname, StackTraceElement[] stackTrace, long lockTime, String lockType)
        {
            this.stackTrace = stackTrace;
            this.lock = lock;
            this.lockTime = lockTime;
            this.lockClazz = lockClazz;
            this.lockname = lockname;
            this.lockType = lockType;
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
            return Tools.toString(lockType + " [" + DateTools.formatTime(new Date(lockTime)) + "]" + lockClazz + ":" + lockname, stackTrace);
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
