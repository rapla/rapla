package org.rapla.client.swing;

import org.rapla.client.ReservationEdit;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import java.awt.Component;
import java.util.Collection;

@ExtensionPoint(context = InjectionContext.swing,id = "org.rapla.client.swing.ReservationToolbarExtension")
public interface ReservationToolbarExtension
{
    Collection<Component> createExtensionButtons(ReservationEdit edit);

    void setReservation(Reservation newReservation, Appointment mutableAppointment);
}
