package org.rapla.storage.dbrm;

import java.io.IOException;

import com.google.gwtjsonrpc.common.FutureResult;


public interface Connector
{
    String getInfo();

	FutureResult call(FutureResult<String> authFailedCommand, String accessToken,Class<?> service, String methodName, Class<?>[] parameterTypes,	Class<?> returnType, Object[] args) throws IOException;
    
}
