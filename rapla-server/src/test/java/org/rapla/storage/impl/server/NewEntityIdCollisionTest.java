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
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 pin for PRD 056 §9 {@code checkIdIntegrity} check #1 — a NEW entity
 * (declared via create-intent) whose id already resolves to a persistent
 * entity must be rejected with an id-collision error at the dispatch choke
 * point. Without this guard, create-with-existing-id is a silent overwrite
 * ({@code checkVersions} only rejects the stale direction).
 *
 * <p>Create-intent travels via two carriers: transient
 * {@code SimpleEntity.isNew} (set in {@code FacadeImpl.setNew}, the sole
 * new-entity funnel) and serialized {@code UpdateEvent.createReferences}
 * (populated in {@code AbstractCachableOperator.createUpdateEvent}).
 * Fail-open: entities without a create marker keep upsert-by-id semantics.
 */
class NewEntityIdCollisionTest extends FacadeTestSupport
{
    private User actingUser;
    private DynamicType eventType;
    private DynamicType resourceType;

    @BeforeEach
    void resolveFixture() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { actingUser = u; break; }
        }
        assertNotNull(actingUser, "fixture must include an admin");
        eventType = facade.getDynamicTypes(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        resourceType = facade.getDynamicTypes(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0];
    }

    private Reservation makeReservation(String name, LocalDateTime start, LocalDateTime end) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, actingUser);
        Appointment a = facade.newAppointmentWithUser(start, end, actingUser);
        r.addAppointment(a);
        return r;
    }

    @Test
    void newReservationReusingExistingIdRejected() throws Exception
    {
        Reservation existing = makeReservation("ID-COLLISION-SRC",
                LocalDateTime.parse("2032-01-10T09:00"),
                LocalDateTime.parse("2032-01-10T11:00"));
        facade.store(existing);

        Reservation intruder = makeReservation("ID-COLLISION-NEW",
                LocalDateTime.parse("2032-01-11T09:00"),
                LocalDateTime.parse("2032-01-11T11:00"));
        ((SimpleEntity) intruder).setId(existing.getId());

        RaplaException ex = assertThrows(RaplaException.class, () -> facade.store(intruder),
                "a NEW reservation reusing a persistent id must be rejected (silent overwrite)");
        assertTrue(ex.getMessage().contains(existing.getId()),
                () -> "collision message should name the client's own id; got " + ex.getMessage());
    }

    @Test
    void newAllocatableReusingExistingIdRejected() throws Exception
    {
        Allocatable existing = facade.newAllocatable(resourceType.newClassification(), actingUser);
        facade.store(existing);

        Allocatable intruder = facade.newAllocatable(resourceType.newClassification(), actingUser);
        ((SimpleEntity) intruder).setId(existing.getId());

        assertThrows(RaplaException.class, () -> facade.store(intruder),
                "a NEW allocatable reusing a persistent id must be rejected");
    }

    @Test
    void editedReservationKeepsUpsertSemantics() throws Exception
    {
        // Fail-open: an edit clone carries no create-intent — storing it with
        // its (persistent) id is a normal update, never a collision.
        Reservation existing = makeReservation("ID-COLLISION-EDIT",
                LocalDateTime.parse("2032-02-10T09:00"),
                LocalDateTime.parse("2032-02-10T11:00"));
        facade.store(existing);

        Reservation editable = facade.edit(existing);
        editable.getClassification().setValue("name", "ID-COLLISION-EDIT-renamed");
        facade.store(editable);
    }

    @Test
    void freshNewEntityStoresNormally() throws Exception
    {
        // A NEW reservation with its own fresh id must store — create-intent
        // only rejects when the id already resolves.
        Reservation fresh = makeReservation("ID-COLLISION-FRESH",
                LocalDateTime.parse("2032-03-10T09:00"),
                LocalDateTime.parse("2032-03-10T11:00"));
        facade.store(fresh);
    }
}
