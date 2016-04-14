package org.rapla.client.event;

import com.google.web.bindery.event.shared.Event;

public class OwnReservationsEvent extends Event<OwnReservationsEvent.OwnReservationsEventHandler>
{
    public interface OwnReservationsEventHandler
    {
        void handle(OwnReservationsEvent event);
    }

    public static final Type<OwnReservationsEventHandler> TYPE = new Type<OwnReservationsEventHandler>();

    @Override public Type<OwnReservationsEventHandler> getAssociatedType()
    {
        return TYPE;
    }

    @Override protected void dispatch(OwnReservationsEventHandler handler)
    {
        handler.handle(this);
    }

}
