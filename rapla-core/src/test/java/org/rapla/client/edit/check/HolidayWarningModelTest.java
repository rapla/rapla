package org.rapla.client.edit.check;

import org.junit.jupiter.api.Test;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.facade.PeriodModel;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link HolidayWarningModel}. Verifies the
 * holiday-conflict detection and the per-preference filter without
 * needing a real facade or {@code PeriodModel}.
 */
class HolidayWarningModelTest
{
    @Test
    void nullHolidayModelReturnsEmpty()
    {
        Map<Appointment, Set<Period>> out = HolidayWarningModel.findHolidayConflicts(null, List.of());
        assertTrue(out.isEmpty());
    }

    @Test
    void nullReservationsReturnsEmpty()
    {
        Map<Appointment, Set<Period>> out = HolidayWarningModel.findHolidayConflicts(
                stubPeriodModel(Map.of()), null);
        assertTrue(out.isEmpty());
    }

    @Test
    void appointmentOverlappingHolidayProducesEntry()
    {
        Appointment a = appointment("2026-06-04T09:00", "2026-06-04T10:00");
        Period holiday = period("2026-06-04T00:00", "2026-06-05T00:00", "Holiday");
        Reservation r = stubReservation(new Appointment[]{a});
        PeriodModel pm = stubPeriodModel(Map.of(intervalOf(a), List.of(holiday)));

        Map<Appointment, Set<Period>> out = HolidayWarningModel.findHolidayConflicts(pm, List.of(r));
        assertEquals(1, out.size());
        assertTrue(out.containsKey(a));
        assertTrue(out.get(a).contains(holiday));
    }

    @Test
    void appointmentNotOverlappingHolidayProducesNoEntry()
    {
        Appointment a = appointment("2026-06-10T09:00", "2026-06-10T10:00");
        // Period far from appointment; getPeriodsFor returns empty
        Reservation r = stubReservation(new Appointment[]{a});
        PeriodModel pm = stubPeriodModel(Map.of(intervalOf(a), List.of()));

        Map<Appointment, Set<Period>> out = HolidayWarningModel.findHolidayConflicts(pm, List.of(r));
        assertTrue(out.isEmpty());
    }

    @Test
    void multipleAppointmentsAreAggregated()
    {
        Appointment a1 = appointment("2026-06-04T09:00", "2026-06-04T10:00");
        Appointment a2 = appointment("2026-12-25T09:00", "2026-12-25T10:00");
        Period h1 = period("2026-06-04T00:00", "2026-06-05T00:00", "June holiday");
        Period h2 = period("2026-12-25T00:00", "2026-12-26T00:00", "Christmas");
        Reservation r = stubReservation(new Appointment[]{a1, a2});
        PeriodModel pm = stubPeriodModel(Map.of(
                intervalOf(a1), List.of(h1),
                intervalOf(a2), List.of(h2)));

        Map<Appointment, Set<Period>> out = HolidayWarningModel.findHolidayConflicts(pm, List.of(r));
        assertEquals(2, out.size());
        assertTrue(out.get(a1).contains(h1));
        assertTrue(out.get(a2).contains(h2));
    }

    // ---------- filter ----------

    @Test
    void filterEmptyInputReturnsEmpty()
    {
        Map<Appointment, Set<Period>> out = HolidayWarningModel.filterByPreference(Map.of(), true, true);
        assertTrue(out.isEmpty());
    }

    @Test
    void filterNullInputReturnsEmpty()
    {
        assertTrue(HolidayWarningModel.filterByPreference(null, true, true).isEmpty());
    }

    @Test
    void filterKeepsBothWhenBothPreferencesOn()
    {
        Appointment single = appointment("2026-06-04T09:00", "2026-06-04T10:00");
        Appointment repeating = appointmentWithRepeating("2026-06-05T09:00", "2026-06-05T10:00");
        Period p = period("2026-06-04T00:00", "2026-06-05T00:00", "H");
        Map<Appointment, Set<Period>> input = new HashMap<>();
        input.put(single, Set.of(p));
        input.put(repeating, Set.of(p));

        Map<Appointment, Set<Period>> out = HolidayWarningModel.filterByPreference(input, true, true);
        assertEquals(2, out.size());
    }

