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
       
        EventTimeModel m = new EventTimeModel();
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
        m.timeTillBreak = configuration.getChild(EventTimeCalculatorPlugin.INTERVAL_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_intervalNumber);
        m.durationOfBreak= configuration.getChild(EventTimeCalculatorPlugin.BREAK_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_breakNumber);
        m.timeUnit= configuration.getChild(EventTimeCalculatorPlugin.TIME_UNIT).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_timeUnit);
        m.timeFormat= configuration.getChild(EventTimeCalculatorPlugin.TIME_FORMAT).getValue(EventTimeCalculatorPlugin.DEFAULT_timeFormat);
        return m;
    }


}

