package org.rapla.client.gwt;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.internal.ReservationControllerImpl;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import java.util.Set;

@DefaultImplementation(of= ReservationController.class, context = InjectionContext.gwt)
public class ReservationControllerGwtImpl extends ReservationControllerImpl
{
    @Inject
    public ReservationControllerGwtImpl(ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n,
            AppointmentFormater appointmentFormater, CalendarSelectionModel calendarModel, RaplaClipboard clipboard, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, calendarModel, clipboard, dialogUiFactory);
    }


    @Override
    protected PopupContext getPopupContext()
    {
        return new GwtPopupContext(null);
    }


    @Override
    protected Promise<Boolean> showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException
    {
        return new ResolvedPromise(Boolean.TRUE);
    }

    @Override
    protected Provider<Set<EventCheck>> getEventChecks()
    {
        return new Provider<Set<EventCheck>>()
        {
            @Override
            public Set<EventCheck> get()
            {
                return Collections.emptySet();
            }
        };
    }

    @Override
    protected void busy(String text)
    {

    }

    @Override
    protected void idle()
    {

    }
}
