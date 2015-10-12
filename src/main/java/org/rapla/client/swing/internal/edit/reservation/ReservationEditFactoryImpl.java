package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.client.ReservationEdit;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.client.internal.ReservationEditFactory;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@DefaultImplementation(of= ReservationEditFactory.class,context = InjectionContext.swing)
@Singleton
public class ReservationEditFactoryImpl implements ReservationEditFactory
{
    private final Set<AppointmentStatusFactory> list;
    private final RaplaContext context;
    private final Set<SwingViewFactory> swingViewFactories;
    @Inject
    public ReservationEditFactoryImpl(Set<AppointmentStatusFactory> list, RaplaContext context, Set<SwingViewFactory> swingViewFactories)
    {
        this.list = list;
        this.context = context;
        this.swingViewFactories = swingViewFactories;
    }
    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        ReservationEditImpl edit = new ReservationEditImpl(context, list, swingViewFactories);
        edit.editReservation(reservation, appointmentBlock);
        return edit;
    }

}
