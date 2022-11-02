package org.rapla.server;

import org.jetbrains.annotations.NotNull;
import org.rapla.inject.InjectionContext;
import org.rapla.inject.Injector;
import org.rapla.inject.raplainject.SimpleRaplaInjector;
import org.rapla.inject.scanning.ScanningClassLoader;
import org.rapla.inject.scanning.ServiceInfLoader;
import org.rapla.logger.Logger;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.server.internal.ShutdownService;
import org.rapla.server.internal.console.ImportExportManagerContainer;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;

import java.util.Collection;

public class ServerCreator
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
        {
            //JsonParserWrapper.setFactory(new GsonParserWrapper());
            SimpleRaplaInjector injector = createInjector(logger, containerContext);
            result.membersInjector = injector.getMembersInjector();
            result.serviceContainer = injector.getInstance(ServerServiceContainer.class);
            return result;
        }

    }

    @NotNull
    private static SimpleRaplaInjector createInjector(Logger logger, ServerContainerContext containerContext) throws Exception {
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
        return injector;
    }

    public static ImportExportManagerContainer createImportExport(Logger logger, ServerContainerContext containerContext) throws Exception
    {
        final SimpleRaplaInjector injector = createInjector(logger, containerContext);
        final ImportExportManagerContainer instance = injector.getInstance(ImportExportManagerContainer.class);
        return instance;
    }

}
