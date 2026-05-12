package org.rapla.client.edit.check;

import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure-Java decision logic for the default pre-save reservation warnings.
 * Carved out of {@code rapla-client/.../check/DefaultReservationCheck}
 * (PRD 023 Phase 10 — EventCheck carve-out).
 * <p>
 * The four rules:
 * <ul>
 *   <li>{@code NO_RESERVATION_NAME} — name is blank and reservation is
 *       not a template.</li>
 *   <li>{@code DUPLICATED_APPOINTMENTS} — two appointments in the same
 *       reservation match (same start/end + same recurrence).</li>
 *   <li>{@code NO_ALLOCATABLES_SELECTED} — zero allocatables and not a
 *       template.</li>
 *   <li>{@code NOT_IN_CALENDAR} — reservation doesn't appear in the
 *       currently visible calendar's selection+filter, and the
 *       {@code show-not-in-calendar} preference is on, and the user
 *       isn't editing a template.</li>
 * </ul>
 * <p>
 * Outputs a list of {@link ReservationWarning}s in input-reservation
 * order, then in the rule order above within each reservation. The view
 * tier renders them as it sees fit (Swing dialog, Angular toast, REST
 * response body).
 */
public final class DefaultReservationWarnings
{
    private DefaultReservationWarnings() {}

    /**
     * @param reservations               the reservations being saved
     * @param locale                     locale for {@code getName(locale)}
     * @param templateMode               true when the user is editing a template
     *                                   (suppresses several warnings — templates
     *                                   are allowed to be incomplete)
     * @param showNotInCalendarWarning   preference flag — typically
     *                                   {@code CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING}
     * @param isInCurrentCalendar        per-reservation predicate; typically
     *                                   {@code calendarModel::isMatchingSelectionAndFilter}
     * @param appointmentShortSummary    formatter for the offending appointment
     *                                   when DUPLICATED_APPOINTMENTS fires
     */
    public static List<ReservationWarning> evaluate(
            Collection<Reservation> reservations,
            Locale locale,
            boolean templateMode,
            boolean showNotInCalendarWarning,
            Predicate<Reservation> isInCurrentCalendar,
            Function<Appointment, String> appointmentShortSummary)
    {
        List<ReservationWarning> warnings = new ArrayList<>();
        if (reservations == null) return warnings;
        for (Reservation r : reservations)
        {
            String template = r.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
            String name = r.getName(locale);
            boolean isTemplate = template != null;

            // 1. NO_RESERVATION_NAME
            if (name != null && name.trim().isEmpty() && !isTemplate)
            {
                warnings.add(ReservationWarning.of(ReservationWarning.Code.NO_RESERVATION_NAME));
            }

            // 2. NOT_IN_CALENDAR
            if (!templateMode && showNotInCalendarWarning && !isTemplate
                    && isInCurrentCalendar != null && !isInCurrentCalendar.test(r))
            {
                warnings.add(ReservationWarning.of(ReservationWarning.Code.NOT_IN_CALENDAR,
                        name != null ? name : ""));
            }

            // 3. DUPLICATED_APPOINTMENTS
            Appointment duplicated = findDuplicatedAppointment(r.getAppointments());
            if (duplicated != null)
            {
                String summary = appointmentShortSummary != null
                        ? appointmentShortSummary.apply(duplicated)
                        : "";
                warnings.add(ReservationWarning.of(ReservationWarning.Code.DUPLICATED_APPOINTMENTS, summary));
            }

            // 4. NO_ALLOCATABLES_SELECTED
            if (r.getAllocatables().length == 0 && !isTemplate)
            {
                warnings.add(ReservationWarning.of(ReservationWarning.Code.NO_ALLOCATABLES_SELECTED));
            }
        }
        return warnings;
    }

    /**
     * Return the first appointment that matches a later one in the array
     * (same start/end + same recurrence semantics), or {@code null}.
     * Mirrors the legacy double-loop in {@code DefaultReservationCheck.check}.
     */
    static Appointment findDuplicatedAppointment(Appointment[] appointments)
    {
        if (appointments == null || appointments.length < 2) return null;
        for (int i = 0; i < appointments.length; i++)
        {
            for (int j = i + 1; j < appointments.length; j++)
            {
                if (appointments[i].matches(appointments[j]))
                {
                    return appointments[i];
                }
            }
        }
        return null;
    }
}
