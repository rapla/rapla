package org.rapla.server;



public interface RemoteMethodFactory<T> {

    public T createService(final RemoteSession remoteSession);
}
