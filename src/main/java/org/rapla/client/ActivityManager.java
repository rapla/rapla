package org.rapla.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;

import org.rapla.client.event.PlaceChangedEvent;
import org.rapla.client.event.PlaceChangedEvent.PlaceChangedEventHandler;
import org.rapla.client.event.StartActivityEvent;
import org.rapla.client.event.StartActivityEvent.StartActivityEventHandler;
import org.rapla.client.event.StopActivityEvent;
import org.rapla.client.event.StopActivityEvent.StopActivityEventHandler;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import com.google.web.bindery.event.shared.EventBus;

public abstract class ActivityManager implements PlaceChangedEventHandler, StartActivityEventHandler, StopActivityEventHandler
{

    private final Application<?> application;
    protected Place place;
    protected final Set<Activity> activities = new LinkedHashSet<Activity>();
    protected final Logger logger;

    @Inject
    public ActivityManager(@SuppressWarnings("rawtypes") Application application, EventBus eventBus, Logger logger)
    {
        this.application = application;
        this.logger = logger;
        eventBus.addHandler(PlaceChangedEvent.TYPE, this);
        eventBus.addHandler(StartActivityEvent.TYPE, this);
        eventBus.addHandler(StopActivityEvent.TYPE, this);
    }

    @Override
    public void startActivity(StartActivityEvent event)
    {
        Activity activity = new Activity(event.getId(), event.getInfo());
        activities.add(activity);
        updateHistroryEntry();
        application.startActivity(activity);
    }

    @Override
    public void stopActivity(StopActivityEvent event)
    {
        Activity activity = new Activity(event.getId(), event.getInfo());
        activities.remove(activity);
        updateHistroryEntry();
    }

    @Override
    public void placeChanged(PlaceChangedEvent event)
    {
        place = event.getNewPlace();
        updateHistroryEntry();
        application.selectPlace(place);
    }

    public final void init() throws RaplaException
    {
        parsePlaceAndActivities();
        application.selectPlace(place);
        if (!activities.isEmpty())
        {
            ArrayList<Activity> toRemove = new ArrayList<Activity>();
            for (Activity activity : activities)
            {
                if (!application.startActivity(activity))
                {
                    toRemove.add(activity);
                }
            }
            if (!toRemove.isEmpty())
            {
                activities.removeAll(toRemove);
                updateHistroryEntry();
            }
        }
    }

    protected abstract void parsePlaceAndActivities() throws RaplaException;

    protected abstract void updateHistroryEntry();

    private static final String ACTIVITY_SEPARATOR = "=";

    public static class Activity
    {
        private final String info;
        private final String id;

        public Activity(String id, String info)
        {
            this.id = id;
            this.info = info;
        }

        public String getId()
        {
            return id;
        }

        public String getInfo()
        {
            return info;
        }

        @Override
        public String toString()
        {
            return id + "=" + info;
        }

        public static Activity fromString(final String activityString)
        {
            if (activityString == null)
            {
                return null;
            }
            int indexOf = activityString.indexOf(ACTIVITY_SEPARATOR);
            if (indexOf > 0)
            {
                String id = activityString.substring(0, indexOf);
                String info = activityString.substring(indexOf + 1);
                return new Activity(id, info);
            }
            return null;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((info == null) ? 0 : info.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Activity other = (Activity) obj;
            if (id == null)
            {
                if (other.id != null)
                    return false;
            }
            else if (!id.equals(other.id))
                return false;
            if (info == null)
            {
                if (other.info != null)
                    return false;
            }
            else if (!info.equals(other.info))
                return false;
            return true;
        }

    }

    private static final String PLACE_SEPARATOR = "/";

    public static class Place
    {
        private final String id;
        private final String info;

        public Place(String id, String info)
        {
            this.id = id;
            this.info = info;
        }

        public String getId()
        {
            return id;
        }

        public String getInfo()
        {
            return info;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(id);
            if (info != null)
            {
                sb.append(PLACE_SEPARATOR);
                sb.append(info);
            }
            return sb.toString();
        }

        public static Place fromString(String token)
        {
            if (token == null || token.isEmpty())
            {
                return null;
            }
            String substring = token;
            int paramsIndex = substring.indexOf("?");
            if (paramsIndex == 0)
            {
                return null;
            }
            if (paramsIndex > 0)
            {
                substring = substring.substring(0, paramsIndex);
            }
            int separator = substring.indexOf(PLACE_SEPARATOR);
            final String info;
            final String id;
            if (separator >= 0)
            {
                id = substring.substring(0, separator);
                info = substring.substring(separator + 1);
            }
            else
            {
                id = substring;
                info = null;
            }
            return new Place(id, info);
        }
    }

}
