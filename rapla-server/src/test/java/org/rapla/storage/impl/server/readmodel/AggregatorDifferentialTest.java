package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OVER / UNDER / PARTIAL resource (aggregator) differential. Exercises bindings against MULTIPLE
 * allocatables — the union, each single allocatable, and proper subsets after restriction — and after
 * every state asserts legacy {@code appointmentMap} == per-allocatable {@code IntervalIndex} (flip on)
 * == window-first global index.
 *
 * <p>TODO: true belongs-to / package aggregator coverage (querying a PARENT allocatable and expecting
 * the CHILD-bound appointment via {@code getDependentRef} expansion) needs a fixture with linked
 * allocatables (e.g. {@code room.belongsto} CATEGORY or a package binding). The base
 * {@link DifferentialReadSupport} facade API exposes only flat {@code addAllocatable} /
 * {@code setRestrictionForAppointment}, so this class covers the reduced-but-valid multi-allocatable
 * surface: union / per-key / subset consistency under restriction and mutation.
 */
class AggregatorDifferentialTest extends DifferentialReadSupport
{
    private static final LocalDateTime BASE_START = LocalDateTime.parse("2025-04-02T14:00:00");
    private static final LocalDateTime BASE_END   = LocalDateTime.parse("2025-04-02T16:00:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    /** Window covering the Nth weekly occurrence (week of BASE + n weeks). */
    private LocalDateTime occFrom(int n) { return BASE_START.plusWeeks(n).toLocalDate().atStartOfDay(); }
    private LocalDateTime occTo(int n)   { return occFrom(n).plusWeeks(1); }

    /** Single appointment bound to three allocatables: query the union, each single key, and a subset. */
    @Test
    void singleAppointment_unionAndPerKey() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        store("agg-single-union", allocs, appt(BASE_START, BASE_END));

        assertAllConsistent("union its week", occFrom(0), occTo(0), allocs);
        // proper subsets — a per-key drop hides in the full union
        assertAllConsistent("subset {0,1}", occFrom(0), occTo(0), allocs.subList(0, 2));
        assertAllConsistent("subset {1,2}", occFrom(0), occTo(0), allocs.subList(1, 3));
        assertAllConsistent("empty week", occFrom(4), occTo(4), allocs);
        assertAllConsistent("all-time union", null, null, allocs);
    }

