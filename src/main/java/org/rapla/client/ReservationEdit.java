package org.rapla.client;

import java.util.Collection;
import java.util.Date;

import javax.swing.event.ChangeListener;

import org.rapla.client.event.ApplicationEvent;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;

public interface ReservationEdit<T> extends RaplaWidget<T>
{
    boolean isModifiedSinceLastChange();

    void addAppointment( Date start, Date end) throws RaplaException;
	
    Reservation getReservation();

    void addAppointmentListener(AppointmentListener listener);
    void removeAppointmentListener(AppointmentListener listener);
   
    Collection<Appointment> getSelectedAppointments();

    void editReservation(Reservation reservation, AppointmentBlock appointmentBlock, ApplicationEvent event) throws RaplaException;

    Reservation getOriginal();

    void updateReservation(Reservation persistant) throws RaplaException;

    void deleteReservation() throws RaplaException;

    void save() throws RaplaException;

    void addReservationChangeListener(ChangeListener listener);

    void removeReservationChangeListener(ChangeListener listener);

    CommandHistory getCommandHistory();

    void updateView(ModificationEvent evt);
}