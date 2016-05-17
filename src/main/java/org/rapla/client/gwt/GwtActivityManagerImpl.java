package org.rapla.client.gwt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.web.bindery.event.shared.EventBus;

@DefaultImplementation(of=AbstractActivityController.class, context = InjectionContext.gwt)
public class GwtActivityManagerImpl extends AbstractActivityController
{
    @Inject
    public GwtActivityManagerImpl( EventBus eventBus, final Logger logger)
    {
        super( eventBus, logger);
        History.addValueChangeHandler(new ValueChangeHandler<String>()
        {
            @Override
            public void onValueChange(ValueChangeEvent<String> event)
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
        });
    }

    @Override
    protected void parsePlaceAndActivities() throws RaplaException
    {
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
            Map<String, List<ApplicationEvent>> activitiesMap = new LinkedHashMap<String, List<ApplicationEvent>>();
            sb.append("?");
            for (Iterator<ApplicationEvent> iterator = activities.iterator(); iterator.hasNext();)
            {
                final ApplicationEvent activity = iterator.next();
                List<ApplicationEvent> activitiesList = activitiesMap.get(activity.getApplicationEventId());
                if(activitiesList == null)
                {
                    activitiesList = new ArrayList<ApplicationEvent>();
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
        History.newItem(sb.toString(), false);
    }

    @Override
    protected boolean isPlace(ApplicationEvent activity)
    {
        return false;
    }

}
