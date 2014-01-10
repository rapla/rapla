package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class EventTimeCalculatorFactory extends RaplaComponent  
{
    boolean isUserPrefAllowed;
    Configuration config;

    public EventTimeCalculatorFactory(RaplaContext context,Configuration config)
	{
    	super( context);
		isUserPrefAllowed = config.getChild(EventTimeCalculatorPlugin.USER_PREFS).getValueAsBoolean(EventTimeCalculatorPlugin.DEFAULT_userPrefs);
		this.config = config;
	}

    public  EventTimeModel getEventTimeModel () {
       
        Configuration configuration = config;
        if ( isUserPrefAllowed)
        {
        	RaplaConfiguration raplaConfig;
			try {
				raplaConfig = getQuery().getPreferences().getEntry(EventTimeCalculatorPlugin.USER_CONFIG);
				if ( raplaConfig != null)
	        	{
	        		configuration = raplaConfig;
	        	}
			} catch (RaplaException e) {
				getLogger().warn(e.getMessage());
			}
        
        }
        EventTimeModel m = new EventTimeModel(configuration);
        return m;
    }


}

