package org.rapla.server.dagger;

import org.rapla.inject.dagger.DaggerReflectionStarter;
import org.rapla.logger.Logger;
import org.rapla.rest.server.Injector;
import org.rapla.rest.server.ReflectionMembersInjector;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.console.ImportExportManagerContainer;

public class DaggerServerCreator
{
    public static class ServerContext
    {
        private Injector membersInjector;
        private ServerServiceContainer serviceContainer;

        public ServerServiceContainer getServiceContainer()
        {
            return serviceContainer;
        }

        public Injector getMembersInjector()
        {
            return membersInjector;
        }
    }
    public static ServerContext create(Logger logger, ServerContainerContext containerContext) throws Exception
    {
        ServerContext result = new ServerContext();
//        SimpleRaplaInjector injector = new SimpleRaplaInjector( logger);
//        injector.addComponentInstance(Logger.class,logger);
//        injector.addComponentInstance(ServerContainerContext.class, containerContext);
//        injector.addComponentProvider(CachableStorageOperator.class, ServerStorageSelector.class);
//        injector.addComponentProvider(StorageOperator.class, ServerStorageSelector.class);
//        injector.initFromMetaInfService(InjectionContext.server);
//        result.membersInjector =injector.getMembersInjector();
//        result.serviceContainer = injector.inject(ServerServiceImpl.class);
//        return result;

        final DaggerRaplaServerStartupModule startupModule = new DaggerRaplaServerStartupModule(containerContext, logger);
        boolean useReflection = true;
        if (useReflection)
        {

            String moduleId = DaggerReflectionStarter.loadModuleId(ServerServiceContainer.class.getClassLoader());
            final Object component = DaggerReflectionStarter.buildComponent(moduleId, DaggerReflectionStarter.Scope.Server, startupModule);
            final Class aClass = component.getClass();
            final ReflectionMembersInjector reflectionMembersInjector = new ReflectionMembersInjector(aClass, component);
            result.membersInjector = reflectionMembersInjector;
            result.serviceContainer = DaggerReflectionStarter.createObject( ServerServiceContainer.class,component);
        }
        else
        {
            org.rapla.server.dagger.RaplaServerComponent component = org.rapla.server.dagger.DaggerRaplaServerComponent.builder().daggerRaplaServerStartupModule(startupModule).build();
            final ReflectionMembersInjector reflectionMembersInjector = new ReflectionMembersInjector(RaplaServerComponent.class, component);
            result.membersInjector = reflectionMembersInjector;
            result.serviceContainer = component.getServerServiceContainer();
        }
        return result;
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
            //org.rapla.server.dagger.RaplaServerComponent component = org.rapla.server.dagger.DaggerRaplaServerComponent.builder().daggerRaplaServerStartupModule(startupModule).build();
            server = null;//component.getImportExportManagerContainer();
        }
        return server;
    }

}
