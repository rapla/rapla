package org.rapla.client;

import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
public class EditController
{
    private final ApplicationEventBus eventBus;
    @Inject
    public EditController(ApplicationEventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    public ReservationEdit[] getEditWindows()
    {
        return new ReservationEdit[] {};

    }

    public <T extends Entity> void edit( AppointmentBlock appointmentBlock, PopupContext popupContext)
    {
        String applicationEventId = EditTaskPresenter.EDIT_EVENTS_ID;
        final Reservation reservation = appointmentBlock.getAppointment().getReservation();
        String info = reservation.getId();
        final List<Reservation> appointmentBlocks = Collections.singletonList(reservation);
        EditApplicationEventContext context = new EditApplicationEventContext<>(appointmentBlocks);
        context.setAppointmentBlock(appointmentBlock);
        final ApplicationEvent event = new ApplicationEvent(applicationEventId, info, popupContext, context);
        eventBus.publish(event);
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
        String applicationEventId = EditTaskPresenter.EDIT_RESOURCES_ID;
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (T t : obj)
        {
            if ( t instanceof Reservation)
            {
                applicationEventId = EditTaskPresenter.EDIT_EVENTS_ID;
            }
            if (first)
            {
                first = false;
            }
            else
            {
                sb.append(",");
            }
            sb.append(t.getId());
        }
        String info = sb.toString();
        ApplicationEventContext context = new EditApplicationEventContext(obj);
        final ApplicationEvent event = new ApplicationEvent(applicationEventId, info, popupContext, context);
        eventBus.publish(event);
    }
}