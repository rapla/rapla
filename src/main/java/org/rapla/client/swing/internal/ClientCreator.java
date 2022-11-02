package org.rapla.client.swing.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.client.ClientService;
import org.rapla.client.UserClientService;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.iolayer.WebstartIO;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.StartupEnvironment;
import org.rapla.inject.InjectionContext;
import org.rapla.inject.raplainject.SimpleRaplaInjector;
import org.rapla.inject.scanning.ScanningClassLoader;
import org.rapla.inject.scanning.ServiceInfLoader;
import org.rapla.logger.Logger;

import javax.inject.Provider;
import java.util.Collection;

public class ClientCreator
{
    static class UserServiceProvider implements  Provider<UserClientService>
    {
        UserClientService client;

        @Override public UserClientService get()
        {
            return client;
        }

        public void setClient(UserClientService client)
        {
            this.client = client;
        }
    }

    public static ClientService create(StartupEnvironment startupEnvironment) throws Exception
    {
        SimpleRaplaInjector injector = initInjector(startupEnvironment, InjectionContext.swing);
        UserServiceProvider userClientServiceProvider = new UserServiceProvider();
        injector.addComponentInstanceProvider(UserClientService.class, userClientServiceProvider);
        final ClientService client = injector.getInstance( ClientService.class);
        userClientServiceProvider.setClient( (UserClientService) client );
        return client;
    }

    @NotNull
    private static SimpleRaplaInjector initInjector(StartupEnvironment startupEnvironment, InjectionContext injectionContext) throws Exception
    {
        Logger logger = startupEnvironment.getBootstrapLogger();
        boolean webstartEnabled = startupEnvironment.getStartupMode() == StartupEnvironment.WEBSTART;
        SimpleRaplaInjector injector = new SimpleRaplaInjector( logger);
        injector.addComponentInstance(Logger.class,logger);
        injector.addComponentInstanceProvider(IOInterface.class, () -> webstartEnabled ? new WebstartIO(logger): new DefaultIO(logger));
        injector.addComponentInstance(StartupEnvironment.class, startupEnvironment);
        ServiceInfLoader.LoadingFilter filter = new ServiceInfLoader.LoadingFilter()
        {
            @Override public boolean classNameShouldBeIgnored(String classname)
            {
                return classname.contains(".server.") || classname.contains(".storage.dbfile.") || classname.contains(".storage.dbsql.");
            }

            @Override public String[] getIgnoredPackages()
            {
                return new String[0];
            }
        };
        final ScanningClassLoader.LoadingResult loadingResult = new ServiceInfLoader().loadClassesFromServiceInfFile(filter,InjectionContext.MODULE_LIST_LOCATION);
        Collection<? extends Class> classes = loadingResult.getClasses();
        for ( Throwable error:loadingResult.getErrors())
        {
            logger.error( error.getMessage(), error);
        }
        injector.initFromClasses(injectionContext, classes);
        return injector;
    }

    public static ClientFacade createFacade(StartupEnvironment startupEnvironment) throws Exception
    {
        final SimpleRaplaInjector simpleRaplaInjector = initInjector(startupEnvironment, InjectionContext.client);
        final ClientFacade facade = simpleRaplaInjector.inject(ClientFacade.class);
        return facade;
    }

}
