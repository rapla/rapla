package org.rapla.client.event;

import org.rapla.client.PopupContext;

import com.google.web.bindery.event.shared.Event;

public class ApplicationEvent extends Event<ApplicationEvent.ApplicationEventHandler>
{
    private final String info;
    private final ApplicationEventContext context;
    private final String applicationEventId;
    private final PopupContext popupContext;
    private static final String ACTIVITY_SEPARATOR = "=";
    private boolean stop = false;
    
    public interface ApplicationEventContext
    {
    }

    public interface ApplicationEventHandler
    {
        void handle(ApplicationEvent event);
    }

    public static final Type<ApplicationEventHandler> TYPE = new Type<ApplicationEventHandler>();

    public ApplicationEvent(String applictionEventId, String info, PopupContext popupContext, ApplicationEventContext context)
    {
        this.applicationEventId = applictionEventId;
        this.info = info;
        this.popupContext = popupContext;
        this.context = context;
    }
    
    public ApplicationEventContext getContext()
    {
        return context;
    }

    public void setStop(boolean stop)
    {
        this.stop = stop;
    }

    public boolean isStop()
    {
        return stop;
    }

    public String getApplicationEventId()
    {
        return applicationEventId;
    }

    public String getInfo()
    {
        return info;
    }

    @Override public Type<ApplicationEventHandler> getAssociatedType()
    {
        return TYPE;
    }

    @Override public String toString()
    {
        return applicationEventId + "=" + info;
    }

    @Override protected void dispatch(ApplicationEventHandler handler)
    {
        handler.handle(this);
    }

    public static ApplicationEvent fromString(final String activityString)
    {
        if (activityString == null)
        {
            return null;
        }
        int indexOf = activityString.indexOf(ACTIVITY_SEPARATOR);
        if (indexOf > 0)
        {
            String id = activityString.substring(0, indexOf);
            String info = activityString.substring(indexOf + 1);
            return new ApplicationEvent(id, info, null, null);
        }
        return null;
    }

    @Override public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((applicationEventId == null) ? 0 : applicationEventId.hashCode());
        result = prime * result + ((info == null) ? 0 : info.hashCode());
        return result;
    }

    @Override public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ApplicationEvent other = (ApplicationEvent) obj;
        if (applicationEventId == null)
        {
            if (other.applicationEventId != null)
                return false;
        }
        else if (!applicationEventId.equals(other.applicationEventId))
            return false;
        if (info == null)
        {
            if (other.info != null)
                return false;
        }
        else if (!info.equals(other.info))
            return false;
        return true;
    }

    public PopupContext getPopupContext()
    {
        return popupContext;
    }
}
