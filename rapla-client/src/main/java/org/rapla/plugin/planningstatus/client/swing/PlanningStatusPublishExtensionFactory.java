package org.rapla.plugin.planningstatus.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.planningstatus.PlanningStatusPlugin;
import org.rapla.plugin.planningstatus.PlanningStatusResources;
import org.rapla.rest.PluginsService;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import java.beans.PropertyChangeListener;

@Service

public class PlanningStatusPublishExtensionFactory implements PublishExtensionFactory
{
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final PlanningStatusResources i18nPlanninsgStatus;
    private final RaplaLocale raplaLocale;
    private final PluginsService plugins;
    private volatile Boolean cachedEnabled;

    @Autowired
	public PlanningStatusPublishExtensionFactory(ClientFacade facade, RaplaResources i18n, PlanningStatusResources i18nPlanninsgStatus,RaplaLocale raplaLocale, PluginsService plugins)
	{
        this.facade = facade;
        this.i18n = i18n;
        this.i18nPlanninsgStatus = i18nPlanninsgStatus;
        this.raplaLocale = raplaLocale;
        this.plugins = plugins;
	}

    @Override
    public boolean isEnabled()
    {
        Boolean c = cachedEnabled;
        if (c != null) return c;
        try
        {
            cachedEnabled = plugins.get("planningstatus").enabled();
            return cachedEnabled;
        }
        catch (Exception e)
        {
            return false;
        }
    }

	public PublishExtension creatExtension(CalendarSelectionModel model,
			PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new PlanningStatusPublishExtension(facade, i18n, raplaLocale, model, i18nPlanninsgStatus);
	}

	
}