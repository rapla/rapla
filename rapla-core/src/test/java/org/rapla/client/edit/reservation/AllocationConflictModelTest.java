package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.reservation.AllocationConflictModel.AllocationOutcome;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.PermissionController;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link AllocationConflictModel}.compute() — closes
 * the test gap noted in PRD 023 Phase 3.
 * <p>
 * Stubs {@link Allocatable} via {@code java.lang.reflect.Proxy} (avoids
 * pulling a real {@code AllocatableImpl} with its DynamicType wiring),
 * subclasses {@link PermissionController} with stub {@code canAllocate},
 * and uses real {@link AppointmentImpl} entities. Sub-second per test.
 */
class AllocationConflictModelTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    // ---------- empty / trivial cases ----------

    @Test
    void emptyAppointmentsYieldEmptyOutcome()
    {
        Allocatable alloc = stubAllocatable("alloc-1", null);
        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[0], Map.of(),
                permitAll(), null, TODAY);
        assertEquals(0, out.conflictingAppointments().length);
        assertEquals(0, out.conflictCount());
        assertEquals(0, out.permissionConflictCount());
        assertNull(out.aggregateRequestStatus());
    }

    @Test
    void noBindingsAndAllPermittedYieldsZeroConflicts()
    {
        Allocatable alloc = stubAllocatable("alloc-1", null);
        Appointment a1 = appointment(stubReservation("R1", null), "2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment(stubReservation("R2", null), "2026-06-02T09:00", "2026-06-02T10:00");

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1, a2}, Map.of(),
                permitAll(), null, TODAY);

        assertEquals(0, out.conflictCount());
        assertFalse(out.conflictingAppointments()[0]);
        assertFalse(out.conflictingAppointments()[1]);
    }

    // ---------- bindings-driven conflicts ----------

    @Test
    void bindingsListedAppointmentIsConflicting()
    {
        Allocatable alloc = stubAllocatable("alloc-1", null);
        Appointment a1 = appointment(stubReservation("R1", null), "2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment(stubReservation("R2", null), "2026-06-02T09:00", "2026-06-02T10:00");

        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> bindings = new HashMap<>();
        bindings.put(alloc.getReference(), List.of(a1));

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1, a2}, bindings,
                permitAll(), null, TODAY);

        assertTrue(out.conflictingAppointments()[0]);
        assertFalse(out.conflictingAppointments()[1]);
        assertEquals(1, out.conflictCount());
        assertEquals(0, out.permissionConflictCount());
    }

    @Test
    void holdBackConflictsAnnotationSuppressesConflictMarkers()
    {
        // KEY_CONFLICT_CREATION=ignore means the UI should NOT show conflicts
        // for this allocatable even when the bindings map says there is one.
        Allocatable alloc = stubAllocatable("alloc-1", ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
        Appointment a1 = appointment(stubReservation("R1", null), "2026-06-01T09:00", "2026-06-01T10:00");

        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> bindings = new HashMap<>();
        bindings.put(alloc.getReference(), List.of(a1));

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1}, bindings,
                permitAll(), null, TODAY);

        assertFalse(out.conflictingAppointments()[0], "hold-back annotation must mask conflict flag");
        assertEquals(0, out.conflictCount());
    }

    // ---------- permission-driven conflicts ----------

    @Test
    void permissionDeniedAppointmentIsMarkedAndCountedInBoth()
    {
        Allocatable alloc = stubAllocatable("alloc-1", null);
        Appointment a1 = appointment(stubReservation("R1", null), "2026-06-01T09:00", "2026-06-01T10:00");

        // Bindings empty → not a binding conflict → falls into the isAllowed branch.
        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1}, Map.of(),
                denyAll(), null, TODAY);

        assertTrue(out.conflictingAppointments()[0], "denied appointment must be flagged");
        assertEquals(1, out.conflictCount());
        assertEquals(1, out.permissionConflictCount(),
                "permissionConflictCount tracks denied separately even when also in conflictCount");
    }

    @Test
    void holdBackConflictsHidesFlagButStillCountsPermissionDenials()
    {
        Allocatable alloc = stubAllocatable("alloc-1", ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
        Appointment a1 = appointment(stubReservation("R1", null), "2026-06-01T09:00", "2026-06-01T10:00");

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1}, Map.of(),
                denyAll(), null, TODAY);

        // Annotation hides the visible-conflict flag…
        assertFalse(out.conflictingAppointments()[0]);
        assertEquals(0, out.conflictCount());
        // …but permission-denied counter increments unconditionally (used for separate UI surface).
        assertEquals(1, out.permissionConflictCount());
    }

    // ---------- request-status aggregation ----------

    @Test
    void aggregateRequestStatusIsFirstNonNullEncountered()
    {
        Allocatable alloc = stubAllocatable("alloc-1", null);
        Reservation r1 = stubReservation("R1", null);                         // no status
        Reservation r2 = stubReservation("R2", RequestStatus.REQUESTED);      // requested
        Reservation r3 = stubReservation("R3", RequestStatus.REQUESTED);      // also requested
        Appointment a1 = appointment(r1, "2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment(r2, "2026-06-02T09:00", "2026-06-02T10:00");
        Appointment a3 = appointment(r3, "2026-06-03T09:00", "2026-06-03T10:00");

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1, a2, a3}, Map.of(),
                permitAll(), null, TODAY);

        assertEquals(RequestStatus.REQUESTED, out.aggregateRequestStatus());
    }

    @Test
    void aggregateRequestStatusNullWhenNoneSet()
    {
        Allocatable alloc = stubAllocatable("alloc-1", null);
        Appointment a1 = appointment(stubReservation("R1", null), "2026-06-01T09:00", "2026-06-01T10:00");

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1}, Map.of(),
                permitAll(), null, TODAY);

        assertNull(out.aggregateRequestStatus());
    }

    @Test
    void transientAppointmentWithNullReservationIsHandled()
    {
        // Regression: PRD 024 P2's check-conflicts controller passes transient
        // AppointmentImpl instances built from AppointmentSpec — these have no
        // reservation parent. compute() must not NPE when getReservation()==null.
        Allocatable alloc = stubAllocatable("alloc-1", null);
        Appointment orphan = appointment(null, "2026-06-01T09:00", "2026-06-01T10:00");

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{orphan}, Map.of(),
                permitAll(), null, TODAY);

        assertNull(out.aggregateRequestStatus());
        assertFalse(out.conflictingAppointments()[0]);
    }

    // ---------- mixed cases ----------

    @Test
    void mixedBindingsAndPermissionDenialsAccumulateBoth()
    {
        Allocatable alloc = stubAllocatable("alloc-1", null);
        Appointment a1 = appointment(stubReservation("R1", null), "2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment(stubReservation("R2", null), "2026-06-02T09:00", "2026-06-02T10:00");
        Appointment a3 = appointment(stubReservation("R3", null), "2026-06-03T09:00", "2026-06-03T10:00");

        // a1 is a binding conflict; a2/a3 fall through to the permission check.
        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> bindings = new HashMap<>();
        bindings.put(alloc.getReference(), List.of(a1));

        // Only a2 is permission-denied; a3 is allowed.
        PermissionController pc = denyOnly(a2);

        AllocationOutcome out = AllocationConflictModel.compute(
                alloc, new Appointment[]{a1, a2, a3}, bindings,
                pc, null, TODAY);

        // a1 conflicts via bindings, a2 via permission, a3 is clean.
        assertTrue(out.conflictingAppointments()[0]);
        assertTrue(out.conflictingAppointments()[1]);
        assertFalse(out.conflictingAppointments()[2]);
        assertEquals(2, out.conflictCount(), "binding + permission both flag");
        assertEquals(1, out.permissionConflictCount(), "only a2 is permission-denied");
    }

    // ---------- helpers ----------

    /** Stub appointment with stored start / end / reservation. Identity-based
     *  equals (default Proxy behaviour) so the binding-map {@code contains(...)}
     *  lookup checks reference identity — the production code passes the same
     *  Appointment instance through bindings and the candidate array. */
    private static Appointment appointment(Reservation parent, String s, String e)
    {
        LocalDateTime start = LocalDateTime.parse(s);
        LocalDateTime end   = LocalDateTime.parse(e);
        return (Appointment) Proxy.newProxyInstance(
                Appointment.class.getClassLoader(),
                new Class[] { Appointment.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getReservation": return parent;
                        case "getStart":       return start;
                        case "getEnd":         return end;
                        case "getMaxEnd":      return end;
                        case "equals":         return proxy == args[0];
                        case "hashCode":       return System.identityHashCode(proxy);
                        case "toString":       return "StubAppointment[" + s + "-" + e + "]";
                        default: throw new UnsupportedOperationException(
                                "stub Appointment does not implement " + method.getName());
                    }
                });
    }

    /** PermissionController that always allows allocation. */
    private static PermissionController permitAll()
    {
        return new PermissionController(java.util.Set.of(), null)
        {
            @Override
            public boolean canAllocate(Allocatable container, User user, LocalDateTime start, LocalDateTime end, LocalDate today)
            {
                return true;
            }
        };
    }

    /** PermissionController that always denies. */
    private static PermissionController denyAll()
    {
        return new PermissionController(java.util.Set.of(), null)
        {
            @Override
            public boolean canAllocate(Allocatable container, User user, LocalDateTime start, LocalDateTime end, LocalDate today)
            {
                return false;
            }
        };
    }

    /** PermissionController that denies one specific appointment (by reference identity) and permits the rest. */
    private static PermissionController denyOnly(Appointment denied)
    {
        return new PermissionController(java.util.Set.of(), null)
        {
            @Override
            public boolean canAllocate(Allocatable container, User user, LocalDateTime start, LocalDateTime end, LocalDate today)
            {
                return !(denied.getStart().equals(start) && denied.getEnd().equals(end));
            }
        };
    }

    private static Allocatable stubAllocatable(String id, String conflictCreationAnnotation)
    {
        ReferenceInfo<Allocatable> ref = new ReferenceInfo<>(id, Allocatable.class);
        return (Allocatable) Proxy.newProxyInstance(
                Allocatable.class.getClassLoader(),
                new Class[] { Allocatable.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":          return id;
                        case "getReference":   return ref;
                        case "getAnnotation":
                            if (args.length == 1 && ResourceAnnotations.KEY_CONFLICT_CREATION.equals(args[0]))
                                return conflictCreationAnnotation;
                            return null;
                        case "equals":         return proxy == args[0];
                        case "hashCode":       return System.identityHashCode(proxy);
                        case "toString":       return "StubAllocatable[" + id + "]";
                        default: throw new UnsupportedOperationException(
                                "stub Allocatable does not implement " + method.getName());
                    }
                });
    }

    private static Reservation stubReservation(String id, RequestStatus requestStatusForAnyAllocatable)
    {
        return (Reservation) Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":            return id;
                        case "getRequestStatus": return requestStatusForAnyAllocatable;
                        case "equals":           return proxy == args[0];
                        case "hashCode":         return System.identityHashCode(proxy);
                        case "toString":         return "StubReservation[" + id + "]";
                        default: throw new UnsupportedOperationException(
                                "stub Reservation does not implement " + method.getName());
                    }
                });
    }
}
