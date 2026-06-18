package org.rapla.entities.dynamictype;

import org.junit.jupiter.api.Test;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Internal rapla types (anonymousEvent, unresolvedResource, period, …) are
 * identified by their canonical {@code rapla:} marker. The id is the immutable,
 * reliable carrier of that marker — the key can be mutated (an old
 * GraphqlKeyMigration sanitized {@code rapla:anonymousEvent} → {@code
 * rapla_anonymousEvent} and persisted it). {@link DynamicTypeImpl#isInternal()}
 * must still recognise such a type, else it leaks into the API / GraphQL SDL.
 */
class DynamicTypeInternalMarkerTest
{
    private static DynamicTypeImpl type(String id, String key)
    {
        DynamicTypeImpl dt = new DynamicTypeImpl();
        dt.setId(id);
        dt.setKey(key);
        return dt;
    }

    @Test
    void freshInternalTypeIsInternal()
    {
        assertTrue(type("rapla:anonymousEvent", "rapla:anonymousEvent").isInternal());
    }

    @Test
    void keySanitizedInternalTypeStillInternalViaId()
    {
        // key mangled by an old migration, id keeps the canonical marker
        assertTrue(type("rapla:anonymousEvent", "rapla_anonymousEvent").isInternal(),
                "internal type must be recognised by its immutable rapla: id even when the key was sanitized");
    }

    @Test
    void userTypeIsNotInternal()
    {
        assertFalse(type("d26ee5af-f455-4f8a-967a-dcf53ffa5d73", "event").isInternal());
    }
}
