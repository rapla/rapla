package org.rapla.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 076 Phase 1 — the fixed scope vocabulary + its capability/default rules.
 * Pure logic, no Spring (tier 1).
 */
class ApiKeyScopesTest
{
    @Test
    void newKeyDefaultsToReadOnly()
    {
        // D5 — a newly created key with no scopes requested is least-privilege {read}.
        assertEquals(Set.of(ApiKeyScopes.READ), ApiKeyScopes.normaliseForNewKey(null));
        assertEquals(Set.of(ApiKeyScopes.READ), ApiKeyScopes.normaliseForNewKey(List.of()));
    }

    @Test
    void missingScopesOnStoredEntryMeansWriteAll()
    {
        // D8 — an EXISTING key entry written before this PRD has no scopes field;
        // it must resolve to full write power (behaviour-identical to today), NEVER read.
        Set<String> resolved = ApiKeyScopes.resolveStored(null);
        assertTrue(ApiKeyScopes.canWriteEvents(resolved), "legacy key must keep event-write");
        assertTrue(ApiKeyScopes.canWriteResources(resolved), "legacy key must keep resource-write");
    }

    @Test
    void explicitScopesArePreserved()
    {
        Set<String> resolved = ApiKeyScopes.resolveStored(List.of(ApiKeyScopes.WRITE_EVENTS));
        assertEquals(Set.of(ApiKeyScopes.WRITE_EVENTS), resolved);
    }

    @Test
    void unknownScopeIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> ApiKeyScopes.normaliseForNewKey(List.of("bogus")));
        assertThrows(IllegalArgumentException.class, () -> ApiKeyScopes.normaliseForNewKey(List.of(ApiKeyScopes.READ, "admin")));
    }

    @Test
    void writeCapabilitiesByScope()
    {
        Set<String> readOnly = Set.of(ApiKeyScopes.READ);
        assertFalse(ApiKeyScopes.canWriteEvents(readOnly));
        assertFalse(ApiKeyScopes.canWriteResources(readOnly));

        Set<String> events = Set.of(ApiKeyScopes.WRITE_EVENTS);
        assertTrue(ApiKeyScopes.canWriteEvents(events));
        assertFalse(ApiKeyScopes.canWriteResources(events));

        Set<String> resources = Set.of(ApiKeyScopes.WRITE_RESOURCES);
        assertFalse(ApiKeyScopes.canWriteEvents(resources));
        assertTrue(ApiKeyScopes.canWriteResources(resources));

        Set<String> all = Set.of(ApiKeyScopes.WRITE_ALL);
        assertTrue(ApiKeyScopes.canWriteEvents(all));
        assertTrue(ApiKeyScopes.canWriteResources(all));
    }

    @Test
    void rotateSelfIsOrthogonalToWrite()
    {
        // D3 — {read, rotate_self} can rotate but writes nothing.
        Set<String> readRotate = ApiKeyScopes.normaliseForNewKey(List.of(ApiKeyScopes.READ, ApiKeyScopes.ROTATE_SELF));
        assertTrue(ApiKeyScopes.canRotateSelf(readRotate));
        assertFalse(ApiKeyScopes.canWriteEvents(readRotate));
        assertFalse(ApiKeyScopes.canWriteResources(readRotate));

        // a plain read key cannot rotate
        assertFalse(ApiKeyScopes.canRotateSelf(Set.of(ApiKeyScopes.READ)));
    }
}
