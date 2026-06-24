package org.rapla.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.rapla.storage.RaplaSecurityException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The two explicit out-of-band scope gates that cover the paths the automatic operator
 * chokepoint ({@code guardApiKeyScopes}) does NOT see (tier 1, pure logic):
 * <ul>
 *   <li>{@link ApiKeyScopeContext#requireWriteAllForBulk} — non-dispatch bulk writes (archiver).</li>
 *   <li>{@link ApiKeyScopeContext#requireInteractiveSession} — reads of server-side secrets/admin
 *       config; api-keys are off-limits by token-kind, regardless of scope.</li>
 * </ul>
 */
class ApiKeyScopeContextTest
{
    @AfterEach
    void resetSource()
    {
        ApiKeyScopeContext.setSource(null);
    }

    @Test
    void interactiveSessionPassesBothGates()
    {
        // default source ⇒ current() == null ⇒ not an api-key ⇒ everything allowed
        ApiKeyScopeContext.setSource(null);
        assertDoesNotThrow(() -> ApiKeyScopeContext.requireInteractiveSession("mail config"));
        assertDoesNotThrow(() -> ApiKeyScopeContext.requireWriteAllForBulk("archiver"));
    }

    @Test
    void anyApiKeyRejectedFromInteractiveOnlyReads()
    {
        // token-kind gate: even a full-power write_all key may not read interactive-only material
        ApiKeyScopeContext.setSource(() -> Set.of(ApiKeyScopes.WRITE_ALL));
        assertThrows(RaplaSecurityException.class,
                () -> ApiKeyScopeContext.requireInteractiveSession("mail config"));

        ApiKeyScopeContext.setSource(() -> Set.of(ApiKeyScopes.READ));
        assertThrows(RaplaSecurityException.class,
                () -> ApiKeyScopeContext.requireInteractiveSession("mail config"));
    }

    @Test
    void bulkGateIsScopeBasedNotTokenKind()
    {
        // requireWriteAllForBulk lets a write_all key through but rejects a lesser scope
        ApiKeyScopeContext.setSource(() -> Set.of(ApiKeyScopes.WRITE_ALL));
        assertDoesNotThrow(() -> ApiKeyScopeContext.requireWriteAllForBulk("archiver"));

        ApiKeyScopeContext.setSource(() -> Set.of(ApiKeyScopes.READ));
        assertThrows(RaplaSecurityException.class,
                () -> ApiKeyScopeContext.requireWriteAllForBulk("archiver"));
    }

    @Test
    void callUnrestrictedSuspendsBothGates() throws Exception
    {
        ApiKeyScopeContext.setSource(() -> Set.of(ApiKeyScopes.READ));
        // inside callUnrestricted current() == null, so the server-internal opt-out passes
        ApiKeyScopeContext.callUnrestricted(() -> {
            ApiKeyScopeContext.requireInteractiveSession("mail config");
            ApiKeyScopeContext.requireWriteAllForBulk("archiver");
            return null;
        });
    }
}
