package org.rapla.client.swing.internal.dagger;

import java.net.URL;

import org.rapla.client.ClientService;
import org.rapla.client.swing.dagger.RaplaJavaClientComponent;
import org.rapla.dagger.DaggerRaplaJavaClientStartupModule;
import org.rapla.framework.StartupEnvironment;
import org.rapla.jsonrpc.client.EntryPointFactory;
import org.rapla.jsonrpc.client.swing.BasicRaplaHTTPConnector;

public class DaggerClientCreator
{
    public static ClientService create(StartupEnvironment startupEnvironment)
    {
        RaplaJavaClientComponent component= org.rapla.client.swing.dagger.DaggerRaplaJavaClientComponent.builder().daggerRaplaJavaClientStartupModule( new DaggerRaplaJavaClientStartupModule(startupEnvironment)).build();
        final ClientService client = component.getClientService();
        URL downloadURL = startupEnvironment.getDownloadURL();
        BasicRaplaHTTPConnector.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(String interfaceName, String relativePath)
            {
                String url = downloadURL.toExternalForm()  + "rapla/json/" +((relativePath != null) ? relativePath: interfaceName);
                return url;
            }
        });
        //server.getMethodProvider().setList( component.getWebservices().getList());
        return client;
    }
}
