package org.rapla.client.event;

import org.rapla.client.event.StartActivityEvent.StartActivityEventHandler;

import com.google.web.bindery.event.shared.Event;

public class StartActivityEvent extends Event<StartActivityEventHandler>
{
    public static interface StartActivityEventHandler
    {
        void startActivity(StartActivityEvent event);
    }

    public static final Type<StartActivityEventHandler> TYPE = new Type<StartActivityEvent.StartActivityEventHandler>();
    
    private final String name;
    
    private final String id;
    
    public StartActivityEvent(String name, String id)
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
    public Type<StartActivityEventHandler> getAssociatedType()
    {
        return TYPE;
    }

    @Override
    protected void dispatch(StartActivityEventHandler handler)
    {
        handler.startActivity(this);
    }
}
