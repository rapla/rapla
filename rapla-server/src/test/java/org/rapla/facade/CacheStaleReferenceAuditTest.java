package org.rapla.facade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Audit tests for the server-side LocalCache to flush out stale-reference and
 * orphaned-entity bugs. Each test names the suspected defect in its
 * {@link DisplayName}; if the test currently fails, the bug is confirmed.
 *
 * <p>Tier-2 via {@link FacadeTestSupport} so we exercise the full dispatch
 * path (facade.store → operator.dispatch → cache.put), which is where a real
 * production stale-reference would surface.
 */
class CacheStaleReferenceAuditTest extends FacadeTestSupport
{
    private User admin;
    private Allocatable room;

    @BeforeEach
    void resolveFixture() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { admin = u; break; }
        }
        assertNotNull(admin, "fixture must include an admin user");

        for (Allocatable a : facade.getAllocatables())
        {
            DynamicType t = a.getClassification().getType();
            if (t != null && "room".equals(t.getKey())) { room = a; break; }
        }
        assertNotNull(room, "fixture must include at least one allocatable of type 'room'");
    }

    /**
     * Audit case 1 — "removed appointment leaks in cache".
     *
     * <p>Suspected defect: {@code LocalCache.put(Reservation)} calls
     * {@code entities.get(entity)} (an Entity object) on
     * {@code Map<String, Entity>}; that lookup always returns null because
     * the Entity object is never .equal to any String key. The
     * "remove old children before re-putting" block at lines 189–198 is
     * therefore dead code, and an appointment removed from a reservation
     * update lingers in {@code entities} forever — resolvable by id but no
     * longer attached to any reservation.
     */
    @Test
    @DisplayName("removed appointment from a stored reservation update should not be resolvable afterwards")
    void removedAppointmentDoesNotLeakInCache() throws Exception
    {
        DynamicType eventType = facade.getDynamicType("event");
        assertNotNull(eventType, "fixture must define an 'event' dynamic type");
        Reservation r = facade.newReservation(eventType.newClassification(), admin);
        Appointment a1 = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), admin);
        Appointment a2 = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 6, 2, 10, 0),
                LocalDateTime.of(2026, 6, 2, 11, 0), admin);
        r.addAppointment(a1);
        r.addAppointment(a2);
        r.addAllocatable(room);
        facade.store(r);

        ReferenceInfo<Appointment> a1Ref = a1.getReference();
        ReferenceInfo<Appointment> a2Ref = a2.getReference();
        ReferenceInfo<Reservation> rRef = r.getReference();

        // Pre-condition: both appointments resolvable through facade after store.
        assertNotNull(facade.tryResolve(a1Ref), "appointment 1 should resolve after first store");
        assertNotNull(facade.tryResolve(a2Ref), "appointment 2 should resolve after first store");

        // Edit the persisted reservation, remove appointment 2, store again.
        Reservation editable = facade.edit((Reservation) facade.tryResolve(rRef));
        Appointment a2Editable = null;
        for (Appointment app : editable.getAppointments())
        {
            if (app.getId().equals(a2Ref.getId())) { a2Editable = app; break; }
        }
        assertNotNull(a2Editable, "removable appointment must be in the editable copy");
        editable.removeAppointment(a2Editable);
        assertEquals(1, editable.getAppointments().length, "editable reservation should have 1 appointment");
        facade.store(editable);

        // Reservation still has one appointment.
        Reservation refetched = (Reservation) facade.tryResolve(rRef);
        assertNotNull(refetched);
        assertEquals(1, refetched.getAppointments().length,
                "post-store reservation should have exactly one appointment");
        assertEquals(a1Ref.getId(), refetched.getAppointments()[0].getId(),
                "the surviving appointment must be the one we kept");

        // ★ The audit assertion: the removed appointment must be gone from the cache.
        // If LocalCache.put's old-child cleanup is dead code, this returns non-null.
        Object stale = facade.tryResolve(a2Ref);
        assertNull(stale,
                "BUG: removed appointment is still resolvable through facade.tryResolve — "
              + "LocalCache.put's old-child cleanup is dead code (entities.get(Entity) on "
              + "Map<String, Entity> never matches). Stale appointment id="
              + a2Ref.getId());
    }

    /**
     * Audit case 1b — leak accumulates across multiple edit cycles.
     *
     * <p>Each "remove an appointment, add a new one, store" cycle leaves one
     * orphan in the cache. After N cycles, N orphans remain. This makes the
     * bug worse than a single-shot leak: long-lived sessions accumulate
     * O(N) stale appointment entities for every reservation that gets
     * appointment churn.
     */
    @Test
    @DisplayName("orphan appointments accumulate across multiple edit cycles")
    void orphanAppointmentsAccumulateAcrossEdits() throws Exception
    {
        DynamicType eventType = facade.getDynamicType("event");
        Reservation r = facade.newReservation(eventType.newClassification(), admin);
        Appointment seed = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 9, 1, 10, 0),
                LocalDateTime.of(2026, 9, 1, 11, 0), admin);
        r.addAppointment(seed);
        r.addAllocatable(room);
        facade.store(r);
        ReferenceInfo<Reservation> rRef = r.getReference();

        java.util.List<ReferenceInfo<Appointment>> orphanedRefs = new java.util.ArrayList<>();
        for (int cycle = 0; cycle < 3; cycle++)
        {
            Reservation editable = facade.edit((Reservation) facade.tryResolve(rRef));

            // remove the (single) existing appointment
            Appointment toRemove = editable.getAppointments()[0];
            orphanedRefs.add(toRemove.getReference());
            editable.removeAppointment(toRemove);

            // add a fresh one
            Appointment fresh = facade.newAppointmentWithUser(
                    LocalDateTime.of(2026, 9, 2 + cycle, 10, 0),
                    LocalDateTime.of(2026, 9, 2 + cycle, 11, 0), admin);
            editable.addAppointment(fresh);
            facade.store(editable);
        }

        // Each removed appointment should be gone. With the cache.put bug,
        // ALL of them remain resolvable.
        int leakCount = 0;
        for (ReferenceInfo<Appointment> ref : orphanedRefs)
        {
            if (facade.tryResolve(ref) != null) leakCount++;
        }
        assertEquals(0, leakCount,
                "BUG: " + leakCount + " of " + orphanedRefs.size()
                + " removed appointments are still resolvable. The orphan count "
                + "scales with the number of edit cycles — long-lived sessions accumulate.");
    }

    /**
     * Audit case 2 — sanity: a fully removed reservation removes its appointments.
     *
     * <p>This is the documented contract — {@code LocalCache.remove(Reservation)}
     * iterates sub-entities. If this fails, the cache's remove path is broken too.
     */
    @Test
    @DisplayName("removing a reservation also removes its appointments from the cache")
    void removingReservationCascadesToAppointments() throws Exception
    {
        DynamicType eventType = facade.getDynamicType("event");
        Reservation r = facade.newReservation(eventType.newClassification(), admin);
        Appointment a = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 7, 1, 10, 0),
                LocalDateTime.of(2026, 7, 1, 11, 0), admin);
        r.addAppointment(a);
        r.addAllocatable(room);
        facade.store(r);
        ReferenceInfo<Appointment> aRef = a.getReference();
        ReferenceInfo<Reservation> rRef = r.getReference();

        Reservation persisted = (Reservation) facade.tryResolve(rRef);
        assertNotNull(persisted);

        facade.remove(facade.edit(persisted));

        assertNull(facade.tryResolve(rRef), "reservation must be gone after remove");
        assertNull(facade.tryResolve(aRef),
                "appointment must be gone after parent reservation is removed (cascade)");
    }
}
