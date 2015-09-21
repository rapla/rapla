package org.rapla.plugin.eventtimecalculator.client;

import java.awt.BorderLayout;
import java.util.Locale;

import javax.swing.JPanel;

import org.rapla.framework.*;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

/**
 * ****************************************************************************
 * This is the admin-option panel.
 *
 * @author Tobias Bertram
 */
public class EventTimeCalculatorAdminOption extends DefaultPluginOption {
    EventTimeCalculatorOption optionPanel;
    EventTimeCalculatorResources i18n;

    public EventTimeCalculatorAdminOption(RaplaContext sm,EventTimeCalculatorResources i18n) throws RaplaContextException
    {
        super(sm);
        this.i18n = i18n;
        optionPanel = new EventTimeCalculatorOption(sm, true);
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
     * returns a string with the name of the class EventTimeCalculatorPlugin.
     */
    public Class<? extends PluginDescriptor<?>> getPluginClass() {
        return EventTimeCalculatorPlugin.class;
    }

    /**
     * returns a string with the name of the plugin.
     */
    public String getName(Locale locale) {
        return i18n.getString("EventTimeCalculatorPlugin");
    }

}

