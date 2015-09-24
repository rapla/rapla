package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EventTimeCalculatorFactory extends RaplaComponent  
{

	@Inject
    public EventTimeCalculatorFactory(RaplaContext context)
	{
    	super( context);

	}

    public  EventTimeModel getEventTimeModel ()
	{
		Configuration configuration = null;
		try
		{
			configuration = getQuery().getSystemPreferences().getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG, new RaplaConfiguration());
		}
		catch (RaplaException e)
		{
			throw new IllegalStateException("Can't find prefences");
		}
		boolean isUserPrefAllowed = configuration.getChild(EventTimeCalculatorPlugin.USER_PREFS).getValueAsBoolean(EventTimeCalculatorPlugin.DEFAULT_userPrefs);
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

