package org.rapla.inject;

public interface Injector<T>
{
    void injectMembers(T instance) throws Exception;
}
