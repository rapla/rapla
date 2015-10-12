package org.rapla.client.internal;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.client.ReservationEdit;

public interface ReservationEditFactory
{
    ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException;

}
