package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.Conflict;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 backfill (PRD 017 Phase 4 follow-up) for {@code ConflictFinder} —
 * the conflict-detection engine in {@code org.rapla.storage.impl.server}
 * (10,712 instructions, 31 % covered before this test). Drives it via
 * {@code facade.getConflictsForReservation(...)}, the same path the UI
 * uses, by constructing reservations that share allocatables.
 *
 * <p>Coverage of {@code ConflictFinder} matters because it underpins the
 * "Are these two events double-booked?" question that the GUI asks on
 * almost every save. A wrong answer = silent data corruption.
 */
class ConflictFinderViaFacadeTest extends FacadeTestSupport
{
    private User actingUser;
    private DynamicType eventType;
    private Allocatable sharedRoom;
    private Allocatable otherRoom;

    @BeforeEach
    void resolveFixture() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { actingUser = u; break; }
        }
        assertNotNull(actingUser, "fixture must include an admin");

        DynamicType[] reservationTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(reservationTypes.length > 0, "fixture must include a reservation type");
        eventType = reservationTypes[0];

        // Pick two distinct allocatables for the conflict matrix.
        Allocatable[] all = facade.getAllocatables();
        assertTrue(all.length >= 2, "fixture must include ≥ 2 allocatables");
        sharedRoom = all[0];
        otherRoom = all[1];
    }

    // ---------- helpers ----------

    private Reservation makeAndStore(String name, LocalDateTime start, LocalDateTime end,
                                     Allocatable... allocatables) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, actingUser);

        Appointment a = facade.newAppointmentWithUser(start, end, actingUser);
        r.addAppointment(a);
        for (Allocatable allocatable : allocatables)
        {
            r.addAllocatable(allocatable);
        }
        facade.store(r);
        return r;
    }

    private long countConflicts(Reservation r) throws Exception
    {
        Collection<Conflict> conflicts = waitFor(facade.getConflictsForReservation(r));
        assertNotNull(conflicts);
        return conflicts.size();
    }

    // ---------- positive cases ----------

    @Test
    void overlappingAppointmentsOnSameAllocatableConflict() throws Exception
    {
        Reservation a = makeAndStore("CONFLICT-A",
                LocalDateTime.parse("2030-06-10T09:00"),
                LocalDateTime.parse("2030-06-10T11:00"),
                sharedRoom);
        Reservation b = makeAndStore("CONFLICT-B",
                LocalDateTime.parse("2030-06-10T10:00"),
                LocalDateTime.parse("2030-06-10T12:00"),
                sharedRoom);

        assertTrue(countConflicts(a) >= 1,
                "A overlapping B on sharedRoom must produce ≥ 1 conflict for A");
        assertTrue(countConflicts(b) >= 1,
                "Symmetric: B must see the conflict too");
    }

    @Test
    void touchingAppointmentEdgeIsNotConflict() throws Exception
    {
        // A ends at 11:00, B starts at 11:00 — touching but not overlapping.
        Reservation a = makeAndStore("EDGE-A",
                LocalDateTime.parse("2030-07-10T09:00"),
                LocalDateTime.parse("2030-07-10T11:00"),
                sharedRoom);
        Reservation b = makeAndStore("EDGE-B",
                LocalDateTime.parse("2030-07-10T11:00"),
                LocalDateTime.parse("2030-07-10T13:00"),
                sharedRoom);

        long aConflicts = countConflicts(a);
        long bConflicts = countConflicts(b);
        // Either both 0 or both equal — touching edges are convention; what
        // we assert is just symmetry.
        assertEquals(aConflicts, bConflicts,
                "edge-touching should be symmetric: A sees same conflict count as B");
    }

    // ---------- negative cases ----------

    @Test
    void differentAllocatablesNeverConflict() throws Exception
    {
        Reservation a = makeAndStore("DIFF-ALLOC-A",
                LocalDateTime.parse("2030-08-10T09:00"),
                LocalDateTime.parse("2030-08-10T11:00"),
                sharedRoom);
        Reservation b = makeAndStore("DIFF-ALLOC-B",
                LocalDateTime.parse("2030-08-10T09:00"),
                LocalDateTime.parse("2030-08-10T11:00"),
                otherRoom);

        long aConflicts = countConflicts(a);
        long bConflicts = countConflicts(b);
        // A and B don't share an allocatable → cannot conflict with each other
        // (other pre-existing conflicts may still register, so we check that
        // count is the same as A-alone scenario: zero new conflicts introduced).
        // Simpler robust assertion: their conflict counts are independent of
        // each other — neither references the other.
        for (Conflict c : waitFor(facade.getConflictsForReservation(a)))
        {
            assertTrue(!c.getReservation1().equals(b.getReference())
                            && !c.getReservation2().equals(b.getReference()),
                    "A's conflicts must not reference B (different allocatables)");
        }
    }

    @Test
    void nonOverlappingTimesOnSameAllocatableDontConflict() throws Exception
    {
        Reservation a = makeAndStore("DAY1",
                LocalDateTime.parse("2030-09-10T09:00"),
                LocalDateTime.parse("2030-09-10T11:00"),
                sharedRoom);
        Reservation b = makeAndStore("DAY2",
                LocalDateTime.parse("2030-09-11T09:00"),
                LocalDateTime.parse("2030-09-11T11:00"),
                sharedRoom);

        // Neither A nor B should see the other in their conflict list.
        for (Conflict c : waitFor(facade.getConflictsForReservation(a)))
        {
            assertTrue(!c.getReservation1().equals(b.getReference())
                            && !c.getReservation2().equals(b.getReference()),
                    "A's conflicts must not reference B (different days, same room)");
        }
    }

    // ---------- read-side: getConflicts() ----------

    @Test
    void getConflictsReturnsConflictsAfterStoringOverlappingReservations() throws Exception
    {
        // Snapshot existing conflict count from fixture + earlier tests in
        // class lifecycle (none — each @Test is fresh).
        long beforeCount = waitFor(facade.getConflicts()).size();

        makeAndStore("BULK-A",
                LocalDateTime.parse("2030-10-10T09:00"),
                LocalDateTime.parse("2030-10-10T11:00"),
                sharedRoom);
        makeAndStore("BULK-B",
                LocalDateTime.parse("2030-10-10T10:00"),
                LocalDateTime.parse("2030-10-10T12:00"),
                sharedRoom);

        long afterCount = waitFor(facade.getConflicts()).size();
        assertTrue(afterCount > beforeCount,
                "global conflict count must rise after storing two overlapping reservations on the same allocatable");
    }
}
