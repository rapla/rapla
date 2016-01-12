package org.rapla.client.event;

import com.google.web.bindery.event.shared.Event;
import org.rapla.client.ActivityManager.Place;
import org.rapla.client.event.PlaceChangedEvent.PlaceChangedEventHandler;

public class PlaceChangedEvent extends Event<PlaceChangedEventHandler>
{
    public interface PlaceChangedEventHandler
    {
        void placeChanged(PlaceChangedEvent event);
    }

    public static final Type<PlaceChangedEventHandler> TYPE = new Type<PlaceChangedEventHandler>();
    private Place newPlace;

    public PlaceChangedEvent(Place newPlace)
    {
        super();
        this.newPlace = newPlace;
    }

    public Place getNewPlace()
    {
        return newPlace;
    }

    @Override
    public Type<PlaceChangedEventHandler> getAssociatedType()
    {
        return TYPE;
    }

    @Override
    protected void dispatch(PlaceChangedEventHandler handler)
    {
        handler.placeChanged(this);
    }
}
