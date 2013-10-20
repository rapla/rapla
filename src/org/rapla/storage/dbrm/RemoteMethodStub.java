package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

public interface RemoteMethodStub {
	  <T> T getWebserviceLocalStub(Class<T> role) throws RaplaException;
}
