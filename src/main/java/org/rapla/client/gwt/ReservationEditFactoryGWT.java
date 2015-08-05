package org.rapla.client.gwt;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.internal.edit.reservation.ReservationEditFactory;

public class ReservationEditFactoryGWT implements ReservationEditFactory
{

    @Override
    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        return null;
    }

}
