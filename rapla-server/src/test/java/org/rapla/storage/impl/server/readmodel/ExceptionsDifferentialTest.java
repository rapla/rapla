package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Differential read-model coverage for repeating appointments with EXCEPTIONS. After each mutation the
 * three read paths (legacy appointmentMap, per-allocatable IntervalIndex flip-on, window-first global
 * index) must agree: an excepted day must be ABSENT from every path; a non-excepted occurrence must be
 * PRESENT in every path. Covers single-allocatable and multi-allocatable (someAllocatables(3)) bindings,
 * multiple exceptions, an exception that empties a window, exceptions on a multi-allocatable repeating,
 * and an exception on one of two appointments in the same reservation.
 */
class ExceptionsDifferentialTest extends DifferentialReadSupport
{
    private static final LocalDateTime BASE_START = LocalDateTime.parse("2025-04-02T14:00:00");
    private static final LocalDateTime BASE_END   = LocalDateTime.parse("2025-04-02T16:00:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    /** A window covering the Nth weekly occurrence (week of BASE + n weeks). */
    private LocalDateTime weekFrom(int n) { return BASE_START.plusWeeks(n).toLocalDate().atStartOfDay(); }
    private LocalDateTime weekTo(int n)   { return weekFrom(n).plusWeeks(1); }

    /** A window covering the Nth daily occurrence (day of BASE + n days). */
    private LocalDateTime dayFrom(int n) { return BASE_START.plusDays(n).toLocalDate().atStartOfDay(); }
    private LocalDateTime dayTo(int n)   { return dayFrom(n).plusDays(1); }

    /** The exception day for the Nth weekly occurrence (the occurrence's start instant). */
    private LocalDateTime weeklyExceptionDay(int n) { return BASE_START.plusWeeks(n); }

    private LocalDateTime dailyExceptionDay(int n) { return BASE_START.plusDays(n); }

    @Test
    void weekly_singleException_singleAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(1);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);
        Reservation r = store("weekly-one-exc-single", allocs, a);

        assertAllConsistent("before exception, week 3 present", weekFrom(3), weekTo(3), allocs);

        Reservation e = edit(r);
        addException(appointmentById(e, a.getId()), weeklyExceptionDay(3));
        storeEdit(e);

