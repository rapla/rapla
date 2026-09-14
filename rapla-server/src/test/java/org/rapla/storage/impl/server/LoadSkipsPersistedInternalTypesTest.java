package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression for the internal-type load-skip. {@code corrupted-internal-type.xml}
 * is a real dev export whose internal types were sanitized by an old
 * GraphqlKeyMigration ({@code rapla:anonymousEvent} → key {@code
 * rapla_anonymousEvent}, id still {@code rapla:anonymousEvent}).
 *
 * <p>On load, such a persisted internal type must NOT overwrite the canonical
 * version created by {@code addInternalTypes} — otherwise the cache holds the
 * mangled key and {@code getDynamicType("rapla:anonymousEvent")} (a key lookup)
 * returns null, plus the type leaks into the GraphQL SDL.
 */
class LoadSkipsPersistedInternalTypesTest extends FacadeTestSupport
{
    @Override
    protected String fixtureResource()
    {
        return "/corrupted-internal-type.xml";
    }

    @Test
    void persistedInternalTypeIsDroppedSoCanonicalColonKeyWins() throws Exception
    {
        DynamicType byCanonicalKey = operator.getDynamicType("rapla:anonymousEvent");
        assertNotNull(byCanonicalKey,
                "getDynamicType by the canonical colon key must resolve (canonical addInternalTypes version)");
        assertEquals("rapla:anonymousEvent", byCanonicalKey.getKey(),
                "cache must hold the canonical colon key, not the persisted rapla_ one");

        assertNull(operator.getDynamicType("rapla_anonymousEvent"),
                "the persisted, key-sanitized internal type must have been dropped on load");
        assertNull(operator.getDynamicType("rapla_unresolvedResource"),
                "the persisted, key-sanitized internal type must have been dropped on load");
    }

    @Test
    void purgeRewritesStoreWithoutThePersistedInternalTypes() throws Exception
    {
        // The post-connect hook (here invoked directly) purges the found
        // internal-type rows from the store — for the FileOperator a saveData
        // rewrite that omits internal types.
        operator.migrateGraphqlKeysIfNeeded();

        String xml = java.nio.file.Files.readString(tempDir.resolve("rapla-data.xml"));
        org.junit.jupiter.api.Assertions.assertFalse(xml.contains("rapla_anonymousEvent"),
                "purge must rewrite the data file without the persisted internal type");
        org.junit.jupiter.api.Assertions.assertFalse(xml.contains("rapla_unresolvedResource"),
                "purge must rewrite the data file without the persisted internal type");

        // canonical type still resolves after the purge
        assertNotNull(operator.getDynamicType("rapla:anonymousEvent"));
    }
}
