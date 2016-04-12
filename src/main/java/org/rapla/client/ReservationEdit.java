package org.rapla.client;

import java.util.Collection;
import java.util.Date;

import javax.swing.event.ChangeListener;

import org.rapla.client.event.TaskPresenter;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;

public interface ReservationEdit extends TaskPresenter
{
    boolean isModifiedSinceLastChange();

    void addAppointment( Date start, Date end) throws RaplaException;
	
    Reservation getReservation();

    void addAppointmentListener(AppointmentListener listener);
    void removeAppointmentListener(AppointmentListener listener);
   
    Collection<Appointment> getSelectedAppointments();

    void editReservation(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException;

    void toFront();

    Reservation getOriginal();

    void updateReservation(Reservation persistant) throws RaplaException;

    void deleteReservation() throws RaplaException;

    void addReservationChangeListener(ChangeListener listener);

    void removeReservationChangeListener(ChangeListener listener);

    CommandHistory getCommandHistory();
}