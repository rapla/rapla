package org.rapla.storage.dbrm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.RaplaResources;
import org.rapla.storage.RaplaSecurityException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the post-2026-05-25 reauth contract: refresh-token only, no password
 * fallback. When the refresh fails, surface session-expired so the higher layers
 * re-show the login dialog rather than silently re-running password login from
 * cached credentials. Removing the password-fallback path eliminates the
 * cleartext-password-in-JVM-memory window for the session's lifetime.
 *
 * <p>Companion to {@code RefreshOn401InterceptorAuthDeadTest} which covers the
 * HTTP-interface-proxy tier; this covers the RPC tier.
 */
class MyCustomConnectorReauthTest
{
    private HttpServer server;
    private RemoteConnectionInfo info;
    private final AtomicInteger refreshCallCount = new AtomicInteger();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private volatile int refreshResponseStatus = 200;
    private volatile String refreshResponseBody =
            "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\","
                    + "\"token_type\":\"Bearer\",\"expires_in\":600}";

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            refreshCallCount.incrementAndGet();
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = refreshResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(refreshResponseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        });
        server.start();
        info = new RemoteConnectionInfo();
        info.setServerURL("http://127.0.0.1:" + server.getAddress().getPort());
        info.setAccessToken("expired-access");
        info.setRefreshToken("live-refresh");
    }

    @AfterEach
    void tearDown()
    {
        if (server != null) server.stop(0);
    }

    private MyCustomConnector connector()
    {
        // i18n + commandQueue are unused on the reauth() path; null-safe Suppliers.
        return new MyCustomConnector(info, () -> (RaplaResources) null, null, TokenStores.noOp());
    }

    @Test
    void refreshTokenReauthReturnsNewAccessToken() throws Exception
    {
        String newToken = connector().reauth(Object.class);

        assertEquals(1, refreshCallCount.get(), "refresh called exactly once");
        assertEquals("new-access", newToken);
        assertEquals("new-access", info.getAccessToken(), "access token rotated on info");
        assertEquals("new-refresh", info.getRefreshToken(), "refresh token rotated on info");
        assertNotNull(capturedBody.get());
        assertTrue(capturedBody.get().contains("grant_type=refresh_token"),
                "body must be OAuth2 form-encoded: " + capturedBody.get());
        // PRD 072 Phase 5: rapla is the single token endpoint — refresh always
        // carries client_id=rapla-client against rapla's own /oauth2/token.
        assertTrue(capturedBody.get().contains("client_id=rapla-client"),
                "body must carry client_id=rapla-client: " + capturedBody.get());
    }

    @Test
    void refreshFailureSurfacesSessionExpiredWithNoPasswordFallback()
    {
        refreshResponseStatus = 400;
        refreshResponseBody = "{\"error\":\"invalid_grant\"}";

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> connector().reauth(Object.class));

        assertTrue(ex.getMessage().toLowerCase().contains("session has expired"),
                "must surface session-expired (no password fallback): " + ex.getMessage());
        // Refresh was attempted exactly once — no silent password-grant retry.
        assertEquals(1, refreshCallCount.get(),
                "no retry / no password-grant fallback after refresh fails");
    }

    @Test
    void absentRefreshTokenSurfacesSessionExpired()
    {
        info.setRefreshToken(null);

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> connector().reauth(Object.class));

        assertTrue(ex.getMessage().toLowerCase().contains("session has expired"),
                "must surface session-expired when nothing to refresh: " + ex.getMessage());
        assertEquals(0, refreshCallCount.get(),
                "refresh endpoint must not be called when no refresh token is available");
    }

    @Test
    void reauthOnPasswordLoginProxyReturnsNull() throws Exception
    {
        // The 401 we got was for the auth endpoint itself — recursing into reauth
        // would loop forever. The contract is "return null to let the caller propagate
        // the 401 to the user." OAuth2PasswordLogin doesn't actually go through the
        // RPC proxy mechanism today, but the guard remains as belt-and-suspenders.
        String result = connector().reauth(OAuth2PasswordLogin.class);
        assertEquals(null, result);
        assertEquals(0, refreshCallCount.get());
    }
}
