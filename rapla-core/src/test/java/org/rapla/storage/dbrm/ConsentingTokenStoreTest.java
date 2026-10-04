package org.rapla.storage.dbrm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 126 Phase 2: the refresh token reaches the disk only with the user's consent. */
class ConsentingTokenStoreTest
{
    /** In-memory stand-in for the file/JNLP backends. */
    static final class MemoryStore implements TokenStore
    {
        final Map<String, String> map = new HashMap<>();
        @Override public Optional<String> read() { return Optional.ofNullable(map.get("refreshToken")); }
        @Override public void tryWrite(String token) { map.put("refreshToken", token); }
        @Override public void tryClear() { map.remove("refreshToken"); }
        @Override public Optional<String> readPref(String key) { return Optional.ofNullable(map.get(key)); }
        @Override public void tryWritePref(String key, String value) { if (value == null || value.isEmpty()) map.remove(key); else map.put(key, value); }
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
    void declinedConsentClearsAndIgnoresLaterWrites()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWrite("old");
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.setConsent(false);
        assertFalse(disk.read().isPresent());
        store.tryWrite("t1");
        assertFalse(disk.read().isPresent());
    }

    @Test
    void storedDecisionAppliesWithoutAsking()
    {
        MemoryStore yes = new MemoryStore();
        yes.tryWritePref(TokenStore.KEY_REMEMBER, "yes");
        ConsentingTokenStore storeYes = new ConsentingTokenStore(yes, "file");
        assertFalse(storeYes.needsDecision());
        storeYes.tryWrite("t1");
        assertEquals(Optional.of("t1"), yes.read());

        MemoryStore no = new MemoryStore();
        no.tryWritePref(TokenStore.KEY_REMEMBER, "no");
        ConsentingTokenStore storeNo = new ConsentingTokenStore(no, "file");
        assertFalse(storeNo.needsDecision());
        storeNo.tryWrite("t1");
        assertFalse(no.read().isPresent());
    }

    @Test
    void existingTokenFromBeforeTheFeatureCountsAsConsent()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWrite("legacy");
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        assertFalse(store.needsDecision());
        store.tryWrite("rotated");
        assertEquals(Optional.of("rotated"), disk.read());
        assertEquals(Optional.of("yes"), disk.readPref(TokenStore.KEY_REMEMBER), "kept as a standing answer, so a later rejected token does not ask again");
    }

    @Test
    void forgetDecisionAsksAgain()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWritePref(TokenStore.KEY_REMEMBER, "yes");
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.forgetDecision();
        assertTrue(store.needsDecision());
        assertFalse(disk.readPref(TokenStore.KEY_REMEMBER).isPresent());
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
    void expiryComesFromTheExpClaim()
    {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"u\",\"exp\":1800000000,\"typ\":\"refresh\"}".getBytes(StandardCharsets.UTF_8));
        String jwt = "eyJhbGciOiJSUzI1NiJ9." + payload + ".sig";
        assertEquals(Optional.of(Instant.ofEpochSecond(1800000000L)), ConsentingTokenStore.expiryOf(jwt));
        assertEquals(Optional.empty(), ConsentingTokenStore.expiryOf("not-a-jwt"));
        assertEquals(Optional.empty(), ConsentingTokenStore.expiryOf(null));
    }
}