    /** Weekly recurrence over the union; query subsets of the binding across occurrences. */
    @Test
    void weekly_unionSubsets() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 8);
        store("agg-weekly-union", allocs, a);

        assertAllConsistent("base week union", occFrom(0), occTo(0), allocs);
        assertAllConsistent("base week subset {0}", occFrom(0), occTo(0), allocs.subList(0, 1));
        assertAllConsistent("3rd occ subset {0,2}", occFrom(3), occTo(3), List.of(allocs.get(0), allocs.get(2)));
        assertAllConsistent("last occ union", occFrom(7), occTo(7), allocs);
        assertAllConsistent("beyond last union", occFrom(20), occTo(20), allocs);
        assertAllConsistent("all-time union", null, null, allocs);
    }

    /**
     * PARTIAL binding: restrict one appointment of a multi-appointment reservation to a SUBSET of the
     * reservation's allocatables. The restricted appointment must only surface on its subset; the
     * unrestricted one on the full union. Both index paths must agree with legacy.
     */
    @Test
    void restrictOneAppointmentToSubset() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment full = appt(BASE_START, BASE_END);                                  // week 0
        Appointment other = appt(BASE_START.plusWeeks(1), BASE_END.plusWeeks(1));       // week 1
        Reservation stored = store("agg-partial", allocs, full, other);

        assertAllConsistent("pre-restrict wk0 union", occFrom(0), occTo(0), allocs);
        assertAllConsistent("pre-restrict wk1 union", occFrom(1), occTo(1), allocs);

        Reservation e = edit(stored);
        restrictAppointment(e, appointmentById(e, other.getId()), List.of(allocs.get(0)));
        storeEdit(e);

        // 'other' now bound only to alloc 0; 'full' still on all three.
        assertAllConsistent("post-restrict wk0 union", occFrom(0), occTo(0), allocs);
        assertAllConsistent("post-restrict wk1 union", occFrom(1), occTo(1), allocs);
        assertAllConsistent("post-restrict wk1 only-subset {0}", occFrom(1), occTo(1), allocs.subList(0, 1));
        assertAllConsistent("post-restrict wk1 excluded {1,2}", occFrom(1), occTo(1), allocs.subList(1, 3));
        assertAllConsistent("all-time union", null, null, allocs);
    }

    /** Restrict, then UNRESTRICT — the appointment must return to the full union on both index paths. */
    @Test
    void restrictThenUnrestrict() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 6);
        Reservation stored = store("agg-restrict-cycle", allocs, a);

        assertAllConsistent("initial union", occFrom(0), occTo(0), allocs);

        Reservation e1 = edit(stored);
        restrictAppointment(e1, appointmentById(e1, a.getId()), List.of(allocs.get(1)));
        storeEdit(e1);
        assertAllConsistent("restricted wk0 union", occFrom(0), occTo(0), allocs);
        assertAllConsistent("restricted wk0 only {1}", occFrom(0), occTo(0), allocs.subList(1, 2));
        assertAllConsistent("restricted wk2 excluded {0,2}", occFrom(2), occTo(2), List.of(allocs.get(0), allocs.get(2)));

        Reservation e2 = edit(stored);
        unrestrictAppointment(e2, appointmentById(e2, a.getId()));
        storeEdit(e2);
        assertAllConsistent("unrestricted wk0 union", occFrom(0), occTo(0), allocs);
        assertAllConsistent("unrestricted wk2 union", occFrom(2), occTo(2), allocs);
        assertAllConsistent("all-time union", null, null, allocs);
    }

    /**
     * OVER-allocation: several distinct reservations all bind the SAME allocatable in overlapping
     * windows. Querying that allocatable aggregates all of them — both index paths must match legacy.
     */
    @Test
    void overlappingReservationsOnSharedAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Allocatable shared = allocs.get(0);

        store("agg-over-A", List.of(shared, allocs.get(1)), weekly(appt(BASE_START, BASE_END), 5));
        store("agg-over-B", List.of(shared, allocs.get(2)),
                appt(BASE_START.plusHours(3), BASE_END.plusHours(3)));            // same week 0, overlaps shared
        store("agg-over-C", List.of(shared), weekly(appt(BASE_START.plusDays(1), BASE_END.plusDays(1)), 4));

        assertAllConsistent("shared-only wk0", occFrom(0), occTo(0), List.of(shared));
        assertAllConsistent("union wk0", occFrom(0), occTo(0), allocs);
        assertAllConsistent("union wk2", occFrom(2), occTo(2), allocs);
        assertAllConsistent("all-time union", null, null, allocs);
    }

    /** Add an exception to one occurrence of a union-bound weekly; the gap must propagate identically. */
    @Test
    void exceptionOnUnionBoundWeekly() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);
        Reservation stored = store("agg-exception-union", allocs, a);

        assertAllConsistent("pre-exc wk3 union", occFrom(3), occTo(3), allocs);

        Reservation e = edit(stored);
        addException(appointmentById(e, a.getId()), occFrom(3));   // drop week-3 occurrence
        storeEdit(e);

        assertAllConsistent("post-exc wk3 union (gap)", occFrom(3), occTo(3), allocs);
        assertAllConsistent("post-exc wk3 subset {0}", occFrom(3), occTo(3), allocs.subList(0, 1));
        assertAllConsistent("post-exc wk2 still present", occFrom(2), occTo(2), allocs);
        assertAllConsistent("post-exc wk4 still present", occFrom(4), occTo(4), allocs);
        assertAllConsistent("all-time union", null, null, allocs);
    }

    /** Remove a union-bound reservation — it must vanish from every allocatable on both index paths. */
    @Test
    void removeUnionBoundReservation() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Reservation stored = store("agg-remove-union", allocs, weekly(appt(BASE_START, BASE_END), 5));

        assertAllConsistent("present wk0 union", occFrom(0), occTo(0), allocs);

        removeReservation(stored);

        assertAllConsistent("removed wk0 union", occFrom(0), occTo(0), allocs);
        assertAllConsistent("removed wk2 union", occFrom(2), occTo(2), allocs);
        for (Allocatable a : allocs)
            assertAllConsistent("removed single " + a.getId(), occFrom(0), occTo(0), List.of(a));
        assertAllConsistent("removed all-time", null, null, allocs);
    }
}
