package org.rapla.plugin.planningstatus;

import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.CalendarSelectionModel;

import java.util.Locale;
import java.util.function.Predicate;

public class PlanningStatusFilter implements Predicate<Appointment> {
    public static PlanningStatusFilter createFromCalendarModel(CalendarSelectionModel model)
    {
        String option = model.getOption(PlanningStatusPlugin.PUBLISH_NON_PLANNED);
        if (option != null && option.equalsIgnoreCase("true"))
        {
            // show all
            return null;
        }
        else
        {
            return new PlanningStatusFilter();
        }
    }

    public static boolean isPlannable(Reservation reservation) {
        Classification classification = reservation.getClassification();
        return classification.getType().getAttribute("status") != null;
    }

    public boolean test(Appointment appointment) {
        Reservation reservation = appointment.getReservation();
        if (reservation == null)
        {
            return false;
        }
        return testReservation(reservation);
    }

    static public boolean testReservation(Reservation reservation) {
        Classification classification = reservation.getClassification();
        String annotation = classification.getType().getAnnotation(PlanningStatusPlugin.PLANNINGSTATUS_CONDITION_ANNOTATION_NAME);
        if (annotation == null || annotation.length() == 0)
        {
            Attribute status = classification.getAttribute("status");
            if (status != null) {
                Object valueForAttribute = classification.getValueForAttribute(status);
                return valueForAttribute == null || valueForAttribute.equals(Boolean.TRUE);
            }
            // no annotation set, show all
            return true;
        }
        String format = classification.format(Locale.ENGLISH, PlanningStatusPlugin.PLANNINGSTATUS_CONDITION_ANNOTATION_NAME).toLowerCase();
        return !(format.equals("false")  || format.equals("no"));
    }
}
