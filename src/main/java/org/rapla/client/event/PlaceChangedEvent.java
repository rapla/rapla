package org.rapla.client.event;

import org.rapla.client.ActivityManager.Place;
import org.rapla.client.event.PlaceChangedEvent.PlaceChangedEventHandler;

import com.google.web.bindery.event.shared.Event;

public class PlaceChangedEvent extends Event<PlaceChangedEventHandler>
{
    public static interface PlaceChangedEventHandler
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
