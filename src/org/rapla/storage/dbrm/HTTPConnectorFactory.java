package org.rapla.storage.dbrm;

import java.net.MalformedURLException;
import java.net.URL;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContextTools;

public class HTTPConnectorFactory implements ConnectorFactory
{

	RaplaContext context;
	I18nBundle i18n;
	
	public HTTPConnectorFactory(RaplaContext context) throws RaplaException {
		this.context = context;
		this.i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);
	}
	
	public Connector create( Configuration config) throws RaplaException {
		try
        {
            String configEntry = config.getChild("server").getValue();
            String serverURL = ContextTools.resolveContext(configEntry, context );
            URL server = new URL(serverURL);
            return new HTTPConnector(i18n, server);
        }
        catch (MalformedURLException e)
        {
            throw new RaplaException("Malformed url. Could not parse " + e.getMessage());
        }
        catch (ConfigurationException e)
        {
            throw new RaplaException(e);
        }
	}


    
}
