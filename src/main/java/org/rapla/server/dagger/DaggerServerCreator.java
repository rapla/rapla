package org.rapla.server.dagger;

import org.rapla.inject.InjectionContext;
import org.rapla.inject.Injector;
import org.rapla.inject.ReflectionMembersInjector;
import org.rapla.inject.dagger.DaggerReflectionStarter;
import org.rapla.inject.raplainject.SimpleRaplaInjector;
import org.rapla.inject.scanning.ScanningClassLoader;
import org.rapla.inject.scanning.ServiceInfLoader;
import org.rapla.logger.Logger;
import org.rapla.server.ServerService;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.server.internal.ShutdownService;
import org.rapla.server.internal.console.ImportExportManagerContainer;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;

import java.util.Collection;

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
        if ( true )
        {
            SimpleRaplaInjector injector = new SimpleRaplaInjector(logger);
            injector.addComponentInstance(Logger.class, logger);
            injector.addComponentInstance(ServerContainerContext.class, containerContext);
            injector.addComponentProvider(CachableStorageOperator.class, ServerStorageSelector.class);
            injector.addNamedComponentInstanceProvider(ServerService.ENV_RAPLAMAIL_ID, () -> containerContext.getMailSession());
            injector.addComponentInstanceProvider(ShutdownService.class, () -> containerContext.getShutdownService());
            injector.addComponentProvider(StorageOperator.class, ServerStorageSelector.class);
            ScanningClassLoader.LoadingFilter filter = null;
            final ScanningClassLoader.LoadingResult loadingResult = new ServiceInfLoader().loadClassesFromServiceInfFile(filter, InjectionContext.MODULE_LIST_LOCATION);
            Collection<? extends Class> classes = loadingResult.getClasses();
            for (Throwable error : loadingResult.getErrors())
            {
                logger.error(error.getMessage(), error);
            }
            injector.initFromClasses(InjectionContext.server, classes);
            result.membersInjector = injector.getMembersInjector();
            result.serviceContainer = injector.getInstance(ServerServiceContainer.class);
            return result;
        }

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
