package org.rapla.client.internal;

import org.rapla.client.ReservationEdit;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;

public interface ReservationEditFactory
{
    ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException;

}
