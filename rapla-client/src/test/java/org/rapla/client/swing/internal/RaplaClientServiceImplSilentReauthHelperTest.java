package org.rapla.client.swing.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks in the post-2026-05-25 cold-startup silent reauth contract:
 *
 * <ul>
 *   <li>POSTs OAuth2-standard {@code grant_type=refresh_token} form body (NOT the
 *       deleted rapla-custom JSON shape).</li>
 *   <li>Parses snake_case {@code access_token} / {@code refresh_token} from the
 *       response (NOT camelCase).</li>
 *   <li>Sends the client_id passed in (Keycloak's id when applicable, NOT a
 *       hardcoded {@code rapla-client}).</li>
 *   <li>Reports failure cleanly on non-2xx so the caller can clear the stale
 *       cached token and fall through to the login dialog.</li>
 * </ul>
 *
 * <p>Pre-fix, the silent reauth POSTed JSON to the deleted
 * {@code /api/auth/refresh} endpoint and always 404'd — every Swing client cold
 * start fell through to the login dialog even with a valid cached token. Per
 * AGENTS.md §1 (test-first), the regression test goes here so the next refactor
 * of that path can't re-introduce the old wire format.
 */
@RunWith(JUnit4.class)
public class RaplaClientServiceImplSilentReauthHelperTest
{
    private HttpServer server;
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody =
            "{\"access_token\":\"fresh-access\",\"refresh_token\":\"rotated-refresh\","
                    + "\"token_type\":\"Bearer\",\"expires_in\":600}";

    @Before
    public void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Two paths — verifies the helper hits whichever URL was passed,
        // not a hardcoded one. Test sets the URL it wants to hit per case.
        server.createContext("/oauth2/token", this::handle);
        server.createContext("/provider/token", this::handle);
        server.start();
    }

    private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException
    {
        callCount.incrementAndGet();
        capturedPath.set(exchange.getRequestURI().getPath());
        capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, out.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
    }

    @After
    public void tearDown()
    {
        if (server != null) server.stop(0);
    }

    private String url(String path)
    {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    public void postsFormEncodedOAuth2RefreshGrantToTheGivenUrlWithGivenClientId() throws Exception
    {
        RaplaClientServiceImpl.SilentRefreshResult result =
                RaplaClientServiceImpl.executeSilentRefresh(url("/provider/token"), "keycloak-client", "stored-refresh");

        assertEquals(1, callCount.get());
        assertEquals("must hit the provider URL passed in", "/provider/token", capturedPath.get());
        assertEquals("must be form-encoded (OAuth2 spec), not JSON",
                "application/x-www-form-urlencoded", capturedContentType.get());
        String body = capturedBody.get();
        assertNotNull(body);
        assertTrue("body must use OAuth2 grant_type, not the deleted rapla JSON shape: " + body,
                body.contains("grant_type=refresh_token"));
        assertTrue("body must carry the refresh token: " + body, body.contains("refresh_token=stored-refresh"));
        assertTrue("body must carry the client_id passed in, not hardcoded rapla-client: " + body,
                body.contains("client_id=keycloak-client"));
        assertFalse("must NOT carry the JSON shape (old wire format): " + body,
                body.contains("\"refreshToken\""));

        assertTrue(result.succeeded());
        assertEquals("fresh-access", result.accessToken);
        assertEquals("rotated-refresh", result.refreshToken);
    }

    @Test
    public void fallsBackToRaplaSasDefaultsWhenCallerPassesThem() throws Exception
    {
        RaplaClientServiceImpl.SilentRefreshResult result =
                RaplaClientServiceImpl.executeSilentRefresh(url("/oauth2/token"), "rapla-client", "rapla-refresh");

        assertEquals("/oauth2/token", capturedPath.get());
        assertTrue("rapla-client id sent: " + capturedBody.get(),
                capturedBody.get().contains("client_id=rapla-client"));
        assertTrue(result.succeeded());
    }

    @Test
    public void nonSuccessHttpStatusYieldsFailureResult() throws Exception
    {
        responseStatus = 400;
        responseBody = "{\"error\":\"invalid_grant\"}";

        RaplaClientServiceImpl.SilentRefreshResult result =
                RaplaClientServiceImpl.executeSilentRefresh(url("/provider/token"), "keycloak-client", "dead-refresh");

        assertFalse("non-2xx must surface as failure", result.succeeded());
        assertEquals(400, result.httpStatus);
        assertNull(result.accessToken);
    }

    @Test
    public void parsesSnakeCaseTokensNotCamelCase() throws Exception
    {
        // The pre-fix code extracted camelCase ("accessToken"). If a regression
        // ever reintroduced camelCase parsing while the server kept emitting
        // snake_case (OAuth2 standard), this test would go red.
        responseBody = "{\"access_token\":\"abc\",\"refresh_token\":\"xyz\"}";

        RaplaClientServiceImpl.SilentRefreshResult result =
                RaplaClientServiceImpl.executeSilentRefresh(url("/oauth2/token"), "rapla-client", "r");

        assertEquals("abc", result.accessToken);
        assertEquals("xyz", result.refreshToken);
    }

    @Test
    public void responseMissingAccessTokenYieldsFailureResult() throws Exception
    {
        responseStatus = 200;
        responseBody = "{\"token_type\":\"Bearer\"}";  // server returned 200 but no token

        RaplaClientServiceImpl.SilentRefreshResult result =
                RaplaClientServiceImpl.executeSilentRefresh(url("/oauth2/token"), "rapla-client", "r");

        assertFalse(result.succeeded());
        assertNull(result.accessToken);
    }
}
