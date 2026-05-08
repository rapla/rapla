package org.rapla.plugin.eventtimecalculator.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;

@Service(EventTimeCalculatorPlugin.PLUGIN_ID)
@Scope("prototype")

public class EventTimeCalculatorAdminOption extends EventTimeCalculatorUserOption implements  PluginOptionPanel
{
    @Autowired
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

