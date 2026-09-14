package org.rapla.entities.domain;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tier-1 contract test for {@link AppointmentMapping#getMatchingAllocatables} — the single
 * primitive shared by Swing's {@code RaplaBuilder} selected-match grouping and the GraphQL
 * {@code AppointmentBlock.matchedBy} field (PRD 100 Phase 5). Locks: candidate-order
 * preservation, absent-key ⇒ skip, and the null-candidates ⇒ all-mapped fallback.
 */
@RunWith(JUnit4.class)
public class AppointmentMappingMatchTest
{
    private static Allocatable alloc(String id)
    {
        AllocatableImpl a = new AllocatableImpl(null, null);
        a.setId(id);
        return a;
    }

    private static Appointment appt(String date)
    {
        LocalDateTime start = LocalDateTime.parse(date + "T09:00:00");
        return new AppointmentImpl(start, start.plusHours(1));
    }

    /** building ⊇ {a1,a2}, roomA ⊇ {a1}, roomB ⊇ {a2} — the belongsTo shape matchedBy resolves. */
    private AppointmentMapping mapping;
    private final Allocatable building = alloc("building");
    private final Allocatable roomA = alloc("roomA");
    private final Allocatable roomB = alloc("roomB");
    private final Appointment a1 = appt("2026-06-08");
    private final Appointment a2 = appt("2026-06-09");

    {
        Map<Entity, Collection<Appointment>> m = new LinkedHashMap<>();
        m.put(building, new ArrayList<>(Arrays.asList(a1, a2)));
        m.put(roomA, new ArrayList<>(List.of(a1)));
        m.put(roomB, new ArrayList<>(List.of(a2)));
        mapping = new AppointmentMapping(m);
    }

    @Test
    public void candidatesRestrictAndPreserveOrder()
    {
        assertEquals(List.of(building, roomA),
                mapping.getMatchingAllocatables(a1, Arrays.asList(building, roomA, roomB)));
        // order follows the candidate list, not the map
        assertEquals(List.of(roomA, building),
                mapping.getMatchingAllocatables(a1, Arrays.asList(roomA, roomB, building)));
    }

    @Test
    public void singleCandidateIsTheSwingSelectedAllocatableCase()
    {
        assertEquals(List.of(building), mapping.getMatchingAllocatables(a1, List.of(building)));
        // roomA is not bound to a2 → dropped, not matched
        assertEquals(List.of(), mapping.getMatchingAllocatables(a2, List.of(roomA)));
    }

    @Test
    public void absentCandidateIsSkipped()
    {
        Allocatable notMapped = alloc("unmapped");
        assertEquals(List.of(building),
                mapping.getMatchingAllocatables(a1, Arrays.asList(notMapped, building)));
    }

    @Test
    public void nullCandidatesFallsBackToAllMappedAllocatables()
    {
        List<Allocatable> matched = mapping.getMatchingAllocatables(a2, null);
        assertEquals(2, matched.size());
        assertTrue(matched.contains(building));
        assertTrue(matched.contains(roomB));
    }
}
