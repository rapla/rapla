package org.rapla.plugin.eventtimecalculator.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

import javax.inject.Inject;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.Locale;

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
	public EventTimeCalculatorUserOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, EventTimeCalculatorResources eventTimei18n) throws
            RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger);
        try
        {
            this.config = facade.getRaplaFacade().getSystemPreferences().getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG, new RaplaConfiguration());
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        this.eventTimei18n = eventTimei18n;
        optionPanel = new EventTimeCalculatorOption(facade, i18n, raplaLocale, logger, false, eventTimei18n);
        panel = optionPanel.createPanel();
    }
    
    @Override
    public boolean isEnabled()
    {
        return config.getAttributeAsBoolean("enabled", EventTimeCalculatorPlugin.ENABLE_BY_DEFAULT);
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

