package org.rapla.client.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

/**
 * PRD 131 D10: the server rejects every impersonation request made with a token carrying an {@code act} claim, so
 * "switch user" while impersonating (A -> B -> C) must authenticate as the actor A, never with the impersonation token.
 */
class RefreshOn401InterceptorImpersonateTest
{
    private HttpServer server;
    private RemoteConnectionInfo info;
    private final List<String> impersonateAuth = Collections.synchronizedList(new ArrayList<>());
    private final List<String> resourceAuth = Collections.synchronizedList(new ArrayList<>());
    private final ClientHttpRequestFactory factory = new BufferingHttpUrlConnectionRequestFactory();

    private static void reply(HttpExchange exchange, int status, String body) throws IOException
    {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
        if (out.length > 0) try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        exchange.close();
    }

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/resources", exchange -> {
            resourceAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, 200, "{}");
        });
        server.createContext("/api/auth/impersonate", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            impersonateAuth.add(auth);
            if ("Bearer actor-fresh".equals(auth) || "Bearer actor-live".equals(auth)) reply(exchange, 200, "{\"access_token\":\"imp-c\"}");
            else reply(exchange, 401, "");
        });
        server.createContext("/oauth2/token", exchange ->
                reply(exchange, 200, "{\"access_token\":\"actor-fresh\",\"refresh_token\":\"actor-refresh\"}"));
        server.start();
        info = new RemoteConnectionInfo();
        info.setServerURL("http://127.0.0.1:" + server.getAddress().getPort());
        info.setRefreshToken("actor-refresh");
        info.setImpersonationAccessToken("imp-b", "bob");
    }

    @AfterEach
    void tearDown()
    {
        server.stop(0);
    }

    private ClientHttpResponse call(String path, HttpMethod method) throws IOException
    {
        ClientProxyConfig.RefreshOn401Interceptor interceptor = new ClientProxyConfig.RefreshOn401Interceptor(info, factory);
        ClientHttpRequest request = factory.createRequest(URI.create(info.getServerURL() + path), method);
        return interceptor.intercept(request, new byte[0], (req, body) -> {
            ClientHttpRequest retry = factory.createRequest(req.getURI(), req.getMethod());
            retry.getHeaders().putAll(req.getHeaders());
            if (body != null && body.length > 0) retry.getBody().write(body);
            return retry.execute();
        });
    }

    @Test
    void ordinaryRequestsWhileImpersonatingCarryTheImpersonationToken() throws IOException
    {
        info.setAccessToken("actor-live");
        call("/api/resources", HttpMethod.GET).close();
        assertEquals(List.of("Bearer imp-b"), resourceAuth);
    }

    @Test
    void switchUserWhileImpersonatingCarriesTheActorsToken() throws IOException
    {
        info.setAccessToken("actor-live");
        try (ClientHttpResponse resp = call("/api/auth/impersonate", HttpMethod.POST))
        {
            assertEquals(200, resp.getStatusCode().value());
        }
        assertEquals(List.of("Bearer actor-live"), impersonateAuth);
    }

    @Test
    void switchUserWithAnExpiredActorTokenRefreshesTheActorAndRetriesWithIt() throws IOException
    {
        info.setAccessToken("actor-old");
        try (ClientHttpResponse resp = call("/api/auth/impersonate", HttpMethod.POST))
        {
            assertEquals(200, resp.getStatusCode().value());
        }
        assertEquals(List.of("Bearer actor-old", "Bearer actor-fresh"), impersonateAuth);
        assertEquals("actor-fresh", info.getAccessToken());
        assertEquals("imp-b", info.getImpersonationAccessToken(), "the running impersonation is untouched until the switch itself succeeds");
    }
}
