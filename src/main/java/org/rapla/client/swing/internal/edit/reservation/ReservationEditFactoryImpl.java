package org.rapla.client.swing.internal.edit.reservation;

import java.awt.Component;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.client.internal.ReservationEditFactory;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.edit.reservation.AllocatableSelection.AllocatableSelectionFactory;
import org.rapla.client.swing.internal.edit.reservation.AppointmentListEdit.AppointmentListEditFactory;
import org.rapla.client.swing.internal.edit.reservation.ReservationInfoEdit.ReservationInfoEditFactory;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of = ReservationEditFactory.class, context = InjectionContext.swing)
@Singleton
public class ReservationEditFactoryImpl implements ReservationEditFactory
{
    private final Set<AppointmentStatusFactory> list;
    private final RaplaContext context;
    private final ReservationController reservationController;
    private final InfoFactory<Component, DialogUI> infoFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactory dialogUiFactory;
    private final ReservationInfoEditFactory reservationInfoEditFactory;
    private final AppointmentListEditFactory appointmentListEditFactory;
    private final AllocatableSelectionFactory allocatableSelectionFactory;

    @Inject
    public ReservationEditFactoryImpl(Set<AppointmentStatusFactory> list, RaplaContext context, ReservationController reservationController,
            InfoFactory<Component, DialogUI> infoFactory, RaplaImages raplaImages, DialogUiFactory dialogUiFactory,
            ReservationInfoEditFactory reservationInfoEditFactory, AppointmentListEditFactory appointmentListEditFactory,
            AllocatableSelectionFactory allocatableSelectionFactory)
    {
        this.list = list;
        this.context = context;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.reservationInfoEditFactory = reservationInfoEditFactory;
        this.appointmentListEditFactory = appointmentListEditFactory;
        this.allocatableSelectionFactory = allocatableSelectionFactory;
    }

    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        ReservationEditImpl edit = new ReservationEditImpl(context, list, reservationController, infoFactory, raplaImages, dialogUiFactory,
                reservationInfoEditFactory, appointmentListEditFactory, allocatableSelectionFactory);
        edit.editReservation(reservation, appointmentBlock);
        return edit;
    }

}
