package org.rapla.client.event;

import org.rapla.client.event.StopActivityEvent.StopActivityEventHandler;

import com.google.web.bindery.event.shared.Event;

public class StopActivityEvent extends Event<StopActivityEventHandler>
{
    public static interface StopActivityEventHandler
    {
        void stopActivity(StopActivityEvent event);
    }

    public static final Type<StopActivityEventHandler> TYPE = new Type<StopActivityEvent.StopActivityEventHandler>();

    private final String info;

    private final String id;

    public StopActivityEvent(String id, String info)
    {
        this.info = info;
        this.id = id;
    }

    public String getInfo()
    {
        return info;
    }

    public String getId()
    {
        return id;
    }

    @Override
    public Type<StopActivityEventHandler> getAssociatedType()
    {
        return TYPE;
    }

    @Override
    protected void dispatch(StopActivityEventHandler handler)
    {
        handler.stopActivity(this);
    }
}
