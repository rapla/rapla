package org.rapla.gui.internal.edit.reservation;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.gui.ReservationEdit;

public interface ReservationEditFactory
{
    ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException;

}
