package org.rapla.client;

import javax.inject.Inject;

import org.rapla.client.event.DetailEndEvent;
import org.rapla.client.event.DetailEndEvent.DetailEndEventHandler;
import org.rapla.client.event.DetailSelectEvent;
import org.rapla.client.event.PlaceChangedEvent;
import org.rapla.client.event.DetailSelectEvent.DetailSelectEventHandler;
import org.rapla.client.event.PlaceChangedEvent.PlaceChangedEventHandler;
import org.rapla.framework.RaplaException;

import com.google.web.bindery.event.shared.EventBus;

public abstract class ActivityManager implements DetailSelectEventHandler, DetailEndEventHandler, PlaceChangedEventHandler
{

    private final Application application;
    protected Place place;

    @Inject
    public ActivityManager(Application application, EventBus eventBus)
    {
        this.application = application;
        eventBus.addHandler(DetailSelectEvent.TYPE, this);
        eventBus.addHandler(DetailEndEvent.TYPE, this);
        eventBus.addHandler(PlaceChangedEvent.TYPE, this);
    }

    @Override
    public void detailsRequested(DetailSelectEvent event)
    {
        createActivityOrPlace(event);
        application.detailsRequested(event);
    }

    @Override
    public void placeChanged(PlaceChangedEvent event)
    {
        place = event.getNewPlace();
        updateHistroryEntry();
    }

    public Place getPlace()
    {
        return place;
    }

    public abstract void init() throws RaplaException;

    protected abstract void createActivityOrPlace(DetailSelectEvent event);

    protected abstract void updateHistroryEntry();

    private static final String PLACE_SEPARATOR = "/";

    public static class Place
    {
        private final String name;
        private final String id;

        public Place(String name, String id)
        {
            this.name = name;
            this.id = id;
        }

        public String getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        @Override
        public String toString()
        {
            return new StringBuilder().append(name).append(PLACE_SEPARATOR).append(id).toString();
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
            final String name;
            final String id;
            if (separator >= 0)
            {
                name = substring.substring(0, separator);
                id = substring.substring(separator + 1);
            }
            else
            {
                name = substring;
                id = null;
            }
            return new Place(name, id);
        }
    }

}
