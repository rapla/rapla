package org.rapla.plugin.exchangeconnector;

//implements PluginDescriptor<ClientServiceContainer> {
public interface ExchangeConnectorPlugin {

	public final static boolean ENABLE_BY_DEFAULT = false;
	
	public static final String PLUGIN_ID = "org.rapla.ExchangeConnector";

    public static final String EXCHANGE_EXPORT = PLUGIN_ID+".selected";
    
//    public void provideServices(ClientServiceContainer container, Configuration config) throws RaplaContextException {
//        container.addResourceFile(ExchangeConnectorConfig.RESOURCE_FILE);
//        container.addContainerProvidedComponent(RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION, ExchangeConnectorAdminOptions.class);
//        if (config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT)) {
//            container.addContainerProvidedComponent(RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION, ExchangeConnectorUserOptions.class);
//    	    container.addContainerProvidedComponent( RaplaClientExtensionPoints.PUBLISH_EXTENSION_OPTION, ExchangeExtensionFactory.class);
//            container.addContainerProvidedComponent( RaplaClientExtensionPoints.CLIENT_EXTENSION, ExchangeClientError.class);
//        }
//    }
    
}
