package org.rapla.client.swing.internal.edit.reservation;

import java.awt.Component;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
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
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of = ReservationEditFactory.class, context = InjectionContext.swing)
@Singleton
public class ReservationEditFactoryImpl implements ReservationEditFactory
{
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;
    private final Set<AppointmentStatusFactory> list;
    private final ReservationController reservationController;
    private final InfoFactory<Component, DialogUI> infoFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactory dialogUiFactory;
    private final ReservationInfoEditFactory reservationInfoEditFactory;
    private final AppointmentListEditFactory appointmentListEditFactory;
    private final AllocatableSelectionFactory allocatableSelectionFactory;
    private final PermissionController permissionController;
    private final FrameControllerList frameControllerList;

    @Inject
    public ReservationEditFactoryImpl(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Set<AppointmentStatusFactory> list,
            RaplaContext context, ReservationController reservationController, InfoFactory<Component, DialogUI> infoFactory, RaplaImages raplaImages,
            DialogUiFactory dialogUiFactory, ReservationInfoEditFactory reservationInfoEditFactory, AppointmentListEditFactory appointmentListEditFactory,
            AllocatableSelectionFactory allocatableSelectionFactory, PermissionController permissionController, FrameControllerList frameControllerList)
    {
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.list = list;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.reservationInfoEditFactory = reservationInfoEditFactory;
        this.appointmentListEditFactory = appointmentListEditFactory;
        this.allocatableSelectionFactory = allocatableSelectionFactory;
        this.permissionController = permissionController;
        this.frameControllerList = frameControllerList;
    }

    public ReservationEdit create(Reservation reservation, AppointmentBlock appointmentBlock) throws RaplaException
    {
        ReservationEditImpl edit = new ReservationEditImpl(facade, i18n, raplaLocale, logger, list, reservationController, infoFactory, raplaImages,
                dialogUiFactory, reservationInfoEditFactory, appointmentListEditFactory, allocatableSelectionFactory, permissionController, frameControllerList);
        edit.editReservation(reservation, appointmentBlock);
        return edit;
    }

}
