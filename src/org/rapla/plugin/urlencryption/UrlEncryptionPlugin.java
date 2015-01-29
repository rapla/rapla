package org.rapla.plugin.urlencryption;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.TypedComponentRole;

/**
 * This plugin provides a service to secure the publishing function of a calendar by encrypting the source parameters.
 * This class initializes the Option panel for the administrator, the UrlEncryptionService on the server and the 
 * Server Stub on the Client for using the encryption service. 
 * 
 * @author Jonas Kohlbrenner
 * 
 */
public class UrlEncryptionPlugin implements PluginDescriptor<ClientServiceContainer>
{
	static final TypedComponentRole<I18nBundle> RESOURCE_FILE = new TypedComponentRole<I18nBundle>(UrlEncryptionPlugin.class.getPackage().getName() + ".UrlEncryptionResource");
    static final String PLUGIN_ENTRY = "org.rapla.plugin.urlencryption";
	public static final String URL_ENCRYPTION = PLUGIN_ENTRY+".selected";
    public static final String PLUGIN_CLASS = UrlEncryptionPlugin.class.getName();
    public static final boolean ENABLE_BY_DEFAULT = false;

	public void provideServices(ClientServiceContainer container, Configuration configuration) throws RaplaContextException
	{
		container.addContainerProvidedComponent(RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION, UrlEncryptionOption.class);
	    
		if(!configuration.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT))
		{
			return;
		}

		// Adding option panel for the administrators
	    container.addContainerProvidedComponent( RaplaClientExtensionPoints.PUBLISH_EXTENSION_OPTION, URLEncyrptionPublicExtensionFactory.class, configuration);
        container.addResourceFile( RESOURCE_FILE );

	}
	
}
