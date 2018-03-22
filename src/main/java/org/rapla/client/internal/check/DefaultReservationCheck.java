package org.rapla.client.internal.check;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Locale;


@Extension(provides = EventCheck.class, id = "defaultcheck")
public class DefaultReservationCheck implements EventCheck
{
    private final DialogUiFactoryInterface dialogUiFactory;
    final ClientFacade clientFacade;
    private final CalendarModel model;
    private final AppointmentFormater appointmentFormater;
    RaplaResources i18n;
    final CheckView view;
    @Inject
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
        try
        {
            User user = clientFacade.getUser();
            Preferences preferences = clientFacade.getRaplaFacade().getPreferences(user);
            final boolean showNotInCalendar = preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, true);
            RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
            for (Reservation reservation : reservations)
            {
                ReservationImpl.checkReservation(i18n, reservation, raplaFacade.getOperator());
                Appointment[] appointments = reservation.getAppointments();
                Appointment duplicatedAppointment = null;
                for (int i=0;i<appointments.length;i++) {
                    for (int j=i + 1;j<appointments.length;j++)
                        if (appointments[i].matches(appointments[j])) {
                            duplicatedAppointment = appointments[i];
                            break;
                        }
                }
                String template = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
                Locale locale = i18n.getLocale();
                String name = reservation.getName(locale);
                if (name.trim().length() == 0 && template == null)
                {
                    view.addWarning(i18n.getString("error.no_reservation_name"));
                }

                if (!model.isMatchingSelectionAndFilter(reservation, null) && clientFacade.getTemplate() == null && showNotInCalendar)
                {
                    view.addWarning(i18n.format("warning.not_in_calendar", reservation.getName(locale)));
                }

                if (duplicatedAppointment != null)
                {
                    view.addWarning(i18n.format("warning.duplicated_appointments", appointmentFormater.getShortSummary(duplicatedAppointment)));

                }
                if (reservation.getAllocatables().length == 0 && template == null)
                {
                    view.addWarning(i18n.getString("warning.no_allocatables_selected"));
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
