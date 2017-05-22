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

    @Override
    public void editReservation(Reservation reservation, Reservation original,AppointmentBlock appointmentBlock, Runnable saveCmd, Runnable closeCmd, Runnable deleteCmd)
            throws RaplaException
    {

    }


    @Override public Reservation getOriginal()
    {
        return null;
    }


    @Override public CommandHistory getCommandHistory()
    {
        return null;
    }

    @Override public void updateView(ModificationEvent evt)
    {

    }

    @Override
    public void fireChange()
    {

    }

    @Override
    public boolean isNew()
    {
        return false;
    }

    @Override public IsWidget getComponent()
    {
        return null;
    }

    @Override
    public boolean hasChanged()
    {
        return false;
    }

    @Override
    public void setReservation(Reservation reservation, Appointment appointment)
    {

    }

    {

    }
}
