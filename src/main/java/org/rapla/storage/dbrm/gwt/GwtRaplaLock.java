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
    public WriteLock writeLockIfAvaliable(Class clazz, String name)
    {
        return writeLock(clazz, name,0);
    }

    @Override
    public WriteLock writeLock(Class clazz,String name) throws RaplaException
    {
        return writeLock(clazz,name,60);
    }

    @Override
    public WriteLock writeLock(Class clazz,String name,int seconds)
    {
        final WriteLock writeLock = new WriteLock(null, clazz, name,new StackTraceElement[] {}, System.currentTimeMillis());
        return writeLock;
    }

    @Override
    public ReadLock readLock(Class clazz, String name) throws RaplaException
    {
        return readLock( clazz,name,20);
    }

    @Override
    public ReadLock readLock(Class clazz, String name,int seconds) throws RaplaException
    {
        return new ReadLock(null, clazz, name,new StackTraceElement[] {},System.currentTimeMillis());
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
