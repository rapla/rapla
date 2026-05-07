package org.rapla.client.event;

import org.rapla.client.Application;
import org.rapla.client.RaplaWidget;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractActivityController
{
    protected Place place;
    protected final Set<ApplicationEvent> activities = new LinkedHashSet<>();
    protected final Logger logger;
    private RaplaWidget activePlace;

    public Application getApplication()
    {
        return application;
    }

    public void setApplication(Application application)
    {
        this.application = application;
    }

    protected Application application;

    public AbstractActivityController(ApplicationEventBus eventBus, Logger logger)
    {
        this.logger = logger;
        eventBus.getApplicationEventObservable().doOnError(ex-> {
            logger.error(ex.getMessage(),ex);
        }).subscribe(this::handle);
    }

    public void handle(ApplicationEvent activity)
    {
        try
        {
            if ( activity.isStop())
            {
                activities.remove(activity);
                updateHistroryEntry();
                application.stopAction(activity);
            }
            else
            {
                if(startActivity(activity))
                {
                    activities.add(activity);
                    updateHistroryEntry();
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(ex.getMessage(), ex);
        }

    }

    abstract  protected boolean isPlace( ApplicationEvent activity);

    private boolean startActivity(ApplicationEvent activity)
    {
        if ( activity == null)
        {
            return false;
        }
        boolean isPlace = isPlace( activity);
        return application.startAction( activity, isPlace);
    }

    public final void init() throws RaplaException
    {
        parsePlaceAndActivities();
        if (!activities.isEmpty())
        {
            ArrayList<ApplicationEvent> toRemove = new ArrayList<>();
            for (ApplicationEvent activity : activities)
            {
                if (!startActivity(activity))
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


    public <T> RaplaWidget<T> provideContent()
    {
        return  activePlace;
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
