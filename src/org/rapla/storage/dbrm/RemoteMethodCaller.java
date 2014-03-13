package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

import com.google.gwtjsonrpc.common.FutureResult;

public interface RemoteMethodCaller {
	FutureResult call( Class<?> service, String methodName,Class<?>[] parameterTypes, Class<?> returnType ,Object[] args) throws RaplaException;
	void setStatusUpdater(StatusUpdater statusUpdater);
}
