package org.rapla.client.gwt;

import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.ActivityManager;
import org.rapla.client.Application;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.web.bindery.event.shared.EventBus;

@Singleton
public class GwtActivityManagerImpl extends ActivityManager
{
    private boolean changeByBrowser = true;

    @Inject
    public GwtActivityManagerImpl(Application application, EventBus eventBus, Logger logger)
    {
        super(application, eventBus, logger);
        History.addValueChangeHandler(new ValueChangeHandler<String>()
        {
            @Override
            public void onValueChange(ValueChangeEvent<String> event)
            {
                if (changeByBrowser)
                {
                    try
                    {
                        GwtActivityManagerImpl.this.init();
                    }
                    catch (RaplaException e)
                    {
                        logger.error("Error updating history change: " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    @Override
    protected void parsePlaceAndActivities() throws RaplaException
    {
        // theory, this class is loaded on startup, so check the url and fire
        // events
        final String token = History.getToken();
        if (token != null && !token.isEmpty())
        {
            place = Place.fromString(token);
            int activitiesStartIndex = token.indexOf("?");
            final String activitiesString = activitiesStartIndex >= 0 ? token.substring(activitiesStartIndex + 1) : null;
            if (activitiesString != null)
            {
                String[] activitiesAsString = activitiesString.split("&");
                for (String activityAsString : activitiesAsString)
                {
                    Activity activity = Activity.fromString(activityAsString);
                    if (activity != null)
                    {
                        activities.add(activity);
                    }
                }
            }
            updateHistroryEntry();
        }
        else
        {
            place = null;
            activities.clear();
        }
    }

    protected void updateHistroryEntry()
    {
        final StringBuilder sb = new StringBuilder();
        if (place != null)
        {
            sb.append(place.toString());
        }
        if (!activities.isEmpty())
        {
            sb.append("?");
            for (Iterator<Activity> iterator = activities.iterator(); iterator.hasNext();)
            {
                final Activity activity = iterator.next();
                sb.append(activity.toString());
                if (iterator.hasNext())
                {
                    sb.append("&");
                }
            }
        }
        try
        {
            changeByBrowser = false;
            History.newItem(sb.toString());
        }
        finally
        {
            changeByBrowser = true;
        }
    }

}
