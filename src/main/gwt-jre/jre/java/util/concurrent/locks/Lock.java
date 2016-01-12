package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

public interface Lock {
    public void lock();
    public void unlock();
    
    public boolean tryLock();
    
    public boolean tryLock(int time, TimeUnit timeUnit) throws InterruptedException;
    
    
}