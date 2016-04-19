package org.rapla.client.swing.internal;

import javax.inject.Inject;
import javax.inject.Provider;

import org.rapla.client.ReservationEdit;
import org.rapla.client.internal.ReservationEditFactory;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(context = InjectionContext.swing, of = ReservationEditFactory.class)
public class ReservationEditFactorySwing implements ReservationEditFactory
{

    private final Provider<ReservationEdit> reservationEditProvider;

    @Inject
    public ReservationEditFactorySwing(Provider<ReservationEdit> reservationEditProvider)
    {
        this.reservationEditProvider = reservationEditProvider;
    }

    @Override
    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        final ReservationEdit reservationEdit = reservationEditProvider.get();
        reservationEdit.editReservation(reservation, appointmentBlock);
        return reservationEdit;
    }

}
