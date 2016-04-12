package org.rapla.client.swing.internal.dagger;

import java.net.URL;

import javax.inject.Provider;

import org.rapla.client.ClientService;
import org.rapla.client.UserClientService;
import org.rapla.client.swing.dagger.DaggerRaplaJavaClientStartupModule;
import org.rapla.framework.StartupEnvironment;
import org.rapla.inject.dagger.DaggerReflectionStarter;
import org.rapla.rest.client.EntryPointFactory;
import org.rapla.rest.client.swing.BasicRaplaHTTPConnector;

public class DaggerClientCreator
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
        String moduleId = DaggerReflectionStarter.loadModuleId( ClientService.class.getClassLoader());
        return create(startupEnvironment, moduleId);
    }

    public static ClientService create(StartupEnvironment startupEnvironment, String moduleId) throws Exception
    {

        final ClientService client;
        UserServiceProvider userClientServiceProvider = new UserServiceProvider();
        final DaggerRaplaJavaClientStartupModule startupModule = new DaggerRaplaJavaClientStartupModule(startupEnvironment,userClientServiceProvider);
        boolean useReflection = true;
        if (useReflection)
        {
            client = DaggerReflectionStarter.startWithReflectionAndStartupModule(moduleId,ClientService.class, DaggerReflectionStarter.Scope.JavaClient, startupModule);
        }
        else
        {
            org.rapla.client.swing.dagger.RaplaJavaClientComponent component= org.rapla.client.swing.dagger.DaggerRaplaJavaClientComponent.builder().daggerRaplaJavaClientStartupModule(startupModule).build();
            client = component.getClientService();
        }
        userClientServiceProvider.setClient( (UserClientService) client );
        final URL downloadURL = startupEnvironment.getDownloadURL();
        BasicRaplaHTTPConnector.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(String interfaceName, String relativePath)
            {
                String url = downloadURL.toExternalForm()  + "rest/" +((relativePath != null) ? relativePath: interfaceName);
                return url;
            }
        });
        return client;
    }

}
