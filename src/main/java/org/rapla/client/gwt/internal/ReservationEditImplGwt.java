package org.rapla.client.gwt.internal;

import com.google.gwt.user.client.ui.IsWidget;
import org.rapla.client.AppointmentListener;
import org.rapla.client.ReservationEdit;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Date;

@DefaultImplementation(context = InjectionContext.gwt, of = ReservationEdit.class)
public class ReservationEditImplGwt implements ReservationEdit<IsWidget>
{
    @Inject
    public ReservationEditImplGwt()
    {
    }

    @Override public boolean isModifiedSinceLastChange()
    {
        return false;
    }

    @Override public void addAppointment(Date start, Date end) throws RaplaException
    {

    }

    @Override public Reservation getReservation()
    {
        return null;
    }

    @Override public void addAppointmentListener(AppointmentListener listener)
    {

    }

    @Override public void removeAppointmentListener(AppointmentListener listener)
    {

    }

    @Override public Collection<Appointment> getSelectedAppointments()
    {
        return null;
    }

    @Override public void editReservation(Reservation reservation, AppointmentBlock appointmentBlock, ApplicationEvent event) throws RaplaException
    {

    }

    @Override public Reservation getOriginal()
    {
        return null;
    }

    @Override public void updateReservation(Reservation persistant) throws RaplaException
    {

    }

    @Override public void deleteReservation() throws RaplaException
    {

    }

    @Override public void save() throws RaplaException
    {

    }

    @Override public CommandHistory getCommandHistory()
    {
        return null;
    }

    @Override public void updateView(ModificationEvent evt)
    {

    }

    @Override public IsWidget getComponent()
    {
        return null;
    }
}
