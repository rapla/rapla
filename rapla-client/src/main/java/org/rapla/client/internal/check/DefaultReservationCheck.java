package org.rapla.client.internal.check;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.edit.check.DefaultReservationWarnings;
import org.rapla.client.edit.check.ReservationWarning;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;
import org.springframework.stereotype.Service;
import org.rapla.scheduler.ResolvedPromise;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Collection;
import java.util.Locale;


@Service

public class DefaultReservationCheck implements EventCheck
{
    private final DialogUiFactoryInterface dialogUiFactory;
    final ClientFacade clientFacade;
    private final CalendarModel model;
    private final AppointmentFormater appointmentFormater;
    RaplaResources i18n;
    final CheckView view;
    @Autowired
    public DefaultReservationCheck(ClientFacade facade, RaplaResources i18n, AppointmentFormater appointmentFormater, CalendarModel model, DialogUiFactoryInterface dialogUiFactory, CheckView view) {
        this.i18n = i18n;
        this.clientFacade = facade;
        this.appointmentFormater = appointmentFormater;
        this.model = model;
        this.dialogUiFactory = dialogUiFactory;
        this.view = view;
    }


    public Promise<Boolean> check(Collection<Reservation> reservations, PopupContext sourceComponent)
    {
        view.clear();
        try
        {
            User user = clientFacade.getUser();
            Preferences preferences = clientFacade.getRaplaFacade().getPreferences(user);
            final boolean showNotInCalendar = preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, true);
            RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
            // Pre-flight entity-level sanity check (server-replicated).
            for (Reservation reservation : reservations)
            {
                ReservationImpl.checkReservation(i18n, reservation, raplaFacade.getOperator());
            }
            // Pure decision: evaluate the four rules in rapla-core.
            // Same decisions an Angular client (PRD 026/028) would run before
            // calling /storage/dispatch — only the dialog popping below is
            // view-specific.
            boolean templateMode = clientFacade.getTemplate() != null;
            java.util.List<ReservationWarning> warnings = DefaultReservationWarnings.evaluate(
                    reservations,
                    i18n.getLocale(),
                    templateMode,
                    showNotInCalendar,
                    r -> {
                        try { return model.isMatchingSelectionAndFilter(r, null); }
                        catch (RaplaException e) { throw new RuntimeException(e); }
                    },
                    appointmentFormater::getShortSummary);
            for (ReservationWarning w : warnings)
            {
                view.addWarning(formatWarning(w));
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

    /** Map a {@link ReservationWarning} code to its i18n bundle key and
     *  interpolate args. View-side counterpart of the pure check —
     *  Angular has its own equivalent that builds Angular messages from
     *  the same code+args. */
    private String formatWarning(ReservationWarning w)
    {
        switch (w.code())
        {
            case NO_RESERVATION_NAME:
                return i18n.getString("error.no_reservation_name");
            case NOT_IN_CALENDAR:
                return i18n.format("warning.not_in_calendar", arg(w, 0));
            case DUPLICATED_APPOINTMENTS:
                return i18n.format("warning.duplicated_appointments", arg(w, 0));
            case NO_ALLOCATABLES_SELECTED:
                return i18n.getString("warning.no_allocatables_selected");
            default:
                return w.code().name();
        }
    }

    private static String arg(ReservationWarning w, int i)
    {
        return i < w.args().size() ? w.args().get(i) : "";
    }
}
