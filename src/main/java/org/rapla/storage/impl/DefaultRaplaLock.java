package org.rapla.storage.impl;

import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@DefaultImplementation(of = RaplaLock.class, context = { InjectionContext.server, InjectionContext.swing })
public class DefaultRaplaLock implements RaplaLock
{
    public static final int DEFAULT_READLOCK_TIMEOUT_SECONDS = 20;
    public static final int DEFAULT_WRITELOCK_TIMEOUT_SECONDS = 60;
    final protected ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    Stack<WriteLock> writeLocks = new Stack<>();
    Stack<ReadLock> readLocks = new Stack<>();
    Logger logger;

    @Inject
    public DefaultRaplaLock(Logger raplaLogger)
    {
        this.logger = raplaLogger;
    }

    public WriteLock writeLock(Class clazz, String name) throws RaplaException
    {
        return writeLock(clazz, name,DEFAULT_WRITELOCK_TIMEOUT_SECONDS);
    }

    @Override
    public ReadLock readLock(Class clazz, String name,int seconds) throws RaplaException
    {
        // Enabling the stack trace is a huge performance cost
        StackTraceElement[] stackTrace = logger.isTraceEnabled() ? getStackTrace() : new StackTraceElement[] {};
        final long currentTime = System.currentTimeMillis();
        final Lock lock = lock(this.readWriteLock.readLock(), seconds, true);
        final ReadLock readLock = new ReadLock(lock,clazz, name, stackTrace, currentTime);
        readLocks.add(readLock);
        return readLock;
    }

    public static void unlock(Lock lock)
    {
        if (lock != null)
        {
            lock.unlock();
        }
    }

    private Lock lock(Lock lock, int seconds, boolean isRead) throws RaplaException
    {
        try
        {
            if (lock.tryLock())
            {
                return lock;
            }
            if (lock.tryLock(seconds, TimeUnit.SECONDS))
            {
                return lock;
            }
            else
            {
                if (logger != null)
                {
                    int logThreshholdTime = 0;
                    logLongLocks(this.writeLocks, logThreshholdTime);
                    logLongLocks(this.readLocks, logThreshholdTime);
                }
                if ( isRead)
                {
                    throw new RaplaSynchronizationException("Someone is currently writing. Please try again! Can't acquire read lock.");
                }
                else
                {
                    throw new RaplaSynchronizationException("Someone is currently reading or writing. Please try again! Can't acquire write lock." + readLocks);
                }
            }
        }
        catch (InterruptedException ex)
        {
            throw new RaplaSynchronizationException(ex);
        }
    }

    private void logLongLocks(Stack<? extends LockInfo> lockCollection, int logThreshholdTime)
    {
        final long currentTime = System.currentTimeMillis();
        final LockInfo[] locks = lockCollection.toArray(new LockInfo[] {});
        for (int i = 0; i < locks.length; i++)
        {
            final LockInfo lock = locks[i];
            final long timeSinceLock = (currentTime - lock.getLockTime())/ 1000;
            if ( timeSinceLock > logThreshholdTime )
            {
                final RaplaSynchronizationException ex = new RaplaSynchronizationException(
                        "Current lock [" + i + "] is blocking for " + timeSinceLock + " seconds " + lock);
                ex.setStackTrace( lock.getStackTrace());
                logger.warn("Lock Blocking ", ex);
            }
        }
    }

    public WriteLock writeLock(Class clazz, String name,int seconds) throws RaplaException
    {
        final WriteLock lock;
        StackTraceElement[] stackTrace = logger.isDebugEnabled() ? getStackTrace() : new StackTraceElement[] {};
        final long currentTime = System.currentTimeMillis();
        if (seconds > 0)
        {
            lock = new WriteLock(lock(this.readWriteLock.writeLock(), seconds, false),clazz, name, stackTrace, currentTime);
        }
        else
        {
            Lock writeLock = this.readWriteLock.writeLock();
            // dispatch also does an refresh without lock so we get the new data each time a store is called
            boolean tryLock = writeLock.tryLock();
            if (tryLock)
            {
                lock = new WriteLock(writeLock,clazz, name, stackTrace, currentTime);
            }
            else
            {
                lock = null;
            }
        }
        if (lock != null)
        {
            writeLocks.add(lock);
        }
        return lock;
    }

    private StackTraceElement[] getStackTrace()
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // We remove the first StackTraceElement
        final int excludeFirstStackTraceRows = 3;
        if (stackTrace != null && stackTrace.length > excludeFirstStackTraceRows)
        {
            final int maxLength = Math.min(25,stackTrace.length - excludeFirstStackTraceRows);
            StackTraceElement[] newStackTrace = new StackTraceElement[maxLength];
            for (int i = 0; i < maxLength; i++)
            {
                newStackTrace[i] = stackTrace[i + excludeFirstStackTraceRows];
            }
            stackTrace = newStackTrace;
        }
        return stackTrace;
    }

    public WriteLock writeLockIfAvaliable(Class clazz, String name)
    {
        try
        {
            return writeLock(clazz, name,0);
        }
        catch (RaplaException e)
        {
            throw new IllegalStateException("This state should never been reached");
            //should never been thrown;
        }
    }

    @Override
    public ReadLock readLock(Class clazz, String name) throws RaplaException
    {
        return readLock(clazz, name,DEFAULT_READLOCK_TIMEOUT_SECONDS);
    }

    @Override
    public boolean isWriteLocked()
    {
        Lock writeLock = readWriteLock.writeLock();
        boolean tryLock = writeLock.tryLock();
        if (tryLock)
        {
            writeLock.unlock();
        }
        return tryLock;
    }

    @Override
    public boolean isReadLocked()
    {
        Lock readLock = readWriteLock.readLock();
        boolean tryLock = readLock.tryLock();
        if (tryLock)
        {
            readLock.unlock();
        }
        return tryLock;
    }

    public void unlock(ReadLock lock)
    {
        if (lock == null)
        {
            return;
        }
        ((Lock) lock.lock).unlock();
        readLocks.remove(lock);
    }

    public void unlock(WriteLock lock)
    {
        if (lock == null)
        {
            return;
        }
        ((Lock) lock.lock).unlock();
        writeLocks.remove(lock);
    }

}

