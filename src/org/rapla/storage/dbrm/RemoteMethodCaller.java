package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

public interface RemoteMethodCaller {
	Object call( Class<?> service, String methodName,Class<?>[] parameterTypes, Class<?> returnType ,Object[] args) throws RaplaException;
	void setStatusUpdater(StatusUpdater statusUpdater);
}
