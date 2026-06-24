package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentMapping;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.SyncStorageOperator;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 086 Phase 4b-ii — the read-model flip. When
 * {@link LocalAbstractCachableOperator#setReadModelAuthoritative(boolean)} is
 * true, {@code queryAppointmentsSync} (plain queries) and
 * {@code getAllAllocatableBindingsSync} (conflict bindings) serve from the
 * block-index instead of the legacy {@code appointmentMap}. Both paths must be
 * behaviour-identical. This tier-2 test drives the real operator over
 * {@code testdefault.xml} and asserts legacy == flipped for both APIs.
 */
class ReadModelReadFlipEquivalenceTest extends FacadeTestSupport
{
    private static Map<String, TreeSet<String>> toAllocApptMap(
            AppointmentMapping mapping, List<Allocatable> scope)
    {
        Map<String, TreeSet<String>> out = new LinkedHashMap<>();
        for (Allocatable alloc : scope)
        {
            Collection<Appointment> appts = mapping.getAppointments(alloc);
            if (appts == null || appts.isEmpty()) continue;
            TreeSet<String> ids = new TreeSet<>();
            for (Appointment a : appts) ids.add(a.getId());
            out.put(alloc.getId(), ids);
        }
        return out;
    }

    @Test
    void queryAppointments_flipEqualsLegacy() throws Exception
    {
        LocalAbstractCachableOperator op = (LocalAbstractCachableOperator) operator;
        SyncStorageOperator sync = (SyncStorageOperator) operator;

        List<Allocatable> scope = Arrays.asList(facade.getAllocatables());
        assertTrue(scope.size() >= 1, "fixture must have allocatables");

        // Derive earliest appointment start from a single all-time query.
        op.setReadModelAuthoritative(false);
        AppointmentMapping allTime = sync.queryAppointmentsSync(
                null, scope, null, null, null, null, Collections.emptyMap(), false);
        LocalDateTime earliest = null;
        for (Appointment a : allTime.getAllAppointments())
        {
            LocalDateTime start = a.getStart();
            if (earliest == null || (start != null && start.isBefore(earliest))) earliest = start;
        }
        assertNotNull(earliest, "fixture must contain at least one appointment");

        // Windows: all-time, a one-week window from earliest, an empty far-future window.
        LocalDateTime[][] windows = new LocalDateTime[][] {
                { null, null },
                { earliest, earliest.plusWeeks(1) },
                { LocalDateTime.of(2099, 1, 1, 0, 0), LocalDateTime.of(2099, 1, 8, 0, 0) },
        };

        boolean sawNonEmpty = false;
        for (LocalDateTime[] window : windows)
        {
            LocalDateTime from = window[0];
            LocalDateTime to = window[1];

            op.setReadModelAuthoritative(false);
            AppointmentMapping legacy = sync.queryAppointmentsSync(
                    null, scope, null, from, to, null, Collections.emptyMap(), false);
            op.setReadModelAuthoritative(true);
            AppointmentMapping flipped = sync.queryAppointmentsSync(
                    null, scope, null, from, to, null, Collections.emptyMap(), false);
            op.setReadModelAuthoritative(false);

            Map<String, TreeSet<String>> legacyMap = toAllocApptMap(legacy, scope);
            Map<String, TreeSet<String>> flippedMap = toAllocApptMap(flipped, scope);

            assertEquals(legacyMap, flippedMap,
                    "flip must match legacy for window [" + from + ", " + to + "]");
            if (!legacyMap.isEmpty()) sawNonEmpty = true;
        }
        assertTrue(sawNonEmpty, "at least one window must return appointments");
    }

    @Test
    void queryAppointmentsByOwner_flipEqualsLegacy() throws Exception
    {
        LocalAbstractCachableOperator op = (LocalAbstractCachableOperator) operator;
        SyncStorageOperator sync = (SyncStorageOperator) operator;

        // Owner-scoped query (allocatables=null, owners=[u]) drives the intervalOwnerCandidates path.
        List<User> owners = Arrays.asList(facade.getUsers());
        assertTrue(owners.size() >= 1, "fixture must have users");

        op.setReadModelAuthoritative(false);
        AppointmentMapping allTime = sync.queryAppointmentsSync(
                null, null, owners, null, null, null, Collections.emptyMap(), false);
        LocalDateTime earliest = null;
        for (Appointment a : allTime.getAllAppointments())
        {
            LocalDateTime start = a.getStart();
            if (earliest == null || (start != null && start.isBefore(earliest))) earliest = start;
        }

        LocalDateTime[][] windows = (earliest == null)
                ? new LocalDateTime[][] { { null, null } }
                : new LocalDateTime[][] { { null, null }, { earliest, earliest.plusWeeks(1) } };

        for (LocalDateTime[] window : windows)
        {
            op.setReadModelAuthoritative(false);
            AppointmentMapping legacy = sync.queryAppointmentsSync(
                    null, null, owners, window[0], window[1], null, Collections.emptyMap(), false);
            op.setReadModelAuthoritative(true);
            AppointmentMapping flipped = sync.queryAppointmentsSync(
                    null, null, owners, window[0], window[1], null, Collections.emptyMap(), false);
            op.setReadModelAuthoritative(false);

            TreeSet<String> legacyIds = new TreeSet<>();
            for (Appointment a : legacy.getAllAppointments()) legacyIds.add(a.getId());
            TreeSet<String> flippedIds = new TreeSet<>();
            for (Appointment a : flipped.getAllAppointments()) flippedIds.add(a.getId());

            assertEquals(legacyIds, flippedIds,
                    "owner-query flip must match legacy for window [" + window[0] + ", " + window[1] + "]");
        }
    }

    @Test
    void conflictBindings_flipEqualsLegacy() throws Exception
    {
        LocalAbstractCachableOperator op = (LocalAbstractCachableOperator) operator;
        SyncStorageOperator sync = (SyncStorageOperator) operator;

        User actingUser = null;
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { actingUser = u; break; }
        }
        assertNotNull(actingUser, "fixture must include an admin");

        DynamicType[] reservationTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(reservationTypes.length > 0, "fixture must include a reservation type");
        DynamicType eventType = reservationTypes[0];

        Allocatable alloc = facade.getAllocatables()[0];

        // Two reservations with overlapping far-future (2035) appointments on the same allocatable.
        Reservation resA = makeAndStore(eventType, actingUser, "FLIP-CONFLICT-A",
                LocalDateTime.parse("2035-06-10T09:00"),
                LocalDateTime.parse("2035-06-10T11:00"), alloc);
        Reservation resB = makeAndStore(eventType, actingUser, "FLIP-CONFLICT-B",
                LocalDateTime.parse("2035-06-10T10:00"),
                LocalDateTime.parse("2035-06-10T12:00"), alloc);

        List<Allocatable> allocs = List.of(alloc);
        List<Appointment> queryAppts = Arrays.asList(resB.getAppointments());

        op.setReadModelAuthoritative(false);
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> legacy =
                sync.getAllAllocatableBindingsSync(allocs, queryAppts, Collections.emptyList());
        op.setReadModelAuthoritative(true);
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> flipped =
                sync.getAllAllocatableBindingsSync(allocs, queryAppts, Collections.emptyList());
        op.setReadModelAuthoritative(false);

        Map<String, Map<String, TreeSet<String>>> legacyShape = normalize(legacy);
        Map<String, Map<String, TreeSet<String>>> flippedShape = normalize(flipped);

        // The conflict must actually be present so the test is meaningful.
        boolean anyConflict = legacyShape.values().stream()
                .flatMap(m -> m.values().stream())
                .anyMatch(s -> !s.isEmpty());
        assertTrue(anyConflict, "resB's appointment must conflict with resA on the shared allocatable");

        assertEquals(legacyShape, flippedShape, "conflict-binding flip must match legacy");
    }

    private static Map<String, Map<String, TreeSet<String>>> normalize(
            Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> bindings)
    {
        Map<String, Map<String, TreeSet<String>>> out = new LinkedHashMap<>();
        for (Map.Entry<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> e : bindings.entrySet())
        {
            Map<String, TreeSet<String>> inner = new LinkedHashMap<>();
            for (Map.Entry<Appointment, Collection<Appointment>> qe : e.getValue().entrySet())
            {
                TreeSet<String> conflicting = new TreeSet<>();
                for (Appointment c : qe.getValue()) conflicting.add(c.getId());
                inner.put(qe.getKey().getId(), conflicting);
            }
            out.put(e.getKey().getId(), inner);
        }
        return out;
    }

    private Reservation makeAndStore(DynamicType eventType, User actingUser, String name,
                                     LocalDateTime start, LocalDateTime end,
                                     Allocatable... allocatables) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, actingUser);
        Appointment a = facade.newAppointmentWithUser(start, end, actingUser);
        r.addAppointment(a);
        for (Allocatable allocatable : allocatables) r.addAllocatable(allocatable);
        facade.store(r);
        return r;
    }
}
