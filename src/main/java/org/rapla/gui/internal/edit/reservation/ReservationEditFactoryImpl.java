package org.rapla.gui.internal.edit.reservation;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.ReservationEdit;

public class ReservationEditFactoryImpl implements ReservationEditFactory
{
    RaplaContext context;
    public ReservationEditFactoryImpl(RaplaContext context)
    {
        this.context = context;
    }
    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        ReservationEditImpl edit = new ReservationEditImpl(context);
        edit.editReservation(reservation, appointmentBlock);
        return edit;
    }

}
