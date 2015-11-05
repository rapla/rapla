package org.rapla.client.swing.internal.dagger;

import dagger.Module;
import dagger.Provides;
import org.rapla.client.ClientService;
import org.rapla.client.RaplaClientListener;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.iolayer.WebstartIO;
import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.storage.StorageOperator;

import javax.inject.Singleton;

@Module public class MyClientModule
{
    StartupEnvironment context;
    Logger logger;

    public MyClientModule(StartupEnvironment context)
    {
        this.context = context;
        this.logger = context.getBootstrapLogger();
    }

    @Provides ClientService provideService()
    {
        return new ClientService()
        {
            @Override public void addRaplaClientListener(RaplaClientListener listener)
            {

            }

            @Override public void removeRaplaClientListener(RaplaClientListener listener)
            {

            }

            @Override public ClientFacade getFacade() throws RaplaContextException
            {
                return null;
            }

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
