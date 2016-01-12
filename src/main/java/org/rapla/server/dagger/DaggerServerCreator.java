package org.rapla.server.dagger;

import org.rapla.framework.logger.Logger;
import org.rapla.inject.dagger.DaggerReflectionStarter;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.ServerContainerContext;

public class DaggerServerCreator
{
    public static ServerServiceContainer create(Logger logger, ServerContainerContext containerContext) throws Exception
    {
        final DaggerRaplaServerStartupModule startupModule = new DaggerRaplaServerStartupModule(containerContext, logger);
        final ServerServiceContainer server;
        boolean useReflection = true;
        if (useReflection)
        {
            String moduleId = DaggerReflectionStarter.loadModuleId(ServerServiceContainer.class.getClassLoader());
            server = DaggerReflectionStarter.startWithReflectionAndStartupModule(moduleId,ServerServiceContainer.class, DaggerReflectionStarter.Scope.Server, startupModule);
        }
        else
        {
            RaplaServerComponent component = org.rapla.server.dagger.DaggerRaplaServerComponent.builder().daggerRaplaServerStartupModule(startupModule).build();
            server = component.getServerServiceContainer();
        }
        return server;
    }

}
