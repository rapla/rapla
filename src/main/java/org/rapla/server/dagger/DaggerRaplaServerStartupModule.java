package org.rapla.server.dagger;

import dagger.Module;
import dagger.Provides;
import org.rapla.logger.Logger;
import org.rapla.server.ServerService;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.server.internal.ShutdownService;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;

import javax.inject.Named;

@Module public class DaggerRaplaServerStartupModule
{
    ServerContainerContext context;
    Logger logger;

    public DaggerRaplaServerStartupModule(ServerContainerContext context, Logger logger)
    {
        this.context = context;
        this.logger = logger;
    }

    @Provides public Logger provideLogger()
    {
        return logger;
    }

    @Provides ServerContainerContext provideContext()
    {
        return context;
    }

    @Named(ServerService.ENV_RAPLAMAIL_ID) @Provides Object mail()
    {
        return context.getMailSession();
    }

    @Provides ShutdownService st()
    {
        return context.getShutdownService();
    }

    @Provides CachableStorageOperator provide1(ServerStorageSelector selector)
    {
        return selector.get();
    }

    @Provides StorageOperator provide2(ServerStorageSelector selector)
    {
        return selector.get();
    }

}
