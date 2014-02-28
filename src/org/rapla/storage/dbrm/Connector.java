package org.rapla.storage.dbrm;

import java.io.IOException;

import org.rapla.framework.RaplaException;


public interface Connector
{
    String getInfo();

	Object call(Class<?> service, String methodName, Class<?>[] parameterTypes,	Class<?> returnType, Object[] args) throws IOException, RaplaException;
    
}
