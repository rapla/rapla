package org.rapla.plugin.eventtimecalculator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

@Singleton
public class EventTimeCalculatorFactory
{

	private final Provider<RaplaFacade> facadeProvider;
	private final Logger logger;
    private final EventTimeCalculatorResources eventTimeI18n;
	@Inject
    public EventTimeCalculatorFactory(Provider<RaplaFacade> facade,Logger logger, final EventTimeCalculatorResources eventTimeI18n)
	{
		this.facadeProvider = facade;
		this.logger = logger;
        this.eventTimeI18n = eventTimeI18n;
	}

    public  EventTimeModel getEventTimeModel ()
	{
		Configuration configuration = null;
		final RaplaFacade facade = facadeProvider.get();
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
        EventTimeModel m = new EventTimeModel(configuration, eventTimeI18n);
        return m;
    }


}

