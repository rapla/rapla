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
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.planningstatus.PlanningStatusPlugin;
import org.rapla.plugin.planningstatus.PlanningStatusResources;

import javax.inject.Inject;
import java.beans.PropertyChangeListener;

@Extension(provides=PublishExtensionFactory.class,id="planningstatus")
public class PlanningStatusPublishExtensionFactory implements PublishExtensionFactory
{
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final PlanningStatusResources i18nPlanninsgStatus;
    private final RaplaLocale raplaLocale;
    private final Logger logger;
    private final IOInterface ioInterface;

    @Inject
	public PlanningStatusPublishExtensionFactory(ClientFacade facade, RaplaResources i18n, PlanningStatusResources i18nPlanninsgStatus,RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface)
	{
        this.facade = facade;
        this.i18n = i18n;
        this.i18nPlanninsgStatus = i18nPlanninsgStatus;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.ioInterface = ioInterface;
	}
    
    @Override
    public boolean isEnabled()
    {
        boolean enabled;
        try
        {
            enabled = facade.getRaplaFacade().getSystemPreferences().getEntryAsBoolean(PlanningStatusPlugin.ENABLED, PlanningStatusPlugin.ENABLE_BY_DEFAULT);
        }
        catch (RaplaException e)
        {
            return false;
        }
        return enabled;
    }

	public PublishExtension creatExtension(CalendarSelectionModel model,
			PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new PlanningStatusPublishExtension(facade, i18n, raplaLocale, logger, model, ioInterface, i18nPlanninsgStatus);
	}

	
}