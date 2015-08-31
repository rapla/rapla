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
    
    private final String name;
    
    private final String id;
    
    public StopActivityEvent(String name, String id)
    {
        this.name = name;
        this.id = id;
    }
    
    public String getName()
    {
        return name;
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
