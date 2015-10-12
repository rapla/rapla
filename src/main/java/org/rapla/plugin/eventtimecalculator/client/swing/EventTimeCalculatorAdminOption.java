package org.rapla.plugin.eventtimecalculator.client.swing;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.*;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.inject.Extension;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

import javax.inject.Inject;

@Extension(provides = PluginOptionPanel.class,id= EventTimeCalculatorPlugin.PLUGIN_ID)
public class EventTimeCalculatorAdminOption extends EventTimeCalculatorUserOption implements  PluginOptionPanel
{
    @Inject
    public EventTimeCalculatorAdminOption(RaplaContext context, EventTimeCalculatorResources i18n, ClientFacade facade) throws RaplaException
    {
        super(context, i18n, facade);
    }

    @Override protected TypedComponentRole<RaplaConfiguration> getConfigEntry()
    {
        return EventTimeCalculatorPlugin.SYSTEM_CONFIG;
    }
}

