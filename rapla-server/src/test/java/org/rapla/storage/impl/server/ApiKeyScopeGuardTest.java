package org.rapla.storage.impl.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.server.ApiKeyScopeContext;
import org.rapla.server.ApiKeyScopes;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 076 Phase 2 — the api-key data-scope is enforced at the operator write chokepoint
 * ({@code check()} via {@code dispatch}), the single seam REST + GraphQL + facade all converge
 * on (D6). Driving the enforcement here proves there is no bypass: any caller that mutates an
 * entity is subject to it.
 *
 * <p>Mapping: events (Reservation/Appointment) need {@code write_events} or {@code write_all};
 * resources (Allocatable) need {@code write_resources} or {@code write_all}; everything else
 * (User, …) needs {@code write_all}. A denial throws {@link RaplaSecurityException} — the SAME
 * type a permission denial throws, so a scoped rejection is indistinguishable from "not allowed".
 */
class ApiKeyScopeGuardTest extends FacadeTestSupport
{
    private User admin;
    private DynamicType eventType;

    @BeforeEach
    void pickFixtures() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { admin = u; break; }
        }
        assertNotNull(admin, "fixture must include an admin");
        DynamicType[] reservationTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(reservationTypes.length > 0);
        eventType = reservationTypes[0];
    }

    @AfterEach
    void clearScopeContext()
    {
        ApiKeyScopeContext.setSource(() -> null);
    }

    private void withScopes(Set<String> scopes)
    {
        ApiKeyScopeContext.setSource(() -> scopes);
    }

    private Allocatable editFirstAllocatable() throws Exception
    {
        Allocatable a = facade.getAllocatables()[0];
        return facade.edit(a);
    }

    private Reservation newReservation() throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", "scope-test");
        Reservation r = facade.newReservation(c, admin);
        Appointment ap = facade.newAppointmentWithUser(
                LocalDateTime.parse("2030-06-10T09:00"), LocalDateTime.parse("2030-06-10T11:00"), admin);
        r.addAppointment(ap);
        return r;
    }

    // ---------- resource axis ----------

    @Test
    void readOnlyKeyCannotWriteResource() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.READ));
        Allocatable edit = editFirstAllocatable();
        assertThrows(RaplaSecurityException.class,
                () -> operator.storeAndRemove(List.of(edit), List.of(), admin));
    }

    @Test
    void writeResourcesKeyCanWriteResource() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.WRITE_RESOURCES));
        Allocatable edit = editFirstAllocatable();
        operator.storeAndRemove(List.of(edit), List.of(), admin); // no throw
    }

    @Test
    void writeEventsKeyCannotWriteResource() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.WRITE_EVENTS));
        Allocatable edit = editFirstAllocatable();
        assertThrows(RaplaSecurityException.class,
                () -> operator.storeAndRemove(List.of(edit), List.of(), admin));
    }

    // ---------- event axis ----------

    @Test
    void readOnlyKeyCannotWriteEvent() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.READ));
        Reservation r = newReservation();
        assertThrows(RaplaSecurityException.class,
                () -> operator.storeAndRemove(List.of(r), List.of(), admin));
    }

    @Test
    void writeEventsKeyCanWriteEvent() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.WRITE_EVENTS));
        Reservation r = newReservation();
        operator.storeAndRemove(List.of(r), List.of(), admin); // no throw
    }

    @Test
    void writeResourcesKeyCannotWriteEvent() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.WRITE_RESOURCES));
        Reservation r = newReservation();
        assertThrows(RaplaSecurityException.class,
                () -> operator.storeAndRemove(List.of(r), List.of(), admin));
    }

    // ---------- other entities need write_all ----------

    @Test
    void writeEventsKeyCannotWriteUser() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.WRITE_EVENTS));
        User edit = facade.edit(admin);
        assertThrows(RaplaSecurityException.class,
                () -> operator.storeAndRemove(List.of(edit), List.of(), admin));
    }

    @Test
    void writeAllKeyCanWriteEverything() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.WRITE_ALL));
        Allocatable res = editFirstAllocatable();
        operator.storeAndRemove(List.of(res), List.of(), admin);
        Reservation r = newReservation();
        operator.storeAndRemove(List.of(r), List.of(), admin);
    }

    // ---------- remove path ----------

    @Test
    void readOnlyKeyCannotRemoveResource() throws Exception
    {
        withScopes(Set.of(ApiKeyScopes.READ));
        ReferenceInfo<Allocatable> ref = facade.getAllocatables()[0].getReference();
        assertThrows(RaplaSecurityException.class,
                () -> operator.storeAndRemove(List.of(), List.of(ref), admin));
    }

    // ---------- no api-key context = unrestricted (interactive session / internal thread) ----------

    @Test
    void noScopeContextIsUnrestricted() throws Exception
    {
        ApiKeyScopeContext.setSource(() -> null);
        Allocatable edit = editFirstAllocatable();
        operator.storeAndRemove(List.of(edit), List.of(), admin); // no throw
    }
}
