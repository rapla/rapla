package org.rapla.client;

import java.util.Collections;
import java.util.List;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.entities.Entity;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EditController
{
    private final EventBus eventBus;

    @Inject
    public EditController(EventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    void newObject( Object contextObject, PopupContext popupContext )
    {

    }

    public <T extends Entity> void edit( T obj, PopupContext popupContext )
    {
        List<T> list = Collections.singletonList(obj);
        edit( list, popupContext);
    }
//  neue Methoden zur Bearbeitung von mehreren gleichartigen Elementen (Entities-Array)
//  orientieren sich an den oberen beiden Methoden zur Bearbeitung von einem Element
    public <T extends Entity> void edit( List<T> obj, PopupContext popupContext )
    {
        // FIXME generate ids and info from object list;
        String applicationEventId = null;
        String info = null;
        final ApplicationEvent event = new ApplicationEvent(applicationEventId, info, popupContext);
        eventBus.fireEvent(event);
    }
}