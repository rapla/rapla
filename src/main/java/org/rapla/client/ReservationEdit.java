package org.rapla.client;

import java.util.Collection;
import java.util.Date;

import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.function.Consumer;

public interface ReservationEdit<T> extends RaplaWidget<T>
{
    void addAppointment( Date start, Date end) throws RaplaException;
	
    Reservation getReservation();

    void addAppointmentListener(AppointmentListener listener);
    void removeAppointmentListener(AppointmentListener listener);
   
    Collection<Appointment> getSelectedAppointments();

    void editReservation(Reservation reservation, AppointmentBlock appointmentBlock, Runnable saveCmd, Runnable closeCmd, Runnable deleteCmd) throws RaplaException;

    Reservation getOriginal();

    //void updateReservation(Reservation persistant) throws RaplaException;

    //void deleteReservation() throws RaplaException;

    CommandHistory getCommandHistory();

    void updateView(ModificationEvent evt);
}