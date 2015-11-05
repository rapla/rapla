package org.rapla.client.swing.internal.dagger;

import java.net.URL;

import org.rapla.client.internal.RaplaClientServiceImpl;
import org.rapla.framework.StartupEnvironment;
import org.rapla.jsonrpc.client.EntryPointFactory;
import org.rapla.jsonrpc.client.swing.BasicRaplaHTTPConnector;

public class DaggerClientCreator
{
    public static RaplaClientServiceImpl create(StartupEnvironment startupEnvironment)
    {
        ClientComponent component= org.rapla.client.swing.internal.dagger.DaggerClientComponent.builder().myClientModule(new MyClientModule(startupEnvironment)).build();
        final RaplaClientServiceImpl client = component.getClient();
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
