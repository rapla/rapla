package org.rapla.client.internal.check;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Extension(provides = EventCheck.class, id = "requestallocationcheck")
public class RequestAllocationCheck implements EventCheck
{

    private final DialogUiFactoryInterface dialogUiFactory;
    final ClientFacade clientFacade;
    private final CalendarModel model;
    private final AppointmentFormater appointmentFormater;
    RaplaResources i18n;
    final CheckView view;
    @Inject
    public RequestAllocationCheck(ClientFacade facade, RaplaResources i18n, AppointmentFormater appointmentFormater, CalendarModel model, DialogUiFactoryInterface dialogUiFactory, CheckView view) {
        this.i18n = i18n;
        this.clientFacade = facade;
        this.appointmentFormater = appointmentFormater;
        this.model = model;
        this.dialogUiFactory = dialogUiFactory;
        this.view = view;
    }



    public Promise<Boolean> check(Collection<Reservation> reservations, PopupContext sourceComponent)
    {
        try
        {
            User user = clientFacade.getUser();
            Preferences preferences = clientFacade.getRaplaFacade().getPreferences(user);
            final boolean showNotInCalendar = preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, true);
            RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
            for (Reservation reservation : reservations)
            {
                for(Allocatable allocatable :  reservation.getAllocatables()){
                    final RequestStatus requestStatus = reservation.getRequestStatus(allocatable);
                    if (requestStatus == RequestStatus.CHANGED) {
                        view.addWarning("Wollen Sie eine Buchungsanfrage für die Ressource '" + allocatable.getName( null ) + "' erstellen?");
                    }
                }
            }
        }
        catch (RaplaException ex)
        {
            view.addWarning(ex.getMessage());
        }
        if (view.hasMessages())
        {
            DialogInterface dialog = dialogUiFactory.createContentDialog(sourceComponent, view.getComponent(), new String[] { i18n.getString("continue"), i18n.getString("back") });
            dialog.setTitle( i18n.getString("warning"));
            dialog.getAction(0).setIcon(i18n.getIcon("icon.save"));
            dialog.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
            return dialog.start(true).thenApply(
                    (index)->index == 0
            );
        }
        else
        {
            return new ResolvedPromise<>(true);
        }
    }
}

