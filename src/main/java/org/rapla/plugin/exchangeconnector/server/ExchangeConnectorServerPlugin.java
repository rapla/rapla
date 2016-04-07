package org.rapla.plugin.exchangeconnector.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.rapla.framework.TypedComponentRole;


public class ExchangeConnectorServerPlugin {//implements PluginDescriptor<ServerServiceContainer> {

    public static List<String> TIMEZONES = new ArrayList<String>();
    static
    {
        InputStream in = null;
        try
        {
            in = ExchangeConnectorServerPlugin.class.getResourceAsStream("timezones.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            while (true)
            {
                String line =reader.readLine(); 
                TIMEZONES.add( line);
                if ( line == null)
                {
                    break;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
        finally
        {
            if ( in != null)
            {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    
    public static final TypedComponentRole<String> EXCHANGE_USER_STORAGE = new TypedComponentRole<String>("org.rapla.server.exchangeuser");

    /* (non-Javadoc)
      * @see org.rapla.framework.PluginDescriptor#provideServices(org.rapla.framework.Container, org.apache.avalon.framework.configuration.Configuration)
      */
//    public void provideServices(ServerServiceContainer container, Configuration config) throws RaplaContextException {
//        convertSettings(container.getContext(), config);
//        container.addResourceFile(ExchangeConnectorConfig.RESOURCE_FILE);
//        container.addRemoteMethodFactory(ExchangeConnectorConfigRemote.class, ExchangeConnectorRemoteConfigFactory.class);
//        container.addContainerProvidedComponent(ExchangeConnectorConfig.class, ConfigReader.class);
//        
//        if (!config.getAttributeAsBoolean("enabled", ExchangeConnectorPlugin.ENABLE_BY_DEFAULT)) {
//        	return;
//        }
//        container.addContainerProvidedComponent(ExchangeAppointmentStorage.class, ExchangeAppointmentStorage.class);
//        container.addContainerProvidedComponent(SynchronisationManager.class, SynchronisationManager.class);
//        container.addContainerProvidedComponent(RaplaServerExtensionPoints.SERVER_EXTENSION, SynchronisationManagerInitializer.class);
//        container.addRemoteMethodFactory(ExchangeConnectorRemote.class, ExchangeConnectorRemoteObjectFactory.class);
//
//    }
//    
//    @SuppressWarnings("restriction")
//    private void convertSettings(RaplaContext context,Configuration config) throws RaplaContextException
//    {
//        String className = ExchangeConnectorPlugin.class.getName();
//        TypedComponentRole<RaplaConfiguration> newConfKey = ExchangeConnectorConfig.EXCHANGESERVER_CONFIG;
//        if ( config.getChildren().length > 0)
//        {
//            org.rapla.server.internal.RemoteStorageImpl.convertToNewPluginConfig(context, className, newConfKey);
//        }
//    }



}
