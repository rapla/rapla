package org.rapla.server;

import org.rapla.framework.RaplaContextException;
import org.rapla.inject.ExtensionPoint;

@ExtensionPoint(id="rpc")
public interface RemoteMethodFactory<T> {
    public T createService(final RemoteSession remoteSession) throws RaplaContextException;
    public Class<T> getInterfaceClass() ;
}
