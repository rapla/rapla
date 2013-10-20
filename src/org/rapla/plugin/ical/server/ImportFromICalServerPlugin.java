package org.rapla.plugin.ical.server;

import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
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

		container.addContainerProvidedComponent(ImportFromICalPlugin.RESOURCE_FILE, I18nBundleImpl.class, I18nBundleImpl.createConfig(Export2iCalPlugin.RESOURCE_FILE.getId()));
	    container.addRemoteMethodFactory(ICalImport.class, RaplaICalImport.class, config);
		
	}

}