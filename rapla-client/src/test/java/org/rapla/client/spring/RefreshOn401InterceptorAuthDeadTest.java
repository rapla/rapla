package org.rapla.client.spring;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.TokenStore;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the 2026-05-20 live-server bug: after a 12h idle period the
 * Swing client's access token expires; {@link ClientProxyConfig.RefreshOn401Interceptor}
 * tries to refresh, the refresh ALSO fails (server returns
 * {@code 400 invalid_grant} because the persisted SESSION token doesn't match
 * — separately fixed in {@code RaplaSQL.LockStorage.readLockTimestamp}). The client
 * then has no path back to a login dialog: the original 401 just propagates as
 * an uncaught exception that {@code RaplaClientServiceImpl.updateError(...)} logs.
 *
 * <p>Locked-in behaviour:
 * <ul>
 *   <li>Interceptor attempts refresh on 401.</li>
 *   <li>If refresh ALSO fails non-2xx, the interceptor clears the stored access
 *       + refresh tokens (both known dead) AND invokes the
 *       {@link RemoteConnectionInfo#getOnAuthDead} hook so higher layers can
 *       fire {@code disconnected(...)} and re-show the login dialog.</li>
 *   <li>If refresh succeeds, neither the tokens nor the hook are touched.</li>
 * </ul>
 */
@RunWith(JUnit4.class)
public class RefreshOn401InterceptorAuthDeadTest
{
    private HttpServer server;
    private RemoteConnectionInfo info;
    private final AtomicInteger resourceCallCount = new AtomicInteger();
    private final AtomicInteger refreshCallCount = new AtomicInteger();
    private volatile int refreshResponseStatus = 400;
    private volatile String refreshResponseBody = "{\"error\":\"invalid_grant\"}";

    // Provider-specific refresh endpoint (e.g. Keycloak's token URL or rapla's BFF).
    // Counts hits and records the form-encoded body so the test can assert what
    // client_id was sent.
    private final AtomicInteger providerRefreshCallCount = new AtomicInteger();
    private volatile String providerRefreshLastBody = null;
    private volatile int providerRefreshResponseStatus = 200;
    private volatile String providerRefreshResponseBody =
            "{\"access_token\":\"kc-new-access\",\"refresh_token\":\"kc-new-refresh\","
                    + "\"token_type\":\"Bearer\",\"expires_in\":600}";

    /** In-memory TokenStore stub so tests can assert persistence happens without
     *  touching a real file. */
    private static final class CapturingTokenStore implements TokenStore
    {
        final AtomicReference<String> persistedToken = new AtomicReference<>();
        final AtomicInteger writeCount = new AtomicInteger();
        @Override public Optional<String> read() { return Optional.ofNullable(persistedToken.get()); }
        @Override public void tryWrite(String token) { persistedToken.set(token); writeCount.incrementAndGet(); }
        @Override public void tryClear() { persistedToken.set(null); }
        @Override public Optional<String> readPref(String key) { return Optional.empty(); }
        @Override public void tryWritePref(String key, String value) {}
    }

    @Before
    public void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Protected resource — always 401 in this test (token expired).
        server.createContext("/api/resources", exchange -> {
            resourceCallCount.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        // Refresh endpoint — configurable per-test.
        server.createContext("/oauth2/token", exchange -> {
            refreshCallCount.incrementAndGet();
            byte[] out = refreshResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(refreshResponseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        });
        // Provider-specific refresh endpoint (Keycloak / BFF). When the test sets
        // info.setRefreshUrl(serverURL + "/provider/token"), the interceptor must
        // route the refresh here instead of /oauth2/token.
        server.createContext("/provider/token", exchange -> {
            providerRefreshCallCount.incrementAndGet();
            providerRefreshLastBody = new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = providerRefreshResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(providerRefreshResponseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        });
        server.start();
        info = new RemoteConnectionInfo();
        info.setServerURL("http://127.0.0.1:" + server.getAddress().getPort());
        info.setAccessToken("expired-access-token");
        info.setRefreshToken("stale-refresh-token");
    }

    @After
    public void tearDown()
    {
        if (server != null) server.stop(0);
    }

    @Test
    public void whenRefreshAlsoReturns400_thenAuthDeadHookFiresAndTokensCleared() throws Exception
    {
        AtomicBoolean authDeadFired = new AtomicBoolean(false);
        info.setOnAuthDead(() -> authDeadFired.set(true));

        ClientHttpRequestFactory factory = new BufferingHttpUrlConnectionRequestFactory();
        ClientProxyConfig.RefreshOn401Interceptor interceptor =
                new ClientProxyConfig.RefreshOn401Interceptor(info, factory);

        // Build a request to the protected resource. Use the same factory so the
        // interceptor's execution chain matches a real RestClient call.
        ClientHttpResponse response = invokeWithInterceptor(interceptor, factory,
                URI.create(info.getServerURL() + "/api/resources"), HttpMethod.GET);

        assertEquals("original 401 must propagate after refresh fails",
                401, response.getStatusCode().value());
        assertEquals("refresh attempted exactly once", 1, refreshCallCount.get());
        assertTrue("onAuthDead hook MUST fire when refresh returns invalid_grant",
                authDeadFired.get());
        assertEquals("access token cleared (known-dead)",
                null, info.getAccessToken());
        assertEquals("refresh token cleared (known-dead)",
                null, info.getRefreshToken());
    }

    @Test
    public void whenRefreshSucceeds_thenAuthDeadHookDoesNotFireAndTokensRotated() throws Exception
    {
        refreshResponseStatus = 200;
        refreshResponseBody = "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\","
                + "\"token_type\":\"Bearer\",\"expires_in\":3600}";

        AtomicBoolean authDeadFired = new AtomicBoolean(false);
        info.setOnAuthDead(() -> authDeadFired.set(true));

        ClientHttpRequestFactory factory = new BufferingHttpUrlConnectionRequestFactory();
        ClientProxyConfig.RefreshOn401Interceptor interceptor =
                new ClientProxyConfig.RefreshOn401Interceptor(info, factory);

        invokeWithInterceptor(interceptor, factory,
                URI.create(info.getServerURL() + "/api/resources"), HttpMethod.GET);

        assertFalse("onAuthDead must NOT fire when refresh succeeded",
                authDeadFired.get());
        assertEquals("new access token persisted", "new-access", info.getAccessToken());
        assertEquals("new refresh token persisted", "new-refresh", info.getRefreshToken());
    }

    /**
     * Regression for the 2026-05-25 token-rotation persistence bug: when Keycloak
     * rotates the refresh token mid-session (most realms do), the interceptor must
     * write the new token back to {@link TokenStore} so cold restart can still
     * silent-reauth. Without persistence, the on-disk token goes stale after the
     * first mid-session refresh and the next launch falls through to the login
     * dialog even though the user's Keycloak session is still alive.
     * Sibling: {@code MyCustomConnector.refreshUsingToken()} already does this;
     * this test locks in symmetry across both refresh paths.
     */
    @Test
    public void whenRefreshRotatesToken_thenNewTokenIsPersistedToTokenStore() throws Exception
    {
        info.setRefreshUrl(info.getServerURL() + "/provider/token");
        info.setOauthClientId("keycloak-client");
        // providerRefreshResponseBody (set in @Before) returns kc-new-refresh as
        // a different value from the request token — simulating rotation.
        CapturingTokenStore store = new CapturingTokenStore();

        ClientHttpRequestFactory factory = new BufferingHttpUrlConnectionRequestFactory();
        ClientProxyConfig.RefreshOn401Interceptor interceptor =
                new ClientProxyConfig.RefreshOn401Interceptor(info, factory, store);

        invokeWithInterceptor(interceptor, factory,
                URI.create(info.getServerURL() + "/api/resources"), HttpMethod.GET);

        assertEquals("rotated refresh token written to store exactly once",
                1, store.writeCount.get());
        assertEquals("persisted token equals the value the provider returned",
                "kc-new-refresh", store.persistedToken.get());
    }

    /**
     * Regression for the 2026-05-25 Keycloak refresh bug: when an OAuth provider
     * (Keycloak, Entra, Google) was used to log in, {@code SwingOAuthLoginFlow}
     * stashes the provider's token endpoint + client_id on {@link RemoteConnectionInfo}.
     * The 401-refresh interceptor must route the refresh there, NOT to rapla's own
     * {@code /oauth2/token} with {@code client_id=rapla-client} — rapla SAS cannot
     * validate a Keycloak-signed refresh JWT and would 400. Sibling refresh path
     * in {@code MyCustomConnector.refreshUsingToken()} already does this; this
     * test locks in that the HTTP-interface-proxy interceptor does too.
     */
    @Test
    public void whenProviderRefreshUrlIsSet_thenRefreshHitsThatUrlNotRaplaSas() throws Exception
    {
        info.setRefreshUrl(info.getServerURL() + "/provider/token");
        info.setOauthClientId("keycloak-client");

        AtomicBoolean authDeadFired = new AtomicBoolean(false);
        info.setOnAuthDead(() -> authDeadFired.set(true));

        ClientHttpRequestFactory factory = new BufferingHttpUrlConnectionRequestFactory();
        ClientProxyConfig.RefreshOn401Interceptor interceptor =
                new ClientProxyConfig.RefreshOn401Interceptor(info, factory);

        invokeWithInterceptor(interceptor, factory,
                URI.create(info.getServerURL() + "/api/resources"), HttpMethod.GET);

        assertEquals("refresh MUST hit the provider's token endpoint",
                1, providerRefreshCallCount.get());
        assertEquals("refresh MUST NOT hit rapla's /oauth2/token when a provider URL is stashed",
                0, refreshCallCount.get());
        assertNotNull("provider received a body", providerRefreshLastBody);
        assertTrue("provider body must carry grant_type=refresh_token: " + providerRefreshLastBody,
                providerRefreshLastBody.contains("grant_type=refresh_token"));
        assertTrue("provider body must carry the provider's client_id, not rapla-client: " + providerRefreshLastBody,
                providerRefreshLastBody.contains("client_id=keycloak-client"));
        assertFalse("provider body must NOT carry client_id=rapla-client: " + providerRefreshLastBody,
                providerRefreshLastBody.contains("client_id=rapla-client"));
        assertFalse("onAuthDead must NOT fire when provider refresh succeeded",
                authDeadFired.get());
        assertEquals("new access token from provider persisted",
                "kc-new-access", info.getAccessToken());
        assertEquals("new refresh token from provider persisted",
                "kc-new-refresh", info.getRefreshToken());
    }

    /**
     * When the provider refresh URL is set but the provider rejects the refresh
     * token (e.g. Keycloak session ended on its side), the interceptor must still
     * fire the auth-dead hook so the Swing client re-shows the login dialog.
     * Specifically must NOT fall back to rapla's {@code /oauth2/token} — once a
     * provider URL is stashed, rapla SAS is irrelevant for this session.
     */
    @Test
    public void whenProviderRefreshUrlIsSetAndProviderRejects_thenAuthDeadFiresWithoutHittingRaplaSas() throws Exception
    {
        info.setRefreshUrl(info.getServerURL() + "/provider/token");
        info.setOauthClientId("keycloak-client");
        providerRefreshResponseStatus = 400;
        providerRefreshResponseBody = "{\"error\":\"invalid_grant\"}";

        AtomicBoolean authDeadFired = new AtomicBoolean(false);
        info.setOnAuthDead(() -> authDeadFired.set(true));

        ClientHttpRequestFactory factory = new BufferingHttpUrlConnectionRequestFactory();
        ClientProxyConfig.RefreshOn401Interceptor interceptor =
                new ClientProxyConfig.RefreshOn401Interceptor(info, factory);

        invokeWithInterceptor(interceptor, factory,
                URI.create(info.getServerURL() + "/api/resources"), HttpMethod.GET);

        assertEquals("provider's token endpoint hit exactly once",
                1, providerRefreshCallCount.get());
        assertEquals("rapla's /oauth2/token must not be touched",
                0, refreshCallCount.get());
        assertTrue("onAuthDead must fire when provider refresh fails",
                authDeadFired.get());
        assertEquals("access token cleared", null, info.getAccessToken());
        assertEquals("refresh token cleared", null, info.getRefreshToken());
    }

    // Minimal execution chain — the interceptor delegates to an executor that
    // performs the actual HTTP call. We provide a trivial one that uses the
    // same request factory.
    private static ClientHttpResponse invokeWithInterceptor(
            ClientProxyConfig.RefreshOn401Interceptor interceptor,
            ClientHttpRequestFactory factory,
            URI uri, HttpMethod method) throws IOException
    {
        ClientHttpRequest request = factory.createRequest(uri, method);
        return interceptor.intercept(request, new byte[0], (req, body) -> {
            // Re-create the request fresh so the (possibly-rotated) bearer header is honored.
            ClientHttpRequest retry = factory.createRequest(req.getURI(), req.getMethod());
            retry.getHeaders().putAll(req.getHeaders());
            if (body != null && body.length > 0) retry.getBody().write(body);
            return retry.execute();
        });
    }
}
