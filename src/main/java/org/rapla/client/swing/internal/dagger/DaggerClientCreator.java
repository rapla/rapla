package org.rapla.client.swing.internal.dagger;

import org.rapla.client.internal.RaplaClientServiceImpl;
import org.rapla.framework.StartupEnvironment;

public class DaggerClientCreator
{
    public static RaplaClientServiceImpl create(StartupEnvironment startupEnvironment)
    {
        ClientComponent component= org.rapla.client.swing.internal.dagger.DaggerClientComponent.builder().myModule(new MyClientModule(startupEnvironment)).build();
        final RaplaClientServiceImpl client = component.getClient();
        //server.getMethodProvider().setList( component.getWebservices().getList());
        return client;
    }
}
