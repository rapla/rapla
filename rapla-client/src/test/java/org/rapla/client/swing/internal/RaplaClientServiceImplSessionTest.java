package org.rapla.client.swing.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.client.spring.NextSession;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.TokenStore;

/** PRD 131 D6, D10, R5, R8, OQ10: the decisions behind "Abmelden", "Neu anmelden", the session-ended dialog and switching while impersonating. */
class RaplaClientServiceImplSessionTest
{
    private static final class MemoryStore implements TokenStore
    {
        final Map<String, String> map = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        @Override public Optional<String> read() { return Optional.ofNullable(map.get("refreshToken")); }
        @Override public void tryWrite(String token) { map.put("refreshToken", token); }
        @Override public void tryClear() { calls.add("clear"); map.remove("refreshToken"); }
        @Override public Optional<String> readPref(String key) { return Optional.ofNullable(map.get(key)); }
        @Override public void tryWritePref(String key, String value) { if (value == null || value.isEmpty()) map.remove(key); else map.put(key, value); }
    }

    private HttpServer server;
    private final AtomicReference<String> revokeBody = new AtomicReference<>();
    private String serverUrl;

    @BeforeEach
    void startServer() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/revoke", exchange -> {
            revokeBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer()
    {
        server.stop(0);
    }

    private static String jwt(String subject)
    {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + subject + "\",\"typ\":\"refresh\"}").getBytes(StandardCharsets.UTF_8));
        return "h." + payload + ".s";
    }

    @Test
    void logoutAndExitRevokesTheSessionClearsItsStoredEntryRemembersThePromptAndExits()
    {
        MemoryStore store = new MemoryStore();
        String refresh = jwt("alice");
        store.tryWrite(refresh);

        NextSession next = RaplaClientServiceImpl.logoutSession(serverUrl, refresh, store, true);

        assertTrue(java.net.URLDecoder.decode(revokeBody.get(), StandardCharsets.UTF_8).contains("token=" + refresh), revokeBody.get());
        assertTrue(revokeBody.get().contains("token_type_hint=refresh_token"));
        assertTrue(next.isExit(), "Abmelden ends the application instead of showing the login dialog");
        assertFalse(next.isForceOauthLogin());
        assertFalse(store.read().isPresent());
        assertEquals("login", store.readPref(TokenStore.KEY_NEXT_LOGIN_PROMPT).orElse(""), "the next start must not silently re-authenticate");
    }

    @Test
    void neuAnmeldenRevokesAndShowsTheLoginDialogWithPrompt()
    {
        MemoryStore store = new MemoryStore();
        String refresh = jwt("alice");

        NextSession next = RaplaClientServiceImpl.logoutSession(serverUrl, refresh, store, false);

        assertTrue(revokeBody.get().startsWith("token="));
        assertFalse(next.isExit());
        assertTrue(next.isForceOauthLogin());
        assertNull(next.info());
    }

    @Test
    void logoutKeepsAStoredEntryOfAnotherUser()
    {
        MemoryStore store = new MemoryStore();
        store.tryWrite(jwt("alice"));

        RaplaClientServiceImpl.logoutSession(serverUrl, jwt("bob"), store, true);

        assertEquals(Optional.of(jwt("alice")), store.read(), "bob logged in without stay-signed-in; alice's stored session stays (OQ10)");
    }

    @Test
    void logoutClearsWhenTheOwnerCannotBeTold()
    {
        MemoryStore store = new MemoryStore();
        store.tryWrite("opaque");

        RaplaClientServiceImpl.logoutSession(serverUrl, "also-opaque", store, true);

        assertFalse(store.read().isPresent());
    }

    @Test
    void unreachableServerDoesNotBlockTheLocalLogout()
    {
        MemoryStore store = new MemoryStore();
        String refresh = jwt("alice");
        store.tryWrite(refresh);
        server.stop(0);

        NextSession next = RaplaClientServiceImpl.logoutSession(serverUrl, refresh, store, true);

        assertTrue(next.isExit());
        assertFalse(store.read().isPresent());
    }

    @Test
    void sessionEndedDialogMapsTheButtonsToLoginAgainAndQuit()
    {
        List<String> ran = new ArrayList<>();
        RaplaClientServiceImpl.onSessionEndedChoice(0, () -> ran.add("relogin"), () -> ran.add("quit"));
        RaplaClientServiceImpl.onSessionEndedChoice(1, () -> ran.add("relogin"), () -> ran.add("quit"));
        RaplaClientServiceImpl.onSessionEndedChoice(-1, () -> ran.add("relogin"), () -> ran.add("quit"));
        assertEquals(List.of("relogin", "quit", "relogin"), ran, "closing the window keeps today's restart behaviour");
    }

    @Test
    void switchingWhileImpersonatingStartsTheNextContextWithTheActorsSession()
    {
        RemoteConnectionInfo info = new RemoteConnectionInfo();
        info.setAccessToken("actor-access");
        info.setRefreshToken("actor-refresh");
        info.setImpersonationAccessToken("imp-b", "bob");

        NextSession next = RaplaClientServiceImpl.switchSession(info, null, "imp-c", "carol");

        assertEquals("actor-access", next.info().getAccessToken(), "A -> B -> C is checked as A -> C: the actor's own session carries on");
        assertEquals("actor-refresh", next.info().getRefreshToken());
        assertEquals("actor-access", next.restoreInfo().getAccessToken(), "switching back returns to the actor");
        assertEquals("imp-c", next.impersonationAccessToken());
        assertEquals("carol", next.impersonationTargetUsername());
        assertFalse(next.isExit());
    }
}
