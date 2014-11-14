package org.rapla.plugin.urlencryption.server;

import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;
import org.rapla.server.RaplaServerExtensionPoints;
import org.rapla.server.ServerServiceContainer;

/**
 * This plugin provides a service to secure the publishing function of a calendar by encrypting the source parameters.
 * This class initializes the Option panel for the administrator, the UrlEncryptionService on the server and the 
 * Server Stub on the Client for using the encryption service. 
 * 
 * @author Jonas Kohlbrenner
 * 
 */
public class UrlEncryptionServerPlugin implements PluginDescriptor<ServerServiceContainer>
{
	public void provideServices(ServerServiceContainer container, Configuration configuration) throws RaplaContextException
	{
		container.addRemoteMethodFactory(UrlEncryption.class,UrlEncryptionService.class, configuration);
	    if(!configuration.getAttributeAsBoolean("enabled", UrlEncryptionPlugin.ENABLE_BY_DEFAULT))
		{
			return;
		}
	    container.addContainerProvidedComponent(UrlEncryption.class,UrlEncryptionService.class, configuration);

		// Adding Service implementation on Server
        container.addContainerProvidedComponent(
                RaplaServerExtensionPoints.SERVLET_REQUEST_RESPONSE_PREPROCESSING_POINT,
                UrlEncryptionServletRequestResponsePreprocessor.class
                );
	}
	
}
