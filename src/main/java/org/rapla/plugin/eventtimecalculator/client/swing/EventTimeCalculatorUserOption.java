package org.rapla.plugin.eventtimecalculator.client.swing;

import java.util.Locale;

import javax.inject.Inject;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.*;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.inject.Extension;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

/**
 * ****************************************************************************
 * This is the admin-option panel.
 *
 * @author Tobias Bertram
 */
@Extension(provides = UserOptionPanel.class, id=EventTimeCalculatorPlugin.PLUGIN_ID)
public class EventTimeCalculatorUserOption extends RaplaGUIComponent implements UserOptionPanel
{
   
    EventTimeCalculatorOption optionPanel;
	private Preferences preferences;
	Configuration config;
	JPanel panel;

    EventTimeCalculatorResources eventTimei18n;
    @Inject
	public EventTimeCalculatorUserOption(RaplaContext context, EventTimeCalculatorResources i18n, ClientFacade facade) throws RaplaException
    {
        super(context);
        this.config = facade.getSystemPreferences().getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG, new RaplaConfiguration());
        eventTimei18n = i18n;
        optionPanel = new EventTimeCalculatorOption(context, false, i18n);
        panel = optionPanel.createPanel();
    }

    @Override
    public JComponent getComponent() {
    	return panel;
    }

    public void show() throws RaplaException {
    	Configuration config = preferences.getEntry(getConfigEntry());
    	if  ( config == null)
    	{
    		config = this.config;
    	}
        optionPanel.readConfig( config);
    }

    protected TypedComponentRole<RaplaConfiguration> getConfigEntry()
    {
        return EventTimeCalculatorPlugin.USER_CONFIG;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }
    

    public void commit()  {
    	RaplaConfiguration config = new RaplaConfiguration("eventtime");
        optionPanel.addChildren(config);
        preferences.putEntry(getConfigEntry(), config);
    }


    /**
     * returns a string with the name of the plugin.
     */
    public String getName(Locale locale) {
        return eventTimei18n.getString("EventTimeCalculatorPlugin");
    }

}

