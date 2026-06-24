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
    void missingScopesOnStoredEntryMeansReadOnly()
    {
        // Legacy handling dropped (supersedes the original D8): a stored entry with no scopes
        // field now resolves to least-privilege {read}, NOT write_all. The only legacy key that
        // writes (dualis) is exempted at its own endpoint via ApiKeyScopeContext.callUnrestricted.
        Set<String> resolved = ApiKeyScopes.resolveStored(null);
        assertEquals(Set.of(ApiKeyScopes.READ), resolved);
        assertFalse(ApiKeyScopes.canWriteEvents(resolved), "legacy key must NOT write events");
        assertFalse(ApiKeyScopes.canWriteResources(resolved), "legacy key must NOT write resources");
    }

    @Test
    void accessDetailsIsValidVocabularyButNotImpliedByPlainRead()
    {
        // access_details gates sensitive identity/permission expansions; the default {read} key
        // does NOT have it. write_all (full power) implies it; write_events does not.
        assertEquals(Set.of(ApiKeyScopes.READ, ApiKeyScopes.ACCESS_DETAILS),
                ApiKeyScopes.normaliseForNewKey(List.of(ApiKeyScopes.ACCESS_DETAILS)));
        assertFalse(ApiKeyScopes.hasAccessDetails(Set.of(ApiKeyScopes.READ)));
        assertTrue(ApiKeyScopes.hasAccessDetails(Set.of(ApiKeyScopes.ACCESS_DETAILS)));
        assertTrue(ApiKeyScopes.hasAccessDetails(Set.of(ApiKeyScopes.WRITE_ALL)));
        assertFalse(ApiKeyScopes.hasAccessDetails(Set.of(ApiKeyScopes.WRITE_EVENTS)));
    }

    @Test
    void createAlwaysIncludesRead()
    {
        // "validate at least read is set on create" — read is the guaranteed floor, auto-added
        // even when only a write scope is requested, so no stored key is ever write-only.
        assertTrue(ApiKeyScopes.normaliseForNewKey(List.of(ApiKeyScopes.WRITE_EVENTS))
                .contains(ApiKeyScopes.READ));
        assertEquals(Set.of(ApiKeyScopes.READ, ApiKeyScopes.WRITE_ALL),
                ApiKeyScopes.normaliseForNewKey(List.of(ApiKeyScopes.WRITE_ALL)));
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
