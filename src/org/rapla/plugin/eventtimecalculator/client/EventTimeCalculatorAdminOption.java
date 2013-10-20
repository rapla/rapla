package org.rapla.plugin.eventtimecalculator.client;

import java.awt.BorderLayout;
import java.util.Locale;

import javax.swing.JPanel;

import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;

/**
 * ****************************************************************************
 * This is the admin-option panel.
 *
 * @author Tobias Bertram
 */
public class EventTimeCalculatorAdminOption extends DefaultPluginOption {
    EventTimeCalculatorOption optionPanel;

    public EventTimeCalculatorAdminOption(RaplaContext sm) {
        super(sm);
        setChildBundleName(EventTimeCalculatorPlugin.RESOURCE_FILE);
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
        return getString("EventTimeCalculatorPlugin");
    }

}

