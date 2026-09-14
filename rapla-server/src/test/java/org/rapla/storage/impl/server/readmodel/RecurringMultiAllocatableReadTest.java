package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reproduces (and then guards) the recurrence-indexing bug found on the real store: a weekly recurring
 * appointment bound to several unrestricted allocatables is returned by the legacy {@code appointmentMap}
 * and the global window-first index, but DROPPED by the per-allocatable {@code IntervalIndex} — with no
 * mutation, just the read-model flip. Each scenario asserts legacy == per-allocatable-flip == window-first.
 */
class RecurringMultiAllocatableReadTest extends DifferentialReadSupport
{
    private static final LocalDateTime BASE_START = LocalDateTime.parse("2025-04-02T14:00:00");
    private static final LocalDateTime BASE_END   = LocalDateTime.parse("2025-04-02T16:00:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    /** A window covering the Nth weekly occurrence (week of BASE + n weeks). */
    private LocalDateTime occFrom(int n) { return BASE_START.plusWeeks(n).toLocalDate().atStartOfDay(); }
    private LocalDateTime occTo(int n)   { return occFrom(n).plusWeeks(1); }

    @Test
    void weeklyBounded_multiAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);   // 10 weekly occurrences
        store("weekly-bounded-multi", allocs, a);

        assertAllConsistent("base week", occFrom(0), occTo(0), allocs);
        assertAllConsistent("5th occurrence (2025-05-07)", occFrom(5), occTo(5), allocs);
        assertAllConsistent("last occurrence", occFrom(9), occTo(9), allocs);
        assertAllConsistent("beyond last (no occurrence)", occFrom(20), occTo(20), allocs);
        assertAllConsistent("all-time", null, null, allocs);
    }

    @Test
    void weeklyOpenEnded_multiAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weeklyOpenEnded(appt(BASE_START, BASE_END));   // number = -1 -> side-set
        store("weekly-open-multi", allocs, a);

        assertAllConsistent("base week", occFrom(0), occTo(0), allocs);
        assertAllConsistent("5th occurrence", occFrom(5), occTo(5), allocs);
        assertAllConsistent("far-future occurrence", occFrom(200), occTo(200), allocs);
    }

    @Test
    void weeklyOverCap_multiAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 60);   // > MATERIALIZE_CAP (52) -> envelope row
        store("weekly-overcap-multi", allocs, a);

        assertAllConsistent("base week", occFrom(0), occTo(0), allocs);
        assertAllConsistent("week 55 (inside, past cap)", occFrom(55), occTo(55), allocs);
        assertAllConsistent("beyond last (week 70)", occFrom(70), occTo(70), allocs);
    }

    @Test
    void singleAppointment_multiAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        store("single-multi", allocs, appt(BASE_START, BASE_END));
        assertAllConsistent("its week", occFrom(0), occTo(0), allocs);
        assertAllConsistent("other week", occFrom(3), occTo(3), allocs);
    }
}
