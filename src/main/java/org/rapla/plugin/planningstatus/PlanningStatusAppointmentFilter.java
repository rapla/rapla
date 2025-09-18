package org.rapla.plugin.planningstatus;

import org.rapla.entities.Category;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.CalendarModel;

import java.util.function.Predicate;

public class PlanningStatusAppointmentFilter implements Predicate<Appointment> {
    public static PlanningStatusAppointmentFilter  createFromCalendarModel(CalendarModel model)
    {
        String option = model.getOption(PlanningStatusPlugin.PUBLISH_NON_PLANNED);
        if (option != null && option.equalsIgnoreCase("true"))
        {
            // show all
            return null;
        }
        else
        {
            return new PlanningStatusAppointmentFilter();
        }
    }

    public boolean test(Appointment appointment) {
        Reservation reservation = appointment.getReservation();
        if (reservation == null)
        {
            return false;
        }
        Classification classification = reservation.getClassification();
        Attribute status = classification.getAttribute("status");


        if (status != null) {
            Object valueForAttribute = classification.getValueForAttribute(status);
            if ( valueForAttribute != null && valueForAttribute instanceof Category && ((Category)valueForAttribute).getKey().equals("planning_complete") )
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        return true;
    }
}
