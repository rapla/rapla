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

    private final String info;

    private final String id;

    public StartActivityEvent(String id, String info)
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
