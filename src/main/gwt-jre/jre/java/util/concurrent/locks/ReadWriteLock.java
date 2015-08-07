package java.util.concurrent.locks;

import java.util.concurrent.locks.Lock;

public interface ReadWriteLock {
    Lock readLock();
    Lock writeLock();
}