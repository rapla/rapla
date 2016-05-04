package org.rapla.client;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.swing.SwingActivityController;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EditController
{
    private final EventBus eventBus;
    private final RaplaFacade facade;

    @Inject
    public EditController(EventBus eventBus, RaplaFacade facade)
    {
        this.eventBus = eventBus;
        this.facade = facade;
    }

    public ReservationEdit[] getEditWindows()
    {
        return new ReservationEdit[] {};

    }
    public void newObject( Object contextObject, PopupContext popupContext )
    {

    }

    public <T extends Entity> void edit( AppointmentBlock appointmentBlock, PopupContext popupContext)
    {
        String applicationEventId = EditTaskPresenter.EDIT_EVENTS_ID;
        final Reservation reservation = appointmentBlock.getAppointment().getReservation();
        String info = reservation.getId();
        final List<Reservation> appointmentBlocks = Collections.singletonList(reservation);
        EditApplicationEventContext context = new EditApplicationEventContext<Reservation>(appointmentBlocks);
        context.setAppointmentBlock(appointmentBlock);
        final ApplicationEvent event = new ApplicationEvent(applicationEventId, info, popupContext, context);
        eventBus.fireEvent(event);
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
        eventBus.fireEvent(event);
    }
}