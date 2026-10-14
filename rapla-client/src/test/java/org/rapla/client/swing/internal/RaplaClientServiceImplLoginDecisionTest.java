package org.rapla.client.swing.internal;

import org.junit.Test;
import org.rapla.client.spring.LogoutSignal;
import org.rapla.storage.dbrm.ConsentingTokenStore;
import org.rapla.storage.dbrm.TokenStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** PRD 131 R4/D6: the server's stay-signed-in decision replaces the consent dialog; the post-logout prompt survives a JVM exit. */
public class RaplaClientServiceImplLoginDecisionTest
{
    private static final class MemoryStore implements TokenStore
    {
        final Map<String, String> map = new HashMap<>();
        @Override public Optional<String> read() { return Optional.ofNullable(map.get("refreshToken")); }
        @Override public void tryWrite(String token) { map.put("refreshToken", token); }
        @Override public void tryClear() { map.remove("refreshToken"); }
        @Override public Optional<String> readPref(String key) { return Optional.ofNullable(map.get(key)); }
        @Override public void tryWritePref(String key, String value) { if (value == null || value.isEmpty()) map.remove(key); else map.put(key, value); }
    }

    @Test
    public void rememberedDecisionStoresTheTokenOfThisLoginAndItsRotations()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        RaplaClientServiceImpl.applyLoginDecision(store, true);
        store.tryWrite("t1");
        assertEquals(Optional.of("t1"), disk.read());
        store.tryWrite("t2");
        assertEquals(Optional.of("t2"), disk.read());
    }

    @Test
    public void unrememberedDecisionKeepsTheTokenOutOfTheStore()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        RaplaClientServiceImpl.applyLoginDecision(store, false);
        store.tryWrite("t1");
        assertFalse(disk.read().isPresent());
    }

    @Test
    public void decisionAfterTheTokenArrivedStillStoresIt()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore store = new ConsentingTokenStore(disk, "file");
        store.tryWrite("t1");
        assertFalse(disk.read().isPresent());
        RaplaClientServiceImpl.applyLoginDecision(store, true);
        assertEquals(Optional.of("t1"), disk.read());
    }

    @Test
    public void aStoreWithoutConsentGateIsLeftAlone()
    {
        MemoryStore disk = new MemoryStore();
        RaplaClientServiceImpl.applyLoginDecision(disk, true);
        assertTrue(disk.map.isEmpty());
    }

    @Test
    public void logoutPromptSurvivesTheExitAndIsFedOnceToTheNextStart()
    {
        MemoryStore disk = new MemoryStore();
        RaplaClientServiceImpl.markLoginPrompt(disk, true);

        LogoutSignal nextStart = new LogoutSignal();
        assertFalse(nextStart.isForceOauthLoginNext());
        assertTrue(RaplaClientServiceImpl.loginPromptPending(disk));
        nextStart.setForceOauthLoginNext(true);
        assertTrue(nextStart.consumeForceOauthLoginNext());

        RaplaClientServiceImpl.markLoginPrompt(disk, false);
        assertFalse("consumed by the successful login", RaplaClientServiceImpl.loginPromptPending(disk));
    }

    @Test
    public void noLogoutNoPrompt()
    {
        assertFalse(RaplaClientServiceImpl.loginPromptPending(new MemoryStore()));
    }

    private static org.rapla.client.spring.NextSession switchFrom(ConsentingTokenStore ctx1)
    {
        org.rapla.storage.dbrm.RemoteConnectionInfo info = new org.rapla.storage.dbrm.RemoteConnectionInfo();
        info.setAccessToken("actor-access");
        info.setRefreshToken("actor-refresh");
        return RaplaClientServiceImpl.switchSession(info, RaplaClientServiceImpl.stayDecision(ctx1), "imp", "bob");
    }

    /** What a rebuilt context does with the token it starts with: carried decision first, then the login() write. */
    private static void startContext(ConsentingTokenStore ctx2, org.rapla.ConnectInfo info)
    {
        RaplaClientServiceImpl.applyCarriedDecision(ctx2, info);
        ctx2.tryWrite(info.getRefreshToken());
    }

    @Test
    public void onlyThisSessionSurvivesSwitchUserAndSwitchBack()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWrite("old-entry");
        ConsentingTokenStore ctx1 = new ConsentingTokenStore(disk, "file");
        RaplaClientServiceImpl.applyLoginDecision(ctx1, false);
        ctx1.tryWrite("actor-refresh");

        org.rapla.client.spring.NextSession next = switchFrom(ctx1);
        startContext(new ConsentingTokenStore(disk, "file"), next.info());
        assertEquals("switch user", Optional.of("old-entry"), disk.read());

        startContext(new ConsentingTokenStore(disk, "file"), next.restoreInfo());
        assertEquals("switch back", Optional.of("old-entry"), disk.read());
    }

    @Test
    public void onlyThisSessionWithoutAnEntryStoresNothingAfterASwitch()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore ctx1 = new ConsentingTokenStore(disk, "file");
        RaplaClientServiceImpl.applyLoginDecision(ctx1, false);
        startContext(new ConsentingTokenStore(disk, "file"), switchFrom(ctx1).info());
        assertFalse(disk.read().isPresent());
    }

    @Test
    public void rememberedLoginKeepsPersistingAcrossASwitch()
    {
        MemoryStore disk = new MemoryStore();
        ConsentingTokenStore ctx1 = new ConsentingTokenStore(disk, "file");
        RaplaClientServiceImpl.applyLoginDecision(ctx1, true);
        ctx1.tryWrite("actor-refresh");

        org.rapla.client.spring.NextSession next = switchFrom(ctx1);
        disk.tryWrite("other");
        startContext(new ConsentingTokenStore(disk, "file"), next.info());
        assertEquals(Optional.of("actor-refresh"), disk.read());
    }

    @Test
    public void coldStartWithAStoredTokenStillRotatesAndSurvivesASwitch()
    {
        MemoryStore disk = new MemoryStore();
        disk.tryWrite("stored");
        ConsentingTokenStore cold = new ConsentingTokenStore(disk, "file");
        RaplaClientServiceImpl.applyCarriedDecision(cold, new org.rapla.ConnectInfo("a", "r"));
        cold.tryWrite("rotated");
        assertEquals(Optional.of("rotated"), disk.read());

        org.rapla.client.spring.NextSession next = switchFrom(cold);
        disk.tryClear();
        disk.tryWrite("stored-again");
        startContext(new ConsentingTokenStore(disk, "file"), next.info());
        assertEquals(Optional.of("actor-refresh"), disk.read());
    }
}
