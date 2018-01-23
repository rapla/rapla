package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.internal.ReservationControllerImpl;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Set;

@DefaultImplementation(of= ReservationController.class,context = InjectionContext.swing)
@Singleton
public class ReservationControllerSwingImpl extends ReservationControllerImpl
{
    private final InfoFactory infoFactory;
    private final RaplaGUIComponent wrapper;
    private final RaplaImages images;
    private final Provider<Set<EventCheck>> checkers;

    @Inject
    public ReservationControllerSwingImpl(ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n,
            AppointmentFormater appointmentFormater, CalendarSelectionModel calendarModel, RaplaClipboard clipboard,Provider<Set<EventCheck>> checkers,InfoFactory infoFactory, RaplaImages images,DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, calendarModel, clipboard, dialogUiFactory);
        this.infoFactory = infoFactory;
        this.wrapper = new RaplaGUIComponent(facade, i18n, raplaLocale, logger);
        this.images = images;
        this.checkers = checkers;
    }

    protected Promise<Boolean> showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException
    {
        DialogInterface dlg =infoFactory.createDeleteDialog(deletables, context);
        return dlg.start(true).thenApply(result->result== 0 ? Boolean.TRUE : Boolean.FALSE);
    }

    @Override
    protected Provider<Set<EventCheck>> getEventChecks()
    {
        return checkers;
    }

    @Override
    protected PopupContext getPopupContext()
    {
        return new SwingPopupContext(wrapper.getMainComponent(), null);
    }


}
