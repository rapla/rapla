package org.rapla.client.gwt;

import org.rapla.client.ReservationEdit;
import org.rapla.client.internal.ReservationEditFactory;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of = ReservationEditFactory.class, context = InjectionContext.gwt)
public class ReservationEditFactoryGwt implements ReservationEditFactory
{

    @Inject
    public ReservationEditFactoryGwt()
    {

    }
    @Override
    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        return null;
    }

}
