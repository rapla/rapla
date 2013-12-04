package org.rapla.server;

import org.rapla.framework.RaplaContextException;

import com.google.gwtjsonrpc.common.RemoteJsonService;

public interface RemoteJsonFactory<T extends RemoteJsonService> {
	   public T createService(final RemoteSession remoteSession) throws RaplaContextException;
}
