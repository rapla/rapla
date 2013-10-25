package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaContextException;

public interface RemoteMethodStub {
	  <T> T getWebserviceLocalStub(Class<T> role) throws RaplaContextException;
}
