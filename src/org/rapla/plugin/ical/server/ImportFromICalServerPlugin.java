package org.rapla.plugin.ical.server;

import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.ical.ICalImport;
import org.rapla.plugin.ical.ImportFromICalPlugin;
import org.rapla.server.ServerServiceContainer;


public class ImportFromICalServerPlugin implements PluginDescriptor<ServerServiceContainer> {

	public void provideServices(ServerServiceContainer container, Configuration config) throws RaplaContextException {
		if (!config.getAttributeAsBoolean("enabled", Export2iCalPlugin.ENABLE_BY_DEFAULT))
			return;

		container.addResourceFile(ImportFromICalPlugin.RESOURCE_FILE);
	    container.addRemoteMethodFactory(ICalImport.class, RaplaICalImport.class, config);
		
	}

}