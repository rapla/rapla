package org.rapla.client.swing.dagger;

import dagger.Module;
import dagger.Provides;
import org.rapla.client.UserClientService;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.iolayer.WebstartIO;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;

import javax.inject.Singleton;

@Module public class DaggerRaplaJavaClientStartupModule
{
    StartupEnvironment context;
    Logger logger;

    public DaggerRaplaJavaClientStartupModule(StartupEnvironment context)
    {
        this.context = context;
        this.logger = context.getBootstrapLogger();
    }

    @Provides UserClientService provideService()
    {
        return new UserClientService()
        {
            @Override public boolean isRunning()
            {
                return false;
            }

            @Override public void switchTo(User user) throws RaplaException
            {

            }

            @Override public boolean canSwitchBack()
            {
                return false;
            }

            @Override public void restart()
            {

            }

            @Override public boolean isLogoutAvailable()
            {
                return false;
            }

            @Override public void logout()
            {

            }
        };
    }

    @Provides public Logger provideLogger()
    {
        return logger;
    }

    @Provides StartupEnvironment provideContext()
    {
        return context;
    }

    @Provides @Singleton IOInterface provideIOContext()
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
