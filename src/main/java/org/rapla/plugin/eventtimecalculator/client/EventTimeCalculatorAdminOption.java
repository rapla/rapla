package org.rapla.plugin.eventtimecalculator.client;

import org.rapla.framework.*;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.inject.Extension;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.util.Locale;

@Extension(provides = PluginOptionPanel.class,id= EventTimeCalculatorPlugin.PLUGIN_ID)
public class EventTimeCalculatorAdminOption extends DefaultPluginOption
{
    EventTimeCalculatorOption optionPanel;
    EventTimeCalculatorResources i18n;

    @Inject
    public EventTimeCalculatorAdminOption(RaplaContext sm,EventTimeCalculatorResources i18n) throws RaplaContextException
    {
        super(sm);
        this.i18n = i18n;
        optionPanel = new EventTimeCalculatorOption(sm, true, i18n);
    }

    /**
     * creates the panel shown in the admin option dialog.
     */
    protected JPanel createPanel() throws RaplaException {
        JPanel panel = super.createPanel();
        JPanel content = optionPanel.createPanel();
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    /**
     * adds new configuration to the children to overwrite the default configuration.
     */
    protected void addChildren(DefaultConfiguration newConfig) {
        optionPanel.addChildren( newConfig);
    }

    /**
     * reads children out of the configuration and shows them in the admin option panel.
     */
    protected void readConfig(Configuration config) {
    	optionPanel.readConfig(config);
    }

    /**
     * returns a string with the name of the plugin.
     */
    public String getName(Locale locale) {
        return i18n.getString("EventTimeCalculatorPlugin");
    }

}

