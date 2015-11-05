package org.rapla.client.swing.internal.dagger;

import org.rapla.client.internal.RaplaClientServiceImpl;
import org.rapla.dagger.DaggerJavaClientModule;

import javax.inject.Singleton;

@Singleton @dagger.Component(modules = { DaggerJavaClientModule.class, MyClientModule.class })
public interface ClientComponent
{
    RaplaClientServiceImpl getClient();
}
