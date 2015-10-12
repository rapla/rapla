package org.rapla.client.gwt;

import org.rapla.client.ReservationEdit;
import org.rapla.client.internal.ReservationEditFactory;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of = ReservationEditFactory.class, context = InjectionContext.gwt)
public class ReservationEditFactoryGwt implements ReservationEditFactory
{

    @Override
    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        return null;
    }

}
