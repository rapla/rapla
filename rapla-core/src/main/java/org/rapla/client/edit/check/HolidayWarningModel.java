package org.rapla.client.edit.check;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.PeriodModel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure-Java decision logic for the holiday-overlap pre-save warning.
 * Carved out of {@code rapla-client/.../check/HolidayExceptionCheck}
 * (PRD 023 Phase 10).
 * <p>
 * Two steps:
 * <ul>
 *   <li>{@link #findHolidayConflicts}: for each appointment in the
 *       supplied reservations, find the holiday {@link Period}s it
 *       overlaps.</li>
 *   <li>{@link #filterByPreference}: drop conflicts that the user has
 *       opted out of seeing (single-appointment vs. repeating).</li>
 * </ul>
 * <p>
 * The actual warning dialog (checkboxes to add holidays as exceptions)
 * stays view-side — only the decisions are pure.
 */
public final class HolidayWarningModel
{
    private HolidayWarningModel() {}

    /**
     * Compute the holiday-period overlap for every appointment in
     * {@code reservations}.
     *
     * @param holidayPeriods the holiday {@link PeriodModel} (typically
     *                       {@code PeriodModel.getHoliday(facade)}). When
     *                       {@code null}, returns an empty map.
     */
    public static Map<Appointment, Set<Period>> findHolidayConflicts(
            PeriodModel holidayPeriods, Collection<Reservation> reservations)
    {
        Map<Appointment, Set<Period>> out = new LinkedHashMap<>();
        if (holidayPeriods == null || reservations == null) return out;
        for (Reservation r : reservations)
        {
            for (Appointment app : r.getAppointments())
            {
                TimeInterval interval = new TimeInterval(app.getStart(), app.getMaxEnd());
                List<Period> periodsFor = holidayPeriods.getPeriodsFor(interval);
                for (Period period : periodsFor)
                {
                    if (app.overlaps(period.getStart(), period.getEnd()))
                    {
                        out.computeIfAbsent(app, k -> new LinkedHashSet<>()).add(period);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Filter the conflict map by user preferences:
     * <ul>
     *   <li>{@code showRepeatingWarning} false → drop entries where the
     *       appointment has a recurrence rule.</li>
     *   <li>{@code showSingleAppointmentWarning} false → drop entries
     *       where the appointment is a single occurrence.</li>
     * </ul>
     */
    public static Map<Appointment, Set<Period>> filterByPreference(
            Map<Appointment, Set<Period>> conflicts,
            boolean showRepeatingWarning,
            boolean showSingleAppointmentWarning)
    {
        if (conflicts == null || conflicts.isEmpty()) return Map.of();
        return conflicts.entrySet().stream()
                .filter(e ->
                {
                    boolean isRepeating = e.getKey().getRepeating() != null;
                    return (isRepeating && showRepeatingWarning)
                            || (!isRepeating && showSingleAppointmentWarning);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * Total count of period conflicts across all appointments — sum of
     * {@code Set<Period>.size()} across the map's values. Drives the
     * "Holidays (N)" label on the reservation-edit toolbar.
     *
     * <p>Null map and null entries are treated as zero.
     */
    public static int countAllPeriodConflicts(Map<Appointment, Set<Period>> conflicts)
    {
        if (conflicts == null) return 0;
        int total = 0;
        for (Set<Period> periods : conflicts.values())
        {
            if (periods != null) total += periods.size();
        }
        return total;
    }
}
