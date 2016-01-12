package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

public class ReentrantReadWriteLock implements ReadWriteLock {
    Lock lock = new Lock()
    {
        public void lock()
        {
        	
        }
        public void unlock()
        {
        	
        }
        
        public boolean tryLock()
        {
        	return true;
        }
        
        
        public boolean tryLock(int time, TimeUnit timeUnit) throws InterruptedException
        {
        	return true;
        }

    };
	
	public Lock readLock()
    {
    	return lock;
    }
    public Lock writeLock()
    {
    	return lock;
    }
}