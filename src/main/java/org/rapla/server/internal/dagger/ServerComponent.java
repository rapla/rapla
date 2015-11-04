package org.rapla.server.internal.dagger;

import javax.inject.Singleton;

import org.rapla.dagger.DaggerServerModule;
import org.rapla.dagger.DaggerWebserviceComponent;
import org.rapla.server.internal.ServerServiceImpl;

@Singleton @dagger.Component(modules = { DaggerServerModule.class, MyModule.class })
public interface ServerComponent
{
    DaggerWebserviceComponent getWebservices();

    ServerServiceImpl getServer();
}