        assertAllConsistent("excepted day (week 3) absent", weekFrom(3), weekTo(3), allocs);
        assertAllConsistent("neighbouring occurrence (week 2) present", weekFrom(2), weekTo(2), allocs);
        assertAllConsistent("neighbouring occurrence (week 4) present", weekFrom(4), weekTo(4), allocs);
        assertAllConsistent("all-time after exception", null, null, allocs);
    }

    @Test
    void weekly_singleException_multiAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);
        Reservation r = store("weekly-one-exc-multi", allocs, a);

        assertAllConsistent("before exception, week 5 present", weekFrom(5), weekTo(5), allocs);

        Reservation e = edit(r);
        addException(appointmentById(e, a.getId()), weeklyExceptionDay(5));
        storeEdit(e);

        assertAllConsistent("excepted day (week 5) absent across 3 allocs", weekFrom(5), weekTo(5), allocs);
        assertAllConsistent("week 4 still present across 3 allocs", weekFrom(4), weekTo(4), allocs);
        assertAllConsistent("all-time after multi-alloc exception", null, null, allocs);
    }

    @Test
    void daily_multipleExceptions_singleAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(1);
        Appointment a = daily(appt(BASE_START, BASE_END), 14);
        Reservation r = store("daily-multi-exc-single", allocs, a);

        assertAllConsistent("before exceptions, day 2 present", dayFrom(2), dayTo(2), allocs);

        Reservation e = edit(r);
        Appointment ea = appointmentById(e, a.getId());
        addException(ea, dailyExceptionDay(2));
        addException(ea, dailyExceptionDay(5));
        addException(ea, dailyExceptionDay(6));
        storeEdit(e);

        assertAllConsistent("excepted day 2 absent", dayFrom(2), dayTo(2), allocs);
        assertAllConsistent("excepted day 5 absent", dayFrom(5), dayTo(5), allocs);
        assertAllConsistent("excepted day 6 absent", dayFrom(6), dayTo(6), allocs);
        assertAllConsistent("non-excepted day 3 present", dayFrom(3), dayTo(3), allocs);
        assertAllConsistent("non-excepted day 7 present", dayFrom(7), dayTo(7), allocs);
        assertAllConsistent("all-time after multiple exceptions", null, null, allocs);
    }

    @Test
    void daily_multipleExceptions_multiAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = daily(appt(BASE_START, BASE_END), 14);
        Reservation r = store("daily-multi-exc-multi", allocs, a);

        Reservation e = edit(r);
        Appointment ea = appointmentById(e, a.getId());
        addException(ea, dailyExceptionDay(1));
        addException(ea, dailyExceptionDay(3));
        storeEdit(e);

        assertAllConsistent("excepted day 1 absent across 3 allocs", dayFrom(1), dayTo(1), allocs);
        assertAllConsistent("excepted day 3 absent across 3 allocs", dayFrom(3), dayTo(3), allocs);
        assertAllConsistent("non-excepted day 2 present across 3 allocs", dayFrom(2), dayTo(2), allocs);
        assertAllConsistent("all-time after multi-alloc multiple exceptions", null, null, allocs);
    }

    @Test
    void exception_emptiesOnlyOccurrenceInWindow() throws Exception
    {
        // weekly: each week contains exactly one occurrence. Excepting week 4 must leave that window
        // with NO occurrence at all in every read path.
        List<Allocatable> allocs = someAllocatables(1);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);
        Reservation r = store("weekly-empty-window-single", allocs, a);

        assertAllConsistent("week 4 has its sole occurrence", weekFrom(4), weekTo(4), allocs);

        Reservation e = edit(r);
        addException(appointmentById(e, a.getId()), weeklyExceptionDay(4));
        storeEdit(e);

        assertAllConsistent("week 4 now empty (sole occurrence excepted)", weekFrom(4), weekTo(4), allocs);
    }

    @Test
    void exception_emptiesOnlyOccurrenceInWindow_multiAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);
        Reservation r = store("weekly-empty-window-multi", allocs, a);

        assertAllConsistent("week 6 has its sole occurrence across 3 allocs", weekFrom(6), weekTo(6), allocs);

        Reservation e = edit(r);
        addException(appointmentById(e, a.getId()), weeklyExceptionDay(6));
        storeEdit(e);

        assertAllConsistent("week 6 now empty across 3 allocs", weekFrom(6), weekTo(6), allocs);
    }

    @Test
    void exceptionOnOneOfTwoAppointments_sameReservation() throws Exception
    {
        // Two weekly appointments in one reservation: a1 starts at BASE, a2 a day later. Except a single
        // occurrence of a1 only — a2's matching-week occurrence must remain present in every read path.
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a1 = weekly(appt(BASE_START, BASE_END), 10);
        Appointment a2 = weekly(appt(BASE_START.plusDays(1), BASE_END.plusDays(1)), 10);
        Reservation r = store("two-appts-one-exc", allocs, a1, a2);

        assertAllConsistent("before exception, week 3 (both appts) present", weekFrom(3), weekTo(3), allocs);

        Reservation e = edit(r);
        addException(appointmentById(e, a1.getId()), weeklyExceptionDay(3));
        storeEdit(e);

        // week 3 window still contains a2's occurrence (and a1's is gone) — all paths must agree.
        assertAllConsistent("week 3: a1 excepted, a2 present", weekFrom(3), weekTo(3), allocs);
        assertAllConsistent("week 5: both appts present", weekFrom(5), weekTo(5), allocs);
        assertAllConsistent("all-time after one-of-two exception", null, null, allocs);
    }

    @Test
    void window_withNoOccurrence_afterException() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);
        Reservation r = store("weekly-no-occ-window", allocs, a);

        Reservation e = edit(r);
        addException(appointmentById(e, a.getId()), weeklyExceptionDay(2));
        storeEdit(e);

        // far beyond the last (bounded) occurrence — empty in every path, exception irrelevant here.
        assertAllConsistent("week 40 has no occurrence at all", weekFrom(40), weekTo(40), allocs);
        // the excepted occurrence still absent.
        assertAllConsistent("excepted week 2 absent", weekFrom(2), weekTo(2), allocs);
    }
}
