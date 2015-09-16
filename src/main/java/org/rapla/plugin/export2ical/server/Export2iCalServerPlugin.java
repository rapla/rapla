package org.rapla.plugin.export2ical.server;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.ICalConfigService;
import org.rapla.plugin.export2ical.ICalExport;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.UpdateDataManagerImpl;

public class Export2iCalServerPlugin implements PluginDescriptor<ServerServiceContainer> {

	public void provideServices(ServerServiceContainer container, Configuration config) throws RaplaContextException {
		container.addRemoteMethodFactory(ICalTimezones.class, RaplaICalTimezones.class, config);
		convertSettings(container.getContext(), config);
		container.addRemoteMethodFactory(ICalConfigService.class,ICalConfigServiceImpl.class);
	       
		if (!config.getAttributeAsBoolean("enabled", Export2iCalPlugin.ENABLE_BY_DEFAULT))
			return;

		container.addResourceFile(Export2iCalPlugin.RESOURCE_FILE);
		container.addRemoteMethodFactory(ICalExport.class, RaplaICalExport.class);
        container.addWebpage(Export2iCalPlugin.GENERATOR,Export2iCalServlet.class);
	}

	  private void convertSettings(RaplaContext context,Configuration config) throws RaplaContextException
	  {
	      String className = Export2iCalPlugin.class.getName();
	      TypedComponentRole<RaplaConfiguration> newConfKey = Export2iCalPlugin.ICAL_CONFIG;
	      if ( config.getChildren().length > 0)
	      {
	          UpdateDataManagerImpl.convertToNewPluginConfig(context, className, newConfKey);
	      }
	 }
}