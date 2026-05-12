package org.rapla.client.edit.check;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.check.ReservationWarning.Code;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link DefaultReservationWarnings} (PRD 023
 * Phase 10 — EventCheck carve-out). Pins the four pre-save rules:
 * NO_RESERVATION_NAME, NOT_IN_CALENDAR, DUPLICATED_APPOINTMENTS,
 * NO_ALLOCATABLES_SELECTED.
 */
class DefaultReservationWarningsTest
{
    private static final Predicate<Reservation> ALWAYS_IN_CALENDAR = r -> true;
    private static final Function<Appointment, String> APP_SUMMARY = a -> "summary[" + a.getStart() + "]";

    // ---------- NO_RESERVATION_NAME ----------

    @Test
    void blankNameProducesNoReservationNameWarning()
    {
        Reservation r = stubReservation("R1", "  ", null, new Appointment[0], 0);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertHasCode(ws, Code.NO_RESERVATION_NAME);
    }

    @Test
    void blankNameOnTemplateDoesNotWarn()
    {
        Reservation r = stubReservation("R1", "  ", "yes", new Appointment[0], 0);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.NO_RESERVATION_NAME));
    }

    @Test
    void nonBlankNameDoesNotWarn()
    {
        Appointment a1 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Reservation r = stubReservation("R1", "My Event", null, new Appointment[]{a1}, 1);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.NO_RESERVATION_NAME));
    }

    // ---------- DUPLICATED_APPOINTMENTS ----------

    @Test
    void duplicateAppointmentsAreDetected()
    {
        // Two AppointmentImpls with identical start/end → matches returns true.
        Appointment a1 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Reservation r = stubReservation("R1", "My Event", null, new Appointment[]{a1, a2}, 1);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertHasCode(ws, Code.DUPLICATED_APPOINTMENTS);
        // Argument carries the appointment summary.
        ReservationWarning dup = ws.stream().filter(w -> w.code() == Code.DUPLICATED_APPOINTMENTS).findFirst().get();
        assertEquals(1, dup.args().size());
        assertTrue(dup.args().get(0).startsWith("summary["));
    }

    @Test
    void distinctAppointmentsDoNotTriggerDuplicateWarning()
    {
        Appointment a1 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment("2026-06-02T09:00", "2026-06-02T10:00");
        Reservation r = stubReservation("R1", "My Event", null, new Appointment[]{a1, a2}, 1);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.DUPLICATED_APPOINTMENTS));
    }

    @Test
    void singleAppointmentNeverTriggersDuplicate()
    {
        Appointment a1 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Reservation r = stubReservation("R1", "My Event", null, new Appointment[]{a1}, 1);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.DUPLICATED_APPOINTMENTS));
    }

    // ---------- NO_ALLOCATABLES_SELECTED ----------

    @Test
    void zeroAllocatablesWarns()
    {
        Reservation r = stubReservation("R1", "Event", null, new Appointment[0], 0);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertHasCode(ws, Code.NO_ALLOCATABLES_SELECTED);
    }

    @Test
    void zeroAllocatablesOnTemplateDoesNotWarn()
    {
        Reservation r = stubReservation("R1", "Event", "yes", new Appointment[0], 0);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.NO_ALLOCATABLES_SELECTED));
    }

    @Test
    void someAllocatablesDoesNotWarn()
    {
        Reservation r = stubReservation("R1", "Event", null, new Appointment[0], 2);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.NO_ALLOCATABLES_SELECTED));
    }

    // ---------- NOT_IN_CALENDAR ----------

    @Test
    void notInCalendarWithPrefOnWarns()
    {
        Reservation r = stubReservation("R1", "Event", null, new Appointment[0], 1);
        Predicate<Reservation> notIn = res -> false;
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, /*showNotInCalendar=*/ true, notIn, APP_SUMMARY);
        assertHasCode(ws, Code.NOT_IN_CALENDAR);
        ReservationWarning notInWarn = ws.stream().filter(w -> w.code() == Code.NOT_IN_CALENDAR).findFirst().get();
        assertEquals("Event", notInWarn.args().get(0), "reservation name passed as arg 0");
    }

    @Test
    void notInCalendarSuppressedByPref()
    {
        Reservation r = stubReservation("R1", "Event", null, new Appointment[0], 1);
        Predicate<Reservation> notIn = res -> false;
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, /*showNotInCalendar=*/ false, notIn, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.NOT_IN_CALENDAR));
    }

    @Test
    void notInCalendarSuppressedInTemplateMode()
    {
        Reservation r = stubReservation("R1", "Event", null, new Appointment[0], 1);
        Predicate<Reservation> notIn = res -> false;
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, /*templateMode=*/ true, true, notIn, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.NOT_IN_CALENDAR));
    }

    @Test
    void notInCalendarSuppressedForTemplateReservation()
    {
        Reservation r = stubReservation("R1", "Event", /*template=*/ "yes", new Appointment[0], 1);
        Predicate<Reservation> notIn = res -> false;
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, notIn, APP_SUMMARY);
        assertFalse(ws.stream().anyMatch(w -> w.code() == Code.NOT_IN_CALENDAR));
    }

    // ---------- ordering + multiple ----------

    @Test
    void multipleWarningsPerReservationStackInRuleOrder()
    {
        // blank name + duplicate appointments + no allocatables
        Appointment a1 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Reservation r = stubReservation("R1", "", null, new Appointment[]{a1, a2}, 0);
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                List.of(r), Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        // Order: NO_RESERVATION_NAME, then DUPLICATED_APPOINTMENTS, then NO_ALLOCATABLES_SELECTED
        assertEquals(3, ws.size());
        assertEquals(Code.NO_RESERVATION_NAME, ws.get(0).code());
        assertEquals(Code.DUPLICATED_APPOINTMENTS, ws.get(1).code());
        assertEquals(Code.NO_ALLOCATABLES_SELECTED, ws.get(2).code());
    }

    @Test
    void nullReservationsReturnsEmpty()
    {
        List<ReservationWarning> ws = DefaultReservationWarnings.evaluate(
                null, Locale.ROOT, false, true, ALWAYS_IN_CALENDAR, APP_SUMMARY);
        assertTrue(ws.isEmpty());
    }

    // ---------- helpers ----------

    private static void assertHasCode(List<ReservationWarning> ws, Code code)
    {
        assertTrue(ws.stream().anyMatch(w -> w.code() == code),
                "expected warning with code " + code + " in " + ws);
    }

    private static Appointment appointment(String startIso, String endIso)
    {
        return new AppointmentImpl(LocalDateTime.parse(startIso), LocalDateTime.parse(endIso));
    }

    /** Minimal Reservation Proxy answering only the methods the carve-out reads. */
    private static Reservation stubReservation(String id, String name, String templateAnnotation,
                                               Appointment[] appointments, int allocatableCount)
    {
        Allocatable[] allocatables = new Allocatable[allocatableCount];
        for (int i = 0; i < allocatableCount; i++)
        {
            allocatables[i] = stubAllocatable("a" + i);
        }
        Map<String, Object> map = Map.of(
                RaplaObjectAnnotations.KEY_TEMPLATE, templateAnnotation == null ? "" : templateAnnotation);
        return (Reservation) Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":            return id;
                        case "getName":          return name;
                        case "getAnnotation":    return templateAnnotation;
                        case "getAppointments":  return appointments;
                        case "getAllocatables":  return allocatables;
                        case "equals":           return proxy == args[0];
                        case "hashCode":         return System.identityHashCode(proxy);
                        case "toString":         return "StubReservation[" + id + "]";
                        default: return null;
                    }
                });
    }

    private static Allocatable stubAllocatable(String id)
    {
        return (Allocatable) Proxy.newProxyInstance(
                Allocatable.class.getClassLoader(),
                new Class[] { Allocatable.class },
                (proxy, method, args) -> "getId".equals(method.getName()) ? id : null);
    }
}
