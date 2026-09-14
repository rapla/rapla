package org.rapla.storage.dbrm;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.storage.RaplaSecurityException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies {@link OAuth2PasswordLogin} — the post-PRD-041 replacement for the
 * removed rapla-custom {@code POST /api/auth/login}. The Swing legacy dialog
 * Login button and {@code ClientFacadeImpl.login(String, char[])} call this
 * seam; it must speak the OAuth 2.0 password grant.
 *
 * <p>PRD 029 Phase 5 (2026-05-25): renamed from
 * {@code OAuth2RemoteAuthentificationServiceTest} when the
 * {@code RemoteAuthentificationService} interface was dropped (single
 * concrete impl, no plugin point).
 */
@RunWith(JUnit4.class)
public class OAuth2PasswordLoginTest
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

    /** i18n is only used for the connection-error message; safe to pass null
     *  when the test doesn't exercise that branch. */
    private OAuth2PasswordLogin seam()
    {
        return new OAuth2PasswordLogin(info, null);
    }

    @Test
    public void loginPostsPasswordGrantAndParsesTokens() throws Exception
    {
        LoginTokens tokens = seam().login(new LoginCredentials("admin", new char[0]));

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
    public void badCredentialsMapToSecurityExceptionPreservingServerBody()
    {
        responseStatus = 400;
        responseBody = "Login failed!";
        try
        {
            seam().login(new LoginCredentials("admin", "wrong".toCharArray()));
            fail("expected RaplaSecurityException on HTTP 400");
        }
        catch (RaplaSecurityException expected)
        {
            // PRD 029 Phase 5 — server's localised body is surfaced so the
            // dialog shows the right message (instead of a hardcoded English
            // "Login failed." regardless of locale).
            assertTrue("must preserve server body: " + expected.getMessage(),
                    expected.getMessage().contains("Login failed!"));
        }
        catch (Exception other)
        {
            fail("expected RaplaSecurityException, got " + other);
        }
    }
}
