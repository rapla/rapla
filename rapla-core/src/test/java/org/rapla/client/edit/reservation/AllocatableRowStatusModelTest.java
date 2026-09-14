package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.reservation.AllocatableRowStatusModel.Inputs;
import org.rapla.client.edit.reservation.AllocatableRowStatusModel.Status;
import org.rapla.client.edit.reservation.AllocationConflictModel.AllocationOutcome;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.PermissionController;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier-1 coverage of {@link AllocatableRowStatusModel} (PRD 023 Phase 6a).
 * Branch-by-branch checks of the icon-category decision tree:
 * AVAILABLE / NOT_ALWAYS_AVAILABLE / REQUEST / CONFLICT / FORBIDDEN.
 */
class AllocatableRowStatusModelTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    // ---------- AVAILABLE branches ----------

    @Test
    void zeroConflictsIsAvailable()
    {
        Inputs in = inputsBuilder()
                .withOutcome(outcome(2, 0, 0, null))
                .build();
        assertEquals(Status.AVAILABLE, AllocatableRowStatusModel.statusOf(in));
    }

    @Test
    void onlyPermissionConflictsAreSameAsAvailableWhenCountsAlign()
    {
        // Branch: permissionConflictCount == conflictCount → AVAILABLE (the
        // "only-permission-issue but actually fine" branch). Reached when
        // checkRestrictions=true and partial conflicts.
        Inputs in = inputsBuilder()
                .withOutcome(outcome(3, 1, 1, null))   // 1 conflict, all are permission-only
                .checkRestrictions(true)
                .build();
        assertEquals(Status.AVAILABLE, AllocatableRowStatusModel.statusOf(in));
    }

    // ---------- NOT_ALWAYS_AVAILABLE ----------

    @Test
    void partialConflictWithCheckRestrictionsFallsThroughToNotAlwaysAvailable()
    {
        // Partial conflicts (some appointments OK, some not) + checkRestrictions=true
        // + no permission-only inflation → final fallback branch (no binding
        // intersection) returns NOT_ALWAYS_AVAILABLE.
        Inputs in = inputsBuilder()
                .withOutcome(outcome(3, 1, 0, null))
                .checkRestrictions(true)
                .build();
        // Restriction list is empty since the stub reservation has no restrictions
        // → falls through to NOT_ALWAYS_AVAILABLE
        assertEquals(Status.NOT_ALWAYS_AVAILABLE, AllocatableRowStatusModel.statusOf(in));
    }

    @Test
    void partialConflictWithoutCheckRestrictionsIsNotAlwaysAvailable()
    {
        Inputs in = inputsBuilder()
                .withOutcome(outcome(3, 1, 0, null))
                .checkRestrictions(false)
                .build();
        assertEquals(Status.NOT_ALWAYS_AVAILABLE, AllocatableRowStatusModel.statusOf(in));
    }

    // ---------- REQUEST ----------

    @Test
    void allConflictAllPermissionDeniedWithCanRequestIsRequest()
    {
        // Branch: conflictCount == total AND conflictCount == permissionConflictCount
        // AND !checkRestrictions AND canRequest → REQUEST
        Inputs in = inputsBuilder()
                .withOutcome(outcome(2, 2, 2, null))
                .checkRestrictions(false)
                .permissionController(permController(false, true, false))   // canRequest=true
                .build();
        assertEquals(Status.REQUEST, AllocatableRowStatusModel.statusOf(in));
    }

    @Test
    void checkRestrictionsRequestOnlyAggregateRequestedIsRequest()
    {
        // Branch: checkRestrictions=true, isRequestOnly=true, aggregateRequestStatus=REQUESTED → REQUEST
        Inputs in = inputsBuilder()
                .withOutcome(outcome(3, 1, 0, RequestStatus.REQUESTED))
                .checkRestrictions(true)
                .permissionController(permController(true, true, false))   // canRequest, isRequestOnly
                .build();
        assertEquals(Status.REQUEST, AllocatableRowStatusModel.statusOf(in));
    }

    // ---------- CONFLICT ----------

    @Test
    void allConflictWithMixedPermissionIsConflict()
    {
        // Branch: conflictCount == total AND conflictCount != permissionConflictCount → CONFLICT
        Inputs in = inputsBuilder()
                .withOutcome(outcome(2, 2, 1, null))
                .build();
        assertEquals(Status.CONFLICT, AllocatableRowStatusModel.statusOf(in));
    }

    // ---------- FORBIDDEN ----------

    @Test
    void allConflictAllPermissionDeniedWithoutCanRequestIsForbidden()
    {
        Inputs in = inputsBuilder()
                .withOutcome(outcome(2, 2, 2, null))
                .checkRestrictions(false)
                .permissionController(permController(false, false, false))
                .build();
        assertEquals(Status.FORBIDDEN, AllocatableRowStatusModel.statusOf(in));
    }

    // ---------- hasPermissionToAllocate ----------

    @Test
    void hasPermissionWithNoOriginalsChecksCanAllocateTimeWindow()
    {
        Appointment app = stubAppointment("2026-06-01T09:00", "2026-06-01T10:00");
        Allocatable alloc = stubAllocatable("a1");
        User user = stubUser("u");
        PermissionController pc = permController(false, false, true);  // canAllocate(start,end)=true

        boolean result = AllocatableRowStatusModel.hasPermissionToAllocate(
                app, alloc, user, List.of(), pc, TODAY);
        org.junit.jupiter.api.Assertions.assertTrue(result);
    }

    @Test
    void hasPermissionWithNoOriginalsDeniedReturnsFalse()
    {
        Appointment app = stubAppointment("2026-06-01T09:00", "2026-06-01T10:00");
        Allocatable alloc = stubAllocatable("a1");
        User user = stubUser("u");
        PermissionController pc = permController(false, false, false);

        boolean result = AllocatableRowStatusModel.hasPermissionToAllocate(
                app, alloc, user, List.of(), pc, TODAY);
        org.junit.jupiter.api.Assertions.assertFalse(result);
    }

    // ---------- helpers ----------

    private InputsBuilder inputsBuilder()
    {
        return new InputsBuilder();
    }

    /** Convenience builder so tests don't list all 10 fields. */
    private static final class InputsBuilder
    {
        private Allocatable allocatable = stubAllocatable("alloc-1");
        private AllocationOutcome outcome = outcome(2, 0, 0, null);
        private Appointment[] appointments = new Appointment[] {
                stubAppointment("2026-06-01T09:00", "2026-06-01T10:00"),
                stubAppointment("2026-06-02T09:00", "2026-06-02T10:00")
        };
        private Collection<Reservation> mutableReservations = List.of(stubReservation("R1"));
        private Collection<Reservation> originalReservations = List.of();
        private Map<ReferenceInfo<Allocatable>, Collection<Appointment>> bindings = Map.of();
        private PermissionController permController = permController(false, false, true);
        private User user = stubUser("u");
        private LocalDate today = TODAY;
        private boolean checkRestrictions = false;

        InputsBuilder withOutcome(AllocationOutcome o) { this.outcome = o; return this; }
        InputsBuilder checkRestrictions(boolean v) { this.checkRestrictions = v; return this; }
        InputsBuilder permissionController(PermissionController pc) { this.permController = pc; return this; }
        Inputs build()
        {
            // align appointments[] length with outcome's conflict array
            return new Inputs(allocatable, outcome, appointments,
                    mutableReservations, originalReservations, bindings,
                    permController, user, today, checkRestrictions);
        }
    }

    /** Build an AllocationOutcome with the requested totals. The boolean
     *  array length must match {@code appointmentCount}. */
    private static AllocationOutcome outcome(int appointmentCount, int conflictCount,
                                             int permissionConflictCount,
                                             RequestStatus aggregateRequestStatus)
    {
        boolean[] conflicts = new boolean[appointmentCount];
        for (int i = 0; i < conflictCount && i < appointmentCount; i++) conflicts[i] = true;
        return new AllocationOutcome(conflicts, conflictCount, permissionConflictCount, aggregateRequestStatus);
    }

    /** Subclass {@link PermissionController} with the three predicates the
     *  row-status decision tree actually consults. The full controller is
     *  unwieldy to construct; the constructor params here are inert. */
    private static PermissionController permController(boolean isRequestOnly, boolean canRequest, boolean canAllocateTimeWindow)
    {
        return new PermissionController(Set.of(), stubOperator())
        {
            @Override
            public boolean isRequestOnly(Allocatable a, User u, LocalDate today) { return isRequestOnly; }
            @Override
            public boolean canRequest(Allocatable a, User u, LocalDate today) { return canRequest; }
            @Override
            public boolean canAllocate(Allocatable a, User u, LocalDateTime s, LocalDateTime e, LocalDate today) { return canAllocateTimeWindow; }
        };
    }

    // ---------- stub builders ----------

    private static Allocatable stubAllocatable(String id)
    {
        ReferenceInfo<Allocatable> ref = new ReferenceInfo<>(id, Allocatable.class);
        return (Allocatable) Proxy.newProxyInstance(
                Allocatable.class.getClassLoader(),
                new Class[] { Allocatable.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":        return id;
                        case "getReference": return ref;
                        case "getAnnotation": return null;
                        case "equals":       return proxy == args[0];
                        case "hashCode":     return System.identityHashCode(proxy);
                        case "toString":     return "StubAllocatable[" + id + "]";
                        default: return null;
                    }
                });
    }

    private static Appointment stubAppointment(String startIso, String endIso)
    {
        LocalDateTime start = LocalDateTime.parse(startIso);
        LocalDateTime end = LocalDateTime.parse(endIso);
        return (Appointment) Proxy.newProxyInstance(
                Appointment.class.getClassLoader(),
                new Class[] { Appointment.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getStart":  return start;
                        case "getEnd":    return end;
                        case "getMaxEnd": return end;
                        case "equals":    return proxy == args[0];
                        case "hashCode":  return System.identityHashCode(proxy);
                        case "toString":  return "StubAppointment[" + startIso + "]";
                        default: return null;
                    }
                });
    }

    private static Reservation stubReservation(String id)
    {
        return (Reservation) Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":               return id;
                        case "getRestriction":      return new Appointment[0];
                        case "getAppointmentsFor":  return new Appointment[0];
                        case "hasAllocatedOn":      return false;
                        case "equals":              return proxy == args[0];
                        case "hashCode":            return System.identityHashCode(proxy);
                        case "toString":            return "StubReservation[" + id + "]";
                        default: return null;
                    }
                });
    }

    private static User stubUser(String id)
    {
        return (User) Proxy.newProxyInstance(
                User.class.getClassLoader(),
                new Class[] { User.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":    return id;
                        case "isAdmin":  return false;
                        case "equals":   return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "StubUser[" + id + "]";
                        default: return null;
                    }
                });
    }

    private static org.rapla.storage.StorageOperator stubOperator()
    {
        return (org.rapla.storage.StorageOperator) Proxy.newProxyInstance(
                org.rapla.storage.StorageOperator.class.getClassLoader(),
                new Class[] { org.rapla.storage.StorageOperator.class },
                (proxy, method, args) -> null);
    }
}