    @Test
    void filterDropsSingleAppointmentsWhenSinglePrefOff()
    {
        Appointment single = appointment("2026-06-04T09:00", "2026-06-04T10:00");
        Appointment repeating = appointmentWithRepeating("2026-06-05T09:00", "2026-06-05T10:00");
        Period p = period("2026-06-04T00:00", "2026-06-05T00:00", "H");
        Map<Appointment, Set<Period>> input = new HashMap<>();
        input.put(single, Set.of(p));
        input.put(repeating, Set.of(p));

        Map<Appointment, Set<Period>> out = HolidayWarningModel.filterByPreference(input,
                /*showRepeatingWarning=*/ true,
                /*showSingleAppointmentWarning=*/ false);
        assertEquals(1, out.size());
        assertTrue(out.containsKey(repeating));
        assertFalse(out.containsKey(single));
    }

    @Test
    void filterDropsRepeatingWhenRepeatingPrefOff()
    {
        Appointment single = appointment("2026-06-04T09:00", "2026-06-04T10:00");
        Appointment repeating = appointmentWithRepeating("2026-06-05T09:00", "2026-06-05T10:00");
        Period p = period("2026-06-04T00:00", "2026-06-05T00:00", "H");
        Map<Appointment, Set<Period>> input = new HashMap<>();
        input.put(single, Set.of(p));
        input.put(repeating, Set.of(p));

        Map<Appointment, Set<Period>> out = HolidayWarningModel.filterByPreference(input,
                /*showRepeatingWarning=*/ false,
                /*showSingleAppointmentWarning=*/ true);
        assertEquals(1, out.size());
        assertTrue(out.containsKey(single));
        assertFalse(out.containsKey(repeating));
    }

    @Test
    void filterDropsAllWhenBothPrefsOff()
    {
        Appointment single = appointment("2026-06-04T09:00", "2026-06-04T10:00");
        Appointment repeating = appointmentWithRepeating("2026-06-05T09:00", "2026-06-05T10:00");
        Period p = period("2026-06-04T00:00", "2026-06-05T00:00", "H");
        Map<Appointment, Set<Period>> input = new HashMap<>();
        input.put(single, Set.of(p));
        input.put(repeating, Set.of(p));

        Map<Appointment, Set<Period>> out = HolidayWarningModel.filterByPreference(input, false, false);
        assertTrue(out.isEmpty());
    }

    // ---------- helpers ----------

    private static TimeInterval intervalOf(Appointment a)
    {
        return new TimeInterval(a.getStart(), a.getMaxEnd());
    }

    private static int idCounter = 0;

    private static Appointment appointment(String startIso, String endIso)
    {
        AppointmentImpl a = new AppointmentImpl(LocalDateTime.parse(startIso), LocalDateTime.parse(endIso));
        // SimpleEntity.hashCode() requires an id — assign a stable test id so
        // HashMap operations don't throw.
        a.setId("test-app-" + (++idCounter));
        return a;
    }

    private static Appointment appointmentWithRepeating(String startIso, String endIso)
    {
        AppointmentImpl a = (AppointmentImpl) appointment(startIso, endIso);
        a.setRepeatingEnabled(true);
        return a;
    }

    private static Period period(String startIso, String endIso, String name)
    {
        LocalDateTime start = LocalDateTime.parse(startIso);
        LocalDateTime end = LocalDateTime.parse(endIso);
        return (Period) Proxy.newProxyInstance(
                Period.class.getClassLoader(),
                new Class[] { Period.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getStart":  return start;
                        case "getEnd":    return end;
                        case "getName":   return name;
                        case "equals":    return proxy == args[0];
                        case "hashCode":  return System.identityHashCode(proxy);
                        case "toString":  return "StubPeriod[" + name + "]";
                        default: return null;
                    }
                });
    }

    private static Reservation stubReservation(Appointment[] appointments)
    {
        return (Reservation) Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getAppointments": return appointments;
                        case "equals":          return proxy == args[0];
                        case "hashCode":        return System.identityHashCode(proxy);
                        default: return null;
                    }
                });
    }

    /** A PeriodModel stub that returns canned periods for each interval the
     *  test sets up. Other PeriodModel methods are not used by the model. */
    private static PeriodModel stubPeriodModel(Map<TimeInterval, List<Period>> answer)
    {
        return (PeriodModel) Proxy.newProxyInstance(
                PeriodModel.class.getClassLoader(),
                new Class[] { PeriodModel.class },
                (proxy, method, args) ->
                {
                    if ("getPeriodsFor".equals(method.getName()) && args.length == 1
                            && args[0] instanceof TimeInterval ti)
                    {
                        return answer.getOrDefault(ti, List.of());
                    }
                    return null;
                });
    }
}
