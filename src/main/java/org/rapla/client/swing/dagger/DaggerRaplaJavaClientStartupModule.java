package org.rapla.client.swing.dagger;

import javax.inject.Provider;
import javax.inject.Singleton;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.rapla.client.UserClientService;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.iolayer.WebstartIO;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;

import dagger.Module;
import dagger.Provides;

@Module public class DaggerRaplaJavaClientStartupModule
{
    private final StartupEnvironment context;
    private final Logger logger;
    private final Provider<UserClientService> userClientServiceProvider;
    private final EventBus eventBus;

    public DaggerRaplaJavaClientStartupModule(StartupEnvironment context,Provider<UserClientService> userClientServiceProvider)
    {
        this.context = context;
        this.logger = context.getBootstrapLogger();
        this.userClientServiceProvider = userClientServiceProvider;
        this.eventBus = new SimpleEventBus();
    }

    @Provides @Singleton public UserClientService provideService()
    {
        return userClientServiceProvider.get();
    }

    @Provides @Singleton public Logger provideLogger()
    {
        return logger;
    }

    @Provides @Singleton public EventBus provideEventBus()
    {
        return eventBus;
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
