package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Per-appointment restriction scenarios for the read-model differential (PRD 082/086). A reservation
 * binds several allocatables, but individual appointments are restricted to a subset; the per-allocatable
 * {@code IntervalIndex} (flip on) must agree with the legacy {@code appointmentMap} (flip off) about which
 * allocatable sees which appointment, and the window-first global index must agree on the unscoped set.
 * Each scenario asserts legacy == per-allocatable-flip == window-first after every stored state.
 */
class RestrictionsDifferentialTest extends DifferentialReadSupport
{
    private static final LocalDateTime A1_START = LocalDateTime.parse("2025-04-02T14:00:00");
    private static final LocalDateTime A1_END   = LocalDateTime.parse("2025-04-02T16:00:00");
    private static final LocalDateTime A2_START = LocalDateTime.parse("2025-04-09T10:00:00");
    private static final LocalDateTime A2_END   = LocalDateTime.parse("2025-04-09T12:00:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    private LocalDateTime weekFrom(LocalDateTime occ) { return occ.toLocalDate().atStartOfDay(); }
    private LocalDateTime weekTo(LocalDateTime occ)   { return weekFrom(occ).plusWeeks(1); }

    @Test
    void twoAppointments_eachRestrictedToOwnAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a1 = appt(A1_START, A1_END);
        Appointment a2 = appt(A2_START, A2_END);
        Reservation stored = store("two-appt-restricted", allocs, a1, a2);

        Reservation e = edit(stored);
        restrictAppointment(e, appointmentById(e, a1.getId()), List.of(allocs.get(0)));
        restrictAppointment(e, appointmentById(e, a2.getId()), List.of(allocs.get(1)));
        storeEdit(e);

        assertAllConsistent("a1 week (restricted to alloc0)", weekFrom(A1_START), weekTo(A1_START), allocs);
        assertAllConsistent("a2 week (restricted to alloc1)", weekFrom(A2_START), weekTo(A2_START), allocs);
        assertAllConsistent("all-time", null, null, allocs);
        assertAllConsistent("empty window (no occurrence)",
                LocalDateTime.parse("2030-01-01T00:00:00"), LocalDateTime.parse("2030-01-08T00:00:00"), allocs);
    }

    @Test
    void singleAppointment_restrictedToSubset() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = appt(A1_START, A1_END);
        Reservation stored = store("single-restricted-subset", allocs, a);

        Reservation e = edit(stored);
        restrictAppointment(e, appointmentById(e, a.getId()), List.of(allocs.get(0), allocs.get(2)));
        storeEdit(e);

        assertAllConsistent("its week", weekFrom(A1_START), weekTo(A1_START), allocs);
        assertAllConsistent("all-time", null, null, allocs);
    }

    @Test
    void appointmentRestrictedToAllocatable_notInQueryWindow() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = appt(A1_START, A1_END);
        Reservation stored = store("restricted-outside-window", allocs, a);

        Reservation e = edit(stored);
        restrictAppointment(e, appointmentById(e, a.getId()), List.of(allocs.get(2)));
        storeEdit(e);

        // query window has the occurrence but scope deliberately excludes the bound allocatable
        assertScopedConsistent("scope excludes bound alloc",
                weekFrom(A1_START), weekTo(A1_START), List.of(allocs.get(0), allocs.get(1)));
        // and the bound allocatable alone must see it
        assertScopedConsistent("only bound alloc",
                weekFrom(A1_START), weekTo(A1_START), List.of(allocs.get(2)));
        assertAllConsistent("full scope its week", weekFrom(A1_START), weekTo(A1_START), allocs);
        assertGlobalConsistent("global its week", weekFrom(A1_START), weekTo(A1_START));
    }

    @Test
    void weeklyRepeating_restrictedToOneAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(A1_START, A1_END), 10);
        Reservation stored = store("weekly-restricted", allocs, a);

        Reservation e = edit(stored);
        restrictAppointment(e, appointmentById(e, a.getId()), List.of(allocs.get(1)));
        storeEdit(e);

        assertAllConsistent("base week", weekFrom(A1_START), weekTo(A1_START), allocs);
        assertAllConsistent("5th occurrence", weekFrom(A1_START.plusWeeks(5)), weekTo(A1_START.plusWeeks(5)), allocs);
        assertAllConsistent("last occurrence", weekFrom(A1_START.plusWeeks(9)), weekTo(A1_START.plusWeeks(9)), allocs);
        assertAllConsistent("beyond last (no occurrence)", weekFrom(A1_START.plusWeeks(20)), weekTo(A1_START.plusWeeks(20)), allocs);
        assertAllConsistent("all-time", null, null, allocs);
    }

    @Test
    void weeklyRepeating_restrictedThenUnrestricted() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(A1_START, A1_END), 10);
        Reservation stored = store("weekly-restrict-toggle", allocs, a);

        Reservation e1 = edit(stored);
        restrictAppointment(e1, appointmentById(e1, a.getId()), List.of(allocs.get(0)));
        storeEdit(e1);
        assertAllConsistent("after restrict to alloc0", weekFrom(A1_START), weekTo(A1_START), allocs);
        assertAllConsistent("after restrict to alloc0 (week5)", weekFrom(A1_START.plusWeeks(5)), weekTo(A1_START.plusWeeks(5)), allocs);

        Reservation e2 = edit(stored);
        unrestrictAppointment(e2, appointmentById(e2, a.getId()));
        storeEdit(e2);
        assertAllConsistent("after unrestrict", weekFrom(A1_START), weekTo(A1_START), allocs);
        assertAllConsistent("after unrestrict (week5)", weekFrom(A1_START.plusWeeks(5)), weekTo(A1_START.plusWeeks(5)), allocs);
        assertAllConsistent("after unrestrict (all-time)", null, null, allocs);
    }

    @Test
    void twoAppointments_oneRestrictedOneUnrestricted() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a1 = weekly(appt(A1_START, A1_END), 8);
        Appointment a2 = appt(A2_START, A2_END);
        Reservation stored = store("mixed-restriction", allocs, a1, a2);

        Reservation e = edit(stored);
        restrictAppointment(e, appointmentById(e, a1.getId()), List.of(allocs.get(2)));
        storeEdit(e);

        assertAllConsistent("a1 base week (restricted)", weekFrom(A1_START), weekTo(A1_START), allocs);
        assertAllConsistent("a1 week3 (restricted)", weekFrom(A1_START.plusWeeks(3)), weekTo(A1_START.plusWeeks(3)), allocs);
        assertAllConsistent("a2 week (unrestricted)", weekFrom(A2_START), weekTo(A2_START), allocs);
        assertAllConsistent("all-time", null, null, allocs);
    }
}
