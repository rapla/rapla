package org.rapla.plugin.eventtimecalculator.client.swing;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

@Extension(provides = PluginOptionPanel.class,id= EventTimeCalculatorPlugin.PLUGIN_ID)
public class EventTimeCalculatorAdminOption extends EventTimeCalculatorUserOption implements  PluginOptionPanel
{
    @Inject
    public EventTimeCalculatorAdminOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, EventTimeCalculatorResources eventTimei18n) throws
            RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger, eventTimei18n);
    }

    @Override protected TypedComponentRole<RaplaConfiguration> getConfigEntry()
    {
        return EventTimeCalculatorPlugin.SYSTEM_CONFIG;
    }
}

