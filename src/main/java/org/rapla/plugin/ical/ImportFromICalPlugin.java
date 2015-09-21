package org.rapla.plugin.ical;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.TypedComponentRole;


public class ImportFromICalPlugin implements PluginDescriptor<ClientServiceContainer>{


	public static final boolean ENABLE_BY_DEFAULT = false;

	//FIXME maybe this is no longer needed with signed applets
	boolean isApplet;
    public ImportFromICalPlugin(StartupEnvironment env)
    {
        isApplet = env.getStartupMode() == StartupEnvironment.APPLET;
    }

	public void provideServices(ClientServiceContainer container, Configuration config) throws RaplaContextException {
		if (!config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT))
			return;

	    if ( !isApplet )
        {
        	container.addContainerProvidedComponent(RaplaClientExtensionPoints.IMPORT_MENU_EXTENSION_POINT, ImportFromICalMenu.class);
        }
	}

}