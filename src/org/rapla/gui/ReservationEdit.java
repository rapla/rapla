package org.rapla.gui;

import java.util.Collection;
import java.util.Date;

import javax.swing.event.ChangeListener;

import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;

public interface ReservationEdit
{
    boolean isModifiedSinceLastChange();

    void addAppointment( Date start, Date end) throws RaplaException;
	
    Reservation getReservation();
    void save() throws RaplaException;
    void delete() throws RaplaException;
    /** You can add a listener that gets notified everytime a reservation is changed: Reservation attributes, appointments or allocation changes all count*/
    void addReservationChangeListener(ChangeListener listener);
    void removeReservationChangeListener(ChangeListener listener);
    
    void addAppointmentListener(AppointmentListener listener);
    void removeAppointmentListener(AppointmentListener listener);
   
    Collection<Appointment> getSelectedAppointments();
}