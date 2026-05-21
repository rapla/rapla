package org.rapla.client.spring;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
