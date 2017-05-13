package org.rapla.client.swing;

import java.util.Collection;

import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationEdit;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id = "org.rapla.client.swing.ReservationToolbarExtension")
public interface ReservationToolbarExtension
{
    Collection<RaplaWidget> createExtensionButtons(ReservationEdit edit);

    void setReservation(Reservation newReservation, Appointment mutableAppointment) throws RaplaException;
}
