package org.rapla.gui;

import java.util.Collection;
import java.util.Date;

import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;

public interface ReservationEdit
{
    boolean isModifiedSinceLastChange();

    void addAppointment( Date start, Date end) throws RaplaException;
	
    Reservation getReservation();
    void save() throws RaplaException;
    void delete() throws RaplaException;
    
    void addAppointmentListener(AppointmentListener listener);
    void removeAppointmentListener(AppointmentListener listener);
   
    Collection<Appointment> getSelectedAppointments();

    void editReservation(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException;

    void toFront();

    void refresh(ModificationEvent evt) throws RaplaException;

    Reservation getOriginal();

    void updateReservation(Reservation persistant) throws RaplaException;

    void deleteReservation() throws RaplaException;
}