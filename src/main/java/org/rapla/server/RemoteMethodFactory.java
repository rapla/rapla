package org.rapla.server;

import org.rapla.framework.RaplaContextException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@Deprecated
public interface RemoteMethodFactory<T,G> {
    public T createService(final G remoteSession) throws RaplaContextException;
}
