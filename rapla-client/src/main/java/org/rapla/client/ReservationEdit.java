package org.rapla.client;

import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface ReservationEdit<T> extends EditTaskPresenter.EditTaskView<Reservation,T>
{
    Promise<Void> addAppointment(Date start, Date end);

    /** {@code LocalDateTime} variant — distinct name avoids `null`-passing ambiguity. */
    default Promise<Void> addAppointmentLocalDateTime(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return addAppointment(
            start == null ? null : org.rapla.components.util.DateTools.toDate(start),
            end == null ? null : org.rapla.components.util.DateTools.toDate(end));
    }
	
    Reservation getReservation();

    void addAppointmentListener(AppointmentListener listener);
    void removeAppointmentListener(AppointmentListener listener);
   
    Collection<Appointment> getSelectedAppointments();

    void editReservation(Reservation reservation, Reservation original,AppointmentBlock appointmentBlock) throws RaplaException;

    Reservation getOriginal();

    boolean hasChanged();

    void addExceptionsToCurrentAppointment(List<TimeInterval> exceptions);

    void setReservation(Reservation reservation, Appointment appointment) throws RaplaException;
    //void updateReservation(Reservation persistent) throws RaplaException;

    //void deleteReservation() throws RaplaException;

    CommandHistory getCommandHistory();

    void updateView(ModificationEvent evt);

    void fireChange();

    boolean isNew();

    void setHasChanged(boolean b);
}