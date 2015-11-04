package org.rapla.server;

import org.rapla.framework.RaplaContextException;

@Deprecated
public interface RemoteMethodFactory<T,G> {
    public T createService(final G remoteSession) throws RaplaContextException;
}
