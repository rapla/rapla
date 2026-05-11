package org.rapla.facade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tier-2 audit: verifies the dispatch path releases the in-memory writeLock
 * even when validation/preprocess throws. If it leaks, the very next call
 * that needs the writeLock would block until the 60-second timeout fires
 * with {@code RaplaSynchronizationException}. This test asserts that pattern
 * doesn't happen.
 */
class DispatchLockReleaseAuditTest extends FacadeTestSupport
{
    private User admin;
    private DynamicType eventType;
    private Allocatable room;

    @BeforeEach
    void resolveFixture() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { admin = u; break; }
        }
        assertNotNull(admin);

        DynamicType[] types = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        eventType = types[0];

        Allocatable[] all = facade.getAllocatables();
        assertNotNull(all);
        room = all[0];
    }

    /**
     * Audit case 11 — dispatch releases writeLock when an attempted
     * Appointment-direct-remove triggers the preprocess guard. If the lock
     * leaked, the follow-up store would either block 60 s and throw
     * {@code RaplaSynchronizationException} (timeout) or hang the test thread.
     */
    @Test
    @DisplayName("writeLock is released when storeAndRemove rejects an Appointment-direct-remove request")
    void writeLockReleasedAfterValidationFailure() throws Exception
    {
        // First: prime by creating a regular reservation. Establishes baseline.
        Reservation r1 = facade.newReservation(eventType.newClassification(), admin);
        Appointment a = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 11, 1, 10, 0),
                LocalDateTime.of(2026, 11, 1, 11, 0), admin);
        r1.addAppointment(a);
        r1.addAllocatable(room);
        facade.store(r1);

        ReferenceInfo<Appointment> appRef = a.getReference();

        // Now try the illegal direct-remove of an Appointment. createUpdateEvent
        // rejects this with RaplaException ("error.remove_object"), but it
        // happens AFTER the writeLock acquisition? No — actually before, because
        // createUpdateEvent runs before dispatch. Either way, dispatch's lock
        // contract must hold: after the throw, the next legitimate op must
        // proceed without timing out.
        org.rapla.storage.StorageOperator op = facade.getOperator();
        assertThrows(RaplaException.class, () -> {
            op.storeAndRemove(
                    Collections.<org.rapla.entities.Entity>emptyList(),
                    Collections.singletonList(appRef),
                    admin, false);
        }, "removing an Appointment directly must throw");

        // Now perform a legitimate store. If the lock leaked, this either
        // hangs or times out with RaplaSynchronizationException after 60s.
        // The test framework's overall timeout would catch a true hang;
        // setting a per-method shorter ceiling here:
        long t0 = System.currentTimeMillis();

        Reservation r2 = facade.newReservation(eventType.newClassification(), admin);
        Appointment b = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 11, 2, 10, 0),
                LocalDateTime.of(2026, 11, 2, 11, 0), admin);
        r2.addAppointment(b);
        r2.addAllocatable(room);
        facade.store(r2);

        long elapsed = System.currentTimeMillis() - t0;
        if (elapsed > 5000)
        {
            throw new AssertionError("BUG: follow-up store took " + elapsed + " ms — "
                  + "writeLock likely leaked after the previous validation failure");
        }

        // Also verify: read-only query still works (reads block on a held writeLock).
        assertEquals(2, countTwoReservationsExist(r1.getReference(), r2.getReference()));
    }

    private int countTwoReservationsExist(ReferenceInfo<Reservation> r1, ReferenceInfo<Reservation> r2)
    {
        int found = 0;
        if (facade.tryResolve(r1) != null) found++;
        if (facade.tryResolve(r2) != null) found++;
        return found;
    }
}
