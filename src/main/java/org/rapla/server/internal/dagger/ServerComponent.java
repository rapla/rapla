package org.rapla.server.internal.dagger;

import org.rapla.dagger.DaggerServerModule;
import org.rapla.dagger.DaggerWebserviceComponent;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.dagger.MyModule;

import javax.inject.Singleton;

@Singleton @dagger.Component(modules = { DaggerServerModule.class, MyModule.class })
public interface ServerComponent
{
    DaggerWebserviceComponent getWebservices();

    ServerServiceImpl getServer();
}
