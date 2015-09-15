package org.rapla.gui.internal.edit.reservation;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.ReservationEdit;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of= ReservationEditFactory.class,context = InjectionContext.swing)
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
