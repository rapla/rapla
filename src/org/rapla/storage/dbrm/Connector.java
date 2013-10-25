package org.rapla.storage.dbrm;

import java.io.IOException;
import java.lang.reflect.Method;

import org.rapla.framework.RaplaException;

public interface Connector
{
    String getInfo();

	Object call(Class<?> service, Method method, Object[] args,	RemoteMethodSerialization remoteMethodSerialization) throws IOException, RaplaException;
    
}
