package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CONFLICT-binding differential (PRD 082/086). Drives {@code getAllAllocatableBindingsSync} under the
 * read-model flip off (legacy {@code appointmentMap} conflict scan, the ground truth) vs flip on
 * (block-index conflict path), normalizes both to
 * {@code Map<allocId, Map<queryApptId, Set<conflictingApptId>>>} restricted to the test's own
 * reservations, and asserts the two shapes are equal.
 *
 * <p>Each scenario builds a known overlap on a shared allocatable, then queries one reservation's
 * appointments against the others. The conflict must actually be present (non-empty), otherwise the
 * differential would pass vacuously. Covers single-vs-single overlap, weekly-vs-single hitting one
 * occurrence, weekly-vs-weekly sharing an occurrence slot, and the multi-allocatable case.
 */
class ConflictBindingDifferentialTest extends DifferentialReadSupport
{
    private static final LocalDateTime BASE_START = LocalDateTime.parse("2035-06-10T09:00:00");
    private static final LocalDateTime BASE_END   = LocalDateTime.parse("2035-06-10T11:00:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    /**
     * The conflict differential: legacy conflict scan (flip off) == block-index conflict path (flip on),
     * normalized to (allocId -> (queryApptId -> {conflictingApptId})) restricted to created reservations.
     * Asserts a real conflict exists first so the comparison is meaningful.
     */
    private void assertConflictConsistent(String label, List<Allocatable> allocs, List<Appointment> queryAppts) throws Exception
    {
        op.setReadModelAuthoritative(false);
        Map<String, Map<String, TreeSet<String>>> legacy =
                normalize(sync.getAllAllocatableBindingsSync(allocs, queryAppts, Collections.emptyList()));
        op.setReadModelAuthoritative(true);
        Map<String, Map<String, TreeSet<String>>> flip =
                normalize(sync.getAllAllocatableBindingsSync(allocs, queryAppts, Collections.emptyList()));
        op.setReadModelAuthoritative(false);

        boolean anyConflict = legacy.values().stream()
                .flatMap(m -> m.values().stream())
                .anyMatch(s -> !s.isEmpty());
        assertTrue(anyConflict, () -> "[" + label + "] expected a real conflict in the legacy scan, got none");

        assertEquals(legacy, flip, () -> "[" + label + "] block-index conflict bindings ≠ legacy appointmentMap scan");
    }

    /** Normalize to (allocId -> (queryApptId -> {conflictingApptId})), filtered to created reservations. */
    private Map<String, Map<String, TreeSet<String>>> normalize(
            Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> bindings)
    {
        Map<String, Map<String, TreeSet<String>>> out = new LinkedHashMap<>();
        for (Map.Entry<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> e : bindings.entrySet())
        {
            Map<String, TreeSet<String>> inner = new LinkedHashMap<>();
            for (Map.Entry<Appointment, Collection<Appointment>> qe : e.getValue().entrySet())
            {
                Appointment queryAppt = qe.getKey();
                if (!mine(queryAppt)) continue;
                TreeSet<String> conflicting = new TreeSet<>();
                for (Appointment c : qe.getValue()) if (mine(c)) conflicting.add(c.getId());
                inner.put(queryAppt.getId(), conflicting);
            }
            if (!inner.isEmpty()) out.put(e.getKey().getId(), inner);
        }
        return out;
    }

    private boolean mine(Appointment a)
    {
        Reservation r = a.getReservation();
        return r != null && created.contains(r.getId());
    }

    private static List<Appointment> appts(Reservation r)
    {
        return new ArrayList<>(List.of(r.getAppointments()));
    }

    @Test
    void twoSingleAppointments_overlapOnSharedAllocatable() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(1);
        store("single-A", allocs, appt(BASE_START, BASE_END));
        Reservation resB = store("single-B", allocs, appt(BASE_START.plusHours(1), BASE_END.plusHours(1)));

        assertConflictConsistent("single-vs-single overlap", allocs, appts(resB));
    }

    @Test
    void twoSingleAppointments_noOverlap() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(1);
        store("single-A-disjoint", allocs, appt(BASE_START, BASE_END));
        Reservation resB = store("single-B-disjoint", allocs, appt(BASE_START.plusDays(1), BASE_END.plusDays(1)));

        // No conflict expected — assertConflictConsistent requires one, so assert equality directly here.
        op.setReadModelAuthoritative(false);
        Map<String, Map<String, TreeSet<String>>> legacy =
                normalize(sync.getAllAllocatableBindingsSync(allocs, appts(resB), Collections.emptyList()));
        op.setReadModelAuthoritative(true);
        Map<String, Map<String, TreeSet<String>>> flip =
                normalize(sync.getAllAllocatableBindingsSync(allocs, appts(resB), Collections.emptyList()));
        op.setReadModelAuthoritative(false);
        assertEquals(legacy, flip, "disjoint single-vs-single: block-index conflict bindings ≠ legacy scan");
    }

    @Test
    void weeklyRepeating_vsSingle_hitsOneOccurrence() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(1);
        store("weekly-A", allocs, weekly(appt(BASE_START, BASE_END), 10));
        // Single appointment landing on the 3rd weekly occurrence's slot.
        Reservation resB = store("single-B", allocs,
                appt(BASE_START.plusWeeks(3), BASE_END.plusWeeks(3)));

        assertConflictConsistent("weekly-vs-single (3rd occurrence)", allocs, appts(resB));
    }

    @Test
    void single_vsWeeklyRepeating_queryIsRepeating() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(1);
        store("single-A", allocs, appt(BASE_START.plusWeeks(2), BASE_END.plusWeeks(2)));
        // The query side is the repeating appointment; its 2nd occurrence overlaps single-A.
        Reservation resB = store("weekly-B", allocs, weekly(appt(BASE_START, BASE_END), 10));

        assertConflictConsistent("repeating-query-vs-single", allocs, appts(resB));
    }

    @Test
    void weekly_vsWeekly_shareOneOccurrenceSlot() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(1);
        store("weekly-A", allocs, weekly(appt(BASE_START, BASE_END), 10));
        Reservation resB = store("weekly-B", allocs, weekly(appt(BASE_START.plusHours(1), BASE_END.plusHours(1)), 10));

        assertConflictConsistent("weekly-vs-weekly shared slot", allocs, appts(resB));
    }

    @Test
    void multiAllocatable_overlapOnEach() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        store("multi-A", allocs, appt(BASE_START, BASE_END));
        Reservation resB = store("multi-B", allocs, appt(BASE_START.plusHours(1), BASE_END.plusHours(1)));

        assertConflictConsistent("multi-allocatable overlap", allocs, appts(resB));
    }

    @Test
    void multiAllocatable_weeklyVsWeekly() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        store("multi-weekly-A", allocs, weekly(appt(BASE_START, BASE_END), 8));
        Reservation resB = store("multi-weekly-B", allocs, weekly(appt(BASE_START.plusHours(1), BASE_END.plusHours(1)), 8));

        assertConflictConsistent("multi-allocatable weekly-vs-weekly", allocs, appts(resB));
    }
}
