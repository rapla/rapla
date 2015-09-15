package org.rapla.server;

import org.rapla.framework.RaplaContextException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(id="rpc", context=InjectionContext.server)
public interface RemoteMethodFactory<T> {
    public T createService(final RemoteSession remoteSession) throws RaplaContextException;
    public Class<T> getInterfaceClass() ;
}
