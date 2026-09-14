package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit tests for conflict / appointment-binding cleanup after entity churn.
 * Each test names the scenario in {@link DisplayName}; failures pin a real
 * stale-reference bug. Pairs with {@link CacheStaleReferenceAuditTest}
 * (LocalCache layer); these focus on the higher-level conflict tracking and
 * the appointment-binding reverse index in
 * {@code AppointmentMapClass}.
 */
class ConflictCleanupAuditTest extends FacadeTestSupport
{
    private User admin;
    private DynamicType eventType;
    private Allocatable sharedRoom;

    @BeforeEach
    void resolveFixture() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { admin = u; break; }
        }
        assertNotNull(admin);

        DynamicType[] reservationTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(reservationTypes.length > 0);
        eventType = reservationTypes[0];

        Allocatable[] all = facade.getAllocatables();
        assertTrue(all.length >= 1);
        sharedRoom = all[0];
    }

    private Reservation makeAndStore(String name, LocalDateTime start, LocalDateTime end,
                                     Allocatable allocatable) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, admin);
        Appointment a = facade.newAppointmentWithUser(start, end, admin);
        r.addAppointment(a);
        r.addAllocatable(allocatable);
        facade.store(r);
        return r;
    }

    /**
     * Audit case 8 — conflict is removed when one of its reservations is
     * deleted.
     *
     * <p>Two reservations on the same allocatable in the same window produce
     * a conflict. Deleting one reservation should drop the conflict from
     * {@code facade.getConflicts()} entirely.
     */
    @Test
    @DisplayName("conflict disappears from getConflicts() when one of its reservations is removed")
    void conflictRemovedWhenReservationRemoved() throws Exception
    {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 10, 1, 11, 0);

        Reservation r1 = makeAndStore("conflictA", start, end, sharedRoom);
        Reservation r2 = makeAndStore("conflictB", start, end, sharedRoom);

        long beforeRemove = waitFor(facade.getConflicts()).size();
        assertTrue(beforeRemove >= 1, "two overlapping reservations should produce ≥ 1 conflict");

        // Remove r2.
        facade.remove(facade.edit(r2));

        // The conflict that involved r2 must be gone.
        Collection<Conflict> after = waitFor(facade.getConflicts());
        for (Conflict c : after)
        {
            ReferenceInfo<Appointment> a1 = c.getAppointment1();
            ReferenceInfo<Appointment> a2 = c.getAppointment2();
            // Neither appointment in any remaining conflict should belong to r2.
            for (Appointment app : r2.getAppointments())
            {
                assertNull(facade.tryResolve(app.getReference()),
                        "r2's appointment " + app.getId() + " should not be resolvable after parent removal");
                assertTrue(!a1.getId().equals(app.getId()) && !a2.getId().equals(app.getId()),
                        "BUG: conflict " + c.getId() + " still references an appointment of the removed reservation");
            }
        }
    }

    /**
     * Audit case 9 — conflict is removed when one of its reservations changes
     * to a different allocatable.
     */
    @Test
    @DisplayName("conflict disappears when one reservation moves off the shared allocatable")
    void conflictRemovedWhenReservationChangesAllocatable() throws Exception
    {
        Allocatable[] all = facade.getAllocatables();
        if (all.length < 2) return; // guard small fixture
        Allocatable other = all[1];

        LocalDateTime start = LocalDateTime.of(2026, 10, 5, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 10, 5, 11, 0);

        Reservation r1 = makeAndStore("moveA", start, end, sharedRoom);
        Reservation r2 = makeAndStore("moveB", start, end, sharedRoom);

        Collection<Conflict> beforeMove = waitFor(facade.getConflicts());
        long beforeCount = beforeMove.stream()
                .filter(c -> c.getAllocatableId().getId().equals(sharedRoom.getId()))
                .count();
        assertTrue(beforeCount >= 1, "should be ≥ 1 conflict on sharedRoom");

        // Move r2 to a different allocatable.
        Reservation editable = facade.edit(r2);
        editable.removeAllocatable(sharedRoom);
        editable.addAllocatable(other);
        facade.store(editable);

        Collection<Conflict> afterMove = waitFor(facade.getConflicts());
        long afterCount = afterMove.stream()
                .filter(c -> c.getAllocatableId().getId().equals(sharedRoom.getId()))
                .count();
        assertEquals(beforeCount - 1, afterCount,
                "exactly one conflict on sharedRoom should be gone after r2 moves off it. "
              + "If this fails, ConflictFinder isn't recomputing on allocatable change. "
              + "Or AppointmentMapClass leaves a stale binding.");
    }

    /**
     * Audit case 10 — conflict is removed when its only appointment is
     * removed from the parent reservation (without removing the reservation
     * itself).
     */
    @Test
    @DisplayName("conflict disappears when its appointment is removed from the parent reservation")
    void conflictRemovedWhenAppointmentRemoved() throws Exception
    {
        LocalDateTime start = LocalDateTime.of(2026, 10, 8, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 10, 8, 11, 0);

        // r1 has TWO appointments — the second overlaps with r2.
        Classification c1 = eventType.newClassification();
        if (c1.getType().getAttribute("name") != null) c1.setValue("name", "twoAppts");
        Reservation r1 = facade.newReservation(c1, admin);
        Appointment r1NonOverlapping = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 10, 7, 10, 0),
                LocalDateTime.of(2026, 10, 7, 11, 0), admin);
        Appointment r1Overlapping = facade.newAppointmentWithUser(start, end, admin);
        r1.addAppointment(r1NonOverlapping);
        r1.addAppointment(r1Overlapping);
        r1.addAllocatable(sharedRoom);
        facade.store(r1);

        Reservation r2 = makeAndStore("apptRemoved", start, end, sharedRoom);

        long before = waitFor(facade.getConflicts()).size();
        assertTrue(before >= 1, "overlapping appointments should produce ≥ 1 conflict");

        // Re-edit r1 and remove the overlapping appointment.
        Reservation editable = facade.edit((Reservation) facade.tryResolve(r1.getReference()));
        Appointment toRemove = null;
        for (Appointment a : editable.getAppointments())
        {
            if (a.getId().equals(r1Overlapping.getId())) { toRemove = a; break; }
        }
        assertNotNull(toRemove, "the overlapping appointment must be in the editable copy");
        editable.removeAppointment(toRemove);
        facade.store(editable);

        long after = waitFor(facade.getConflicts()).size();
        assertEquals(before - 1, after,
                "exactly one conflict should be gone after the overlapping appointment is removed. "
              + "If this fails, the conflict map still references a removed appointment.");
    }
}
