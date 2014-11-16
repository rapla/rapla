package org.rapla.rest.gwtjsonrpc.client.impl.ser;

import javax.inject.Provider;

public class SimpleProvider<T> implements Provider<T> {
    T t;
    public SimpleProvider(T t) {
        this.t = t;
    }
    
    public T get()
    {
        return t;
    }
   
}
