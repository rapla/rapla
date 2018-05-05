package org.rapla.client.gwt;

import com.google.gwt.user.client.History;
import org.rapla.client.CalendarPlacePresenter;
import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@DefaultImplementation(of=AbstractActivityController.class, context = InjectionContext.gwt)
public class GwtActivityManagerImpl extends AbstractActivityController
{
    @Inject
    public GwtActivityManagerImpl(ApplicationEventBus eventBus, final Logger logger)
    {
        super( eventBus, logger);
        History.addValueChangeHandler(event -> {
            try
            {
                GwtActivityManagerImpl.this.init();
            }
            catch (RaplaException e)
            {
                logger.error("Error updating history change: " + e.getMessage(), e);
            }
        });
    }

    @Override
    protected void parsePlaceAndActivities() throws RaplaException
    {
        activities.add(new ApplicationEvent("cal", "Standard", null, null));
        if (true) {
            return;
        }
        // theory, this class is loaded on startup, so check the url and fire
        // events
        final String token = History.getToken();
        activities.clear();
        place = null;
        if (token != null && !token.isEmpty())
        {
            place = Place.fromString(token);
            int activitiesStartIndex = token.indexOf("?");
            final String activitiesString = activitiesStartIndex >= 0 ? token.substring(activitiesStartIndex + 1) : null;
            if (activitiesString != null)
            {
                String[] activitiesAsStringList = activitiesString.split("&");
                for (String activityListAsString : activitiesAsStringList)
                {
                    final String[] split = activityListAsString.split("=");
                    final String id = split[0];
                    final String[] activitiyIds = split[1].split(",");
                    for (String info : activitiyIds)
                    {
                        final ApplicationEvent activity = new ApplicationEvent(id, info, null, null);
                        activities.add(activity);
                    }
                }
            }
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
            Map<String, List<ApplicationEvent>> activitiesMap = new LinkedHashMap<>();
            sb.append("?");
            for (Iterator<ApplicationEvent> iterator = activities.iterator(); iterator.hasNext();)
            {
                final ApplicationEvent activity = iterator.next();
                List<ApplicationEvent> activitiesList = activitiesMap.get(activity.getApplicationEventId());
                if(activitiesList == null)
                {
                    activitiesList = new ArrayList<>();
                    activitiesMap.put(activity.getApplicationEventId(), activitiesList);
                }
                activitiesList.add(activity);
            }
            for (Entry<String, List<ApplicationEvent>> entries : activitiesMap.entrySet())
            {
                final String name = entries.getKey();
                sb.append(name);
                sb.append("=");
                final List<ApplicationEvent> activitiesList = entries.getValue();
                for (ApplicationEvent activity : activitiesList)
                {
                    sb.append(activity.getInfo());
                    sb.append(",");
                }
                // delete last ','
                sb.deleteCharAt(sb.length()-1);
                sb.append("&");
            }
            // delete last &
            sb.deleteCharAt(sb.length()-1);
        }
//        History.newItem(sb.toString(), false);
    }

    @Override
    protected boolean isPlace(ApplicationEvent activity)
    {
        final String applicationEventId = activity.getApplicationEventId();
        switch(applicationEventId)
        {
            case CalendarPlacePresenter.PLACE_ID:
                return true;
        }
        return false;
    }

}
