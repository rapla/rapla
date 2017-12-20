package org.rapla.client.event;


public class OwnReservationsEvent
{
    public interface OwnReservationsEventHandler
    {
        void handle(OwnReservationsEvent event);
    }


    protected void dispatch(OwnReservationsEventHandler handler)
    {
        handler.handle(this);
    }

}
