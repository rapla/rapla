package org.rapla.client.swing.internal.dagger;

import org.rapla.client.ClientService;
import org.rapla.client.swing.dagger.RaplaJavaClientComponent;
import org.rapla.client.swing.dagger.DaggerRaplaJavaClientStartupModule;
import org.rapla.framework.StartupEnvironment;
import org.rapla.inject.dagger.DaggerReflectionStarter;
import org.rapla.jsonrpc.client.EntryPointFactory;
import org.rapla.jsonrpc.client.swing.BasicRaplaHTTPConnector;

import java.net.URL;

public class DaggerClientCreator
{
    public static ClientService create(StartupEnvironment startupEnvironment) throws Exception
    {
        final ClientService client;
        final DaggerRaplaJavaClientStartupModule startupModule = new DaggerRaplaJavaClientStartupModule(startupEnvironment);
        boolean useReflection = true;
        if (useReflection)
        {
            client = DaggerReflectionStarter.startWithReflectionAndStartupModule(ClientService.class, DaggerReflectionStarter.Scope.JavaClient, startupModule);
        }
        else
        {
            RaplaJavaClientComponent component= org.rapla.client.swing.dagger.DaggerRaplaJavaClientComponent.builder().daggerRaplaJavaClientStartupModule(startupModule).build();
            client = component.getClientService();
        }

        URL downloadURL = startupEnvironment.getDownloadURL();
        BasicRaplaHTTPConnector.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(String interfaceName, String relativePath)
            {
                String url = downloadURL.toExternalForm()  + "rapla/json/" +((relativePath != null) ? relativePath: interfaceName);
                return url;
            }
        });
        return client;
    }
}
