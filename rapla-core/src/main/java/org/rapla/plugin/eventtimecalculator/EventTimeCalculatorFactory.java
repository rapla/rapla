package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.User;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Supplier;

public class EventTimeCalculatorFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EventTimeCalculatorFactory.class);

	private final Supplier<RaplaFacade> facadeProvider;
    private final EventTimeCalculatorResources eventTimeI18n;

	@Autowired
    public EventTimeCalculatorFactory(Supplier<RaplaFacade> facade, final EventTimeCalculatorResources eventTimeI18n)
	{
		this.facadeProvider = facade;
        this.eventTimeI18n = eventTimeI18n;
	}

    public  EventTimeModel getEventTimeModel (User user)
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
        if ( isUserPrefAllowed && user != null)
        {
        	RaplaConfiguration raplaConfig;
			try {
				raplaConfig = facade.getPreferences(user).getEntry(EventTimeCalculatorPlugin.USER_CONFIG);
				if ( raplaConfig != null)
	        	{
	        		configuration = raplaConfig;
	        	}
			} catch (RaplaException e) {
				LOGGER.warn(e.getMessage());
			}

        }
        EventTimeModel m = new EventTimeModel(configuration, eventTimeI18n);
        return m;
    }


}
