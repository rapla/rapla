package org.rapla.client.spring;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbrm.RemoteConnectionInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies {@link ClientProxyConfig.OAuth2RemoteAuthentificationService} — the
 * post-PRD-041 replacement for the removed rapla-custom {@code POST /api/auth/login}.
 * The Swing fallback login dialog and {@code MyCustomConnector}'s password-reauth
 * path call this seam; it must speak the OAuth 2.0 token endpoint.
 */
@RunWith(JUnit4.class)
public class OAuth2RemoteAuthentificationServiceTest
{
    private HttpServer server;
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody =
            "{\"access_token\":\"acc-xyz\",\"refresh_token\":\"ref-abc\","
            + "\"token_type\":\"Bearer\",\"expires_in\":3599}";
    private RemoteConnectionInfo info;

    @Before
    public void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        });
        server.start();
        info = new RemoteConnectionInfo();
        info.setServerURL("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @After
    public void tearDown()
    {
        if (server != null) server.stop(0);
    }

    private RemoteAuthentificationService service()
    {
        return new ClientProxyConfig.OAuth2RemoteAuthentificationService(info);
    }

    @Test
    public void loginPostsPasswordGrantAndParsesTokens() throws Exception
    {
        LoginTokens tokens = service().login(new LoginCredentials("admin", "", null));

        String body = capturedBody.get();
        assertNotNull("token endpoint must have been called", body);
        assertTrue("password grant: " + body, body.contains("grant_type=password"));
        assertTrue("username sent: " + body, body.contains("username=admin"));
        assertTrue("empty password param sent: " + body, body.contains("&password=&"));
        assertTrue("public client id sent: " + body, body.contains("client_id=rapla-client"));

        assertEquals("acc-xyz", tokens.getAccessToken());
        assertEquals("ref-abc", tokens.getRefreshToken());
        assertEquals(3599L, tokens.getExpiresIn());
    }

    @Test
    public void refreshPostsRefreshTokenGrant() throws Exception
    {
        service().refresh(new RemoteAuthentificationService.RefreshRequest("ref-old"));

        String body = capturedBody.get();
        assertNotNull(body);
        assertTrue("refresh grant: " + body, body.contains("grant_type=refresh_token"));
        assertTrue("refresh token sent: " + body, body.contains("refresh_token=ref-old"));
    }

    @Test
    public void badCredentialsMapToSecurityException()
    {
        responseStatus = 400;
        responseBody = "{\"error\":\"invalid_grant\"}";
        try
        {
            service().login(new LoginCredentials("admin", "wrong", null));
            fail("expected RaplaSecurityException on HTTP 400");
        }
        catch (RaplaSecurityException expected)
        {
            // ok — the Swing login dialog surfaces this as a login failure
        }
        catch (Exception other)
        {
            fail("expected RaplaSecurityException, got " + other);
        }
    }

    @Test
    public void connectAsIsRejectedNotSilentlyDropped()
    {
        try
        {
            service().login(new LoginCredentials("admin", "", "someone-else"));
            fail("expected RaplaSecurityException — the password grant has no connectAs");
        }
        catch (RaplaSecurityException expected)
        {
            assertNull("server must not be contacted when connectAs is set", capturedBody.get());
        }
        catch (Exception other)
        {
            fail("expected RaplaSecurityException, got " + other);
        }
    }
}
