package org.rapla.client.event;

import com.google.web.bindery.event.shared.Event;

public class CalendarRefreshEvent extends Event<CalendarRefreshEvent.CalendarRefreshEventHandler>
{
    public interface CalendarRefreshEventHandler
    {
        void handle(CalendarRefreshEvent event);
    }

    public static final Type<CalendarRefreshEventHandler> TYPE = new Type<CalendarRefreshEventHandler>();

    @Override public Type<CalendarRefreshEventHandler> getAssociatedType()
    {
        return TYPE;
    }

    @Override protected void dispatch(CalendarRefreshEventHandler handler)
    {
        handler.handle(this);
    }

}
