package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EventTimeCalculatorFactory
{

	private final ClientFacade facade;
	private final Logger logger;
	@Inject
    public EventTimeCalculatorFactory(ClientFacade facade, Logger logger)
	{
		this.facade = facade;
		this.logger = logger;
	}

    public  EventTimeModel getEventTimeModel ()
	{
		Configuration configuration = null;
		try
		{
			configuration = facade.getSystemPreferences().getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG, new RaplaConfiguration());
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
				raplaConfig = facade.getPreferences().getEntry(EventTimeCalculatorPlugin.USER_CONFIG);
				if ( raplaConfig != null)
	        	{
	        		configuration = raplaConfig;
	        	}
			} catch (RaplaException e) {
				logger.warn(e.getMessage());
			}
        
        }
        EventTimeModel m = new EventTimeModel(configuration);
        return m;
    }


}

