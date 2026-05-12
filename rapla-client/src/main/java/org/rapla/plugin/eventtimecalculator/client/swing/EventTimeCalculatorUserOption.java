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
import org.rapla.logger.Logger;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorConfigService;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.Locale;

/**
 * ****************************************************************************
 * This is the admin-option panel.
 *
 * @author Tobias Bertram
 */
@Service
@Scope("prototype")

public class EventTimeCalculatorUserOption extends RaplaGUIComponent implements UserOptionPanel
{
   
    EventTimeCalculatorOption optionPanel;
	private Preferences preferences;
	Configuration config;
	JPanel panel;

    EventTimeCalculatorResources eventTimei18n;
    private final EventTimeCalculatorConfigService configService;

    @Autowired
	public EventTimeCalculatorUserOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, EventTimeCalculatorResources eventTimei18n,
                                         EventTimeCalculatorConfigService configService) throws RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger);
        this.configService = configService;
        // System default fetched via dedicated REST endpoint (was: bulk preference
        // bootstrap via getSystemPreferences().getEntry(SYSTEM_CONFIG)).
        try
        {
            Configuration restConfig = configService.getSystemConfig();
            this.config = restConfig != null ? restConfig : new RaplaConfiguration();
        }
        catch (Exception e)
        {
            // Fall back to the local cache if the REST call fails — lets the
            // dialog still open with sensible defaults.
            logger.warn("GET /eventtimecalculator/system-config failed, falling back to local cache: " + e.getMessage());
            try
            {
                this.config = facade.getRaplaFacade().getSystemPreferences().getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG, new RaplaConfiguration());
            }
            catch (RaplaException re)
            {
                throw new RaplaInitializationException(re);
            }
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
        // Read the per-user override via /eventtimecalculator/user-config
        // instead of the bulk /storage/resources preference cache. Save path
        // is unchanged: commit() writes to the editable prefs clone and the
        // dialog framework saves via facade.store -> /storage/dispatch.
        Configuration userConfig;
        try
        {
            userConfig = configService.getUserConfig();
        }
        catch (Exception e)
        {
            getLogger().warn("GET /eventtimecalculator/user-config failed, falling back to local cache: " + e.getMessage());
            userConfig = preferences.getEntry(getConfigEntry());
        }
        Configuration effective = userConfig != null ? userConfig : this.config;
        optionPanel.readConfig(effective);
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

