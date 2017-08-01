package org.rapla.client.swing.dagger;

import dagger.Module;
import dagger.Provides;
import org.rapla.client.UserClientService;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.iolayer.WebstartIO;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;

import javax.inject.Provider;
import javax.inject.Singleton;

@Module public class DaggerRaplaJavaClientStartupModule
{
    private final StartupEnvironment context;
    private final Logger logger;
    private final Provider<UserClientService> userClientServiceProvider;

    public DaggerRaplaJavaClientStartupModule(StartupEnvironment context,Provider<UserClientService> userClientServiceProvider)
    {
        this.context = context;
        this.logger = context.getBootstrapLogger();
        this.userClientServiceProvider = userClientServiceProvider;
    }

    @Provides @Singleton public UserClientService provideService()
    {
        return userClientServiceProvider.get();
    }

    @Provides @Singleton public Logger provideLogger()
    {
        return logger;
    }

    @Provides @Singleton public StartupEnvironment provideContext()
    {
        return context;
    }

    @Provides @Singleton public IOInterface provideIOContext()
    {
        boolean webstartEnabled = context.getStartupMode() == StartupEnvironment.WEBSTART;
        if (webstartEnabled)
        {
            return new WebstartIO( logger);
        }
        else
        {
            return new DefaultIO( logger);
        }
    }

}
