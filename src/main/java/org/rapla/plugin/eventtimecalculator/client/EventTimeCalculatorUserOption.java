package org.rapla.plugin.eventtimecalculator.client;

import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

/**
 * ****************************************************************************
 * This is the admin-option panel.
 *
 * @author Tobias Bertram
 */
public class EventTimeCalculatorUserOption extends RaplaGUIComponent implements OptionPanel {
   
    EventTimeCalculatorOption optionPanel;
	private Preferences preferences;
	Configuration config;
	JPanel panel;

    EventTimeCalculatorResources eventTimei18n;
	public EventTimeCalculatorUserOption(RaplaContext context, Configuration config) throws RaplaContextException
    {
        super(context);
        this.config = config;
        eventTimei18n = context.lookup(EventTimeCalculatorResources.class);
        optionPanel = new EventTimeCalculatorOption(context, false);
        panel = optionPanel.createPanel();
    }

    @Override
    public JComponent getComponent() {
    	return panel;
    }

    public void show() throws RaplaException {
    	Configuration config = preferences.getEntry( EventTimeCalculatorPlugin.USER_CONFIG);
    	if  ( config == null)
    	{
    		config = this.config;
    	}
        optionPanel.readConfig( config);
    }


    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }
    

    public void commit()  {
    	RaplaConfiguration config = new RaplaConfiguration("eventtime");
        optionPanel.addChildren(config);
        preferences.putEntry( EventTimeCalculatorPlugin.USER_CONFIG, config);
    }


    /**
     * returns a string with the name of the plugin.
     */
    public String getName(Locale locale) {
        return eventTimei18n.getString("EventTimeCalculatorPlugin");
    }

}

