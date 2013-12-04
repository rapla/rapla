package org.rapla.server;

import org.rapla.framework.RaplaContextException;



public interface RemoteMethodFactory<T> {

    public T createService(final RemoteSession remoteSession) throws RaplaContextException;
}
