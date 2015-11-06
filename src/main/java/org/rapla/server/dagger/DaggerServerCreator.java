package org.rapla.server.dagger;

import org.rapla.dagger.DaggerRaplaServerStartupModule;
import org.rapla.framework.logger.Logger;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.ServerServiceImpl;

public class DaggerServerCreator
{
    public static ServerServiceContainer create(Logger logger, ServerServiceImpl.ServerContainerContext containerContext)
    {
        final DaggerRaplaServerStartupModule daggerRaplaServerStartupModule = new DaggerRaplaServerStartupModule(containerContext, logger);
        final DaggerRaplaServerComponent.Builder builder = DaggerRaplaServerComponent.builder();
        final DaggerRaplaServerComponent.Builder builder1 = builder.daggerRaplaServerStartupModule(daggerRaplaServerStartupModule);
        RaplaServerComponent component= builder1.build();
        final ServerServiceContainer server = component.getServerServiceContainer();
        server.setServiceMap(component.getServiceMap());
        return server;
    }
}
