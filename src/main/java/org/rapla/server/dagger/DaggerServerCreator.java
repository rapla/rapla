package org.rapla.server.dagger;

import org.rapla.framework.logger.Logger;
import org.rapla.inject.dagger.DaggerReflectionStarter;
import org.rapla.rest.server.ReflectionMembersInjector;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.console.ImportExportManagerContainer;

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
            final Object component = DaggerReflectionStarter.buildComponent(moduleId, DaggerReflectionStarter.Scope.Server, startupModule);
            server = DaggerReflectionStarter.createObject( ServerServiceContainer.class,component);
            final Class aClass = component.getClass();
            final ReflectionMembersInjector reflectionMembersInjector = new ReflectionMembersInjector(aClass, component);
            ((ServerServiceImpl)server).setMembersInjector(reflectionMembersInjector);
        }
        else
        {
            org.rapla.server.dagger.RaplaServerComponent component = null;//org.rapla.server.dagger.DaggerRaplaServerComponent.builder().daggerRaplaServerStartupModule(startupModule).build();
            server = component.getServerServiceContainer();
        }
        return server;
    }

    public static ImportExportManagerContainer createImportExport(Logger logger, ServerContainerContext containerContext) throws Exception
    {
        final DaggerRaplaServerStartupModule startupModule = new DaggerRaplaServerStartupModule(containerContext, logger);
        final ImportExportManagerContainer server;
        boolean useReflection = true;
        if (useReflection)
        {
            String moduleId = DaggerReflectionStarter.loadModuleId(ServerServiceContainer.class.getClassLoader());
            server = DaggerReflectionStarter.startWithReflectionAndStartupModule(moduleId,ImportExportManagerContainer.class, DaggerReflectionStarter.Scope.Server, startupModule);
        }
        else
        {
            org.rapla.server.dagger.RaplaServerComponent component = org.rapla.server.dagger.DaggerRaplaServerComponent.builder().daggerRaplaServerStartupModule(startupModule).build();
            server = null;//component.getImportExportManagerContainer();
        }
        return server;
    }

}
