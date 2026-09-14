package org.rapla.storage;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.rest.JacksonObjectMapperFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the PRD 056 §9 create-intent carrier on the Swing dispatch wire: the
 * client-side {@code RemoteOperator} builds an {@link UpdateEvent} (its
 * {@code createSet} populated from the transient {@code SimpleEntity.isNew})
 * and POSTs it to {@code /dispatch} through the shared rapla mapper. If
 * {@code createSet} doesn't survive that round-trip, {@code checkIdIntegrity}
 * check #1 silently degrades to fail-open for every Swing create — no test
 * above tier 1 exercises the serialized path.
 */
class UpdateEventCreateSetWireTest
{
    private final JsonMapper mapper = JacksonObjectMapperFactory.create();

    @Test
    void createReferencesSurviveTheDispatchWire() throws Exception
    {
        UpdateEvent original = new UpdateEvent();
        original.setUserId("b0000000-1111-4222-8333-444455556666");
        ReferenceInfo<Reservation> ref = new ReferenceInfo<>(
                "e0000000-1111-4222-8333-444455556666", Reservation.class);
        original.addCreate(ref);

        String json = mapper.writeValueAsString(original);
        UpdateEvent restored = mapper.readValue(json, UpdateEvent.class);

        Collection<ReferenceInfo> createRefs = restored.getCreateReferences();
        assertEquals(1, createRefs.size(), "createSet must survive the wire");
        ReferenceInfo restoredRef = createRefs.iterator().next();
        assertEquals("e0000000-1111-4222-8333-444455556666", restoredRef.getId());
        assertEquals(Reservation.class, restoredRef.getType(),
                "localname must resolve back to the entity class");
    }

    @Test
    void eventWithoutCreateSetDeserializesToEmpty() throws Exception
    {
        // Fail-open compatibility: an event from an older client (no createSet
        // field in the JSON) must yield an empty collection, not blow up.
        UpdateEvent original = new UpdateEvent();
        original.setUserId("b0000000-1111-4222-8333-444455556666");

        String json = mapper.writeValueAsString(original);
        UpdateEvent restored = mapper.readValue(json, UpdateEvent.class);

        assertTrue(restored.getCreateReferences().isEmpty());
    }
}
