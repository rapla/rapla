package org.rapla.storage.dbrm;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 126 Phase 2 / PRD 131 R4: the refresh token reaches the disk only with the consent the server's stay-signed-in decision gives. */
class ConsentingTokenStoreTest
{
    /** In-memory stand-in for the file/JNLP backends. */
    static final class MemoryStore implements TokenStore
    {
        final Map<String, String> map = new HashMap<>();
        Failure failure;
        @Override public Optional<String> read() { return Optional.ofNullable(map.get("refreshToken")); }
        @Override public void tryWrite(String token) { map.put("refreshToken", token); }
        @Override public void tryClear() { map.remove("refreshToken"); }
        @Override public Optional<String> readPref(String key) { return Optional.ofNullable(map.get(key)); }
        @Override public void tryWritePref(String key, String value) { if (value == null || value.isEmpty()) map.remove(key); else map.put(key, value); }
        @Override public Optional<Failure> failure() { return Optional.ofNullable(failure); }
    }

    @Test
    void withoutDecisionTheTokenWaitsUntilConsent()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.tryWrite("t1");
        assertFalse(disk.read().isPresent(), "nothing on disk before the user decided");
        assertTrue(store.setConsent(true), "stored and read back");
        assertEquals(Optional.of("t1"), disk.read());
        store.tryWrite("t2");
        assertEquals(Optional.of("t2"), disk.read(), "rotations keep flowing after consent");
    }

    @Test
    void declinedConsentLeavesAStoredEntryUntouchedAndIgnoresLaterWrites()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWrite("old");
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        assertFalse(store.setConsent(false));
        assertEquals(Optional.of("old"), disk.read(), "a login without stay-signed-in never touches the stored entry (PRD 131 OQ10)");
        store.tryWrite("t1");
        assertEquals(Optional.of("old"), disk.read(), "its tokens stay in memory");
        assertFalse(store.remembers());
    }

    @Test
    void declinedConsentWithoutAnEntryStoresNothing()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.setConsent(false);
        store.tryWrite("t1");
        assertFalse(disk.read().isPresent());
    }

    @Test
    void rememberedChoiceIsVisibleForTheStorageHint()
    {
        ConsentingTokenStore store = new ConsentingTokenStore(new MemoryStore(), "file");
        assertFalse(store.remembers(), "no decision yet");
        store.setConsent(true);
        assertTrue(store.remembers());
    }

    @Test
    void subjectOfReadsTheSubClaimOrNothing()
    {
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"u1\",\"typ\":\"refresh\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(Optional.of("u1"), ConsentingTokenStore.subjectOf("h." + payload + ".s"));
        assertEquals(Optional.empty(), ConsentingTokenStore.subjectOf("not-a-jwt"));
        assertEquals(Optional.empty(), ConsentingTokenStore.subjectOf(null));
    }

    @Test
    void aRememberPrefLeftByTheOldDialogIsIgnored()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWritePref("remember", "yes");
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.tryWrite("t1");
        assertFalse(disk.read().isPresent(), "only the server's decision stores a token now");
        assertTrue(store.setConsent(true));
        assertEquals(Optional.of("t1"), disk.read());
    }

    @Test
    void aStoredTokenCountsAsStandingConsent()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWrite("stored");
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.tryWrite("rotated");
        assertEquals(Optional.of("rotated"), disk.read());
    }

    @Test
    void consentFailsWhenTheBackendCannotStore()
    {
        TokenStore dead = TokenStores.noOp();
        ConsentingTokenStore store = new ConsentingTokenStore(dead, "none");
        store.tryWrite("t1");
        assertFalse(store.setConsent(true));
        assertEquals("none", store.backend());
    }

    @Test
    void tokenAppearingAfterConstructionStillCountsAsConsent()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        disk.tryWrite("restored");   // the keyed backend only knows its origin after construction
        store.tryWrite("rotated");
        assertEquals(Optional.of("rotated"), disk.read());
    }

    @Test
    void standingIsResolvedBeforeARejectedTokenIsCleared()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        disk.tryWrite("stored");
        assertEquals(Optional.of("stored"), store.read(), "startup restore");
        store.tryClear();   // the server rejected it
        store.tryWrite("fresh");
        assertEquals(Optional.of("fresh"), disk.read(), "the stored token had been consent");
    }

    @Test
    void consentIsNotStoredWhileTheBackendReportsAFailure()
    {
        MemoryStore disk = new MemoryStore();
        disk.failure = new TokenStore.Failure(java.nio.file.Path.of("tokens.json"), "readable by others");
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.tryWrite("t1");
        assertFalse(store.setConsent(true), "memory-only is not stored");
        assertEquals(disk.failure, store.failure().orElse(null), "the failure is visible through the wrapper");
    }
}
