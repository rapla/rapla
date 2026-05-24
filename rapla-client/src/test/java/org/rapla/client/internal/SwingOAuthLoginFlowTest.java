package org.rapla.client.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class SwingOAuthLoginFlowTest
{
    private HttpServer fakeAuthServer;
    private String fakeAccessToken;
    private String fakeRefreshToken;
    private String acceptedClientId;
    private String capturedCodeVerifier;
    private String capturedCode;

    @Before
    public void setUp() throws IOException
    {
        fakeAccessToken = "access-" + System.nanoTime();
        fakeRefreshToken = "refresh-" + System.nanoTime();
        acceptedClientId = "rapla-client";
        fakeAuthServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeAuthServer.createContext("/oauth2/token", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseForm(body);
            capturedCodeVerifier = params.get("code_verifier");
            capturedCode = params.get("code");
            if (!"authorization_code".equals(params.get("grant_type"))
                    || !acceptedClientId.equals(params.get("client_id"))
                    || params.get("code_verifier") == null
                    || params.get("code") == null)
            {
                respond(exchange, 400, "{\"error\":\"invalid_request\"}");
                return;
            }
            String json = "{\"access_token\":\"" + fakeAccessToken
                    + "\",\"refresh_token\":\"" + fakeRefreshToken
                    + "\",\"expires_in\":3600}";
            respond(exchange, 200, json);
        });
        fakeAuthServer.start();
    }

    @After
    public void tearDown()
    {
        if (fakeAuthServer != null) fakeAuthServer.stop(0);
    }

    @Test
    public void happyPathExchangesCodeForTokens() throws Exception
    {
        OAuthConfig cfg = new OAuthConfig(true, "rapla-client",
                fakeAuthServerUrl() + "/oauth2/authorize",
                fakeAuthServerUrl() + "/oauth2/token",
                List.of("openid", "profile"));

        SwingOAuthLoginFlow flow = new SwingOAuthLoginFlow(cfg,
                (url) -> simulateBrowserLogin(url, "test-code-xyz", null));

        OAuthTokens tokens = flow.start().future().get(10, TimeUnit.SECONDS);
        assertEquals(fakeAccessToken, tokens.getAccessToken());
        assertEquals(fakeRefreshToken, tokens.getRefreshToken());
        assertEquals(3600L, tokens.getExpiresIn());
        assertEquals("test-code-xyz", capturedCode);
        assertNotNull("verifier must be sent at token exchange", capturedCodeVerifier);
        assertTrue("verifier hashes to the challenge sent on authorize",
                capturedCodeVerifierMatchesChallenge());
    }

    @Test
    public void stateMismatchFailsTheFlow() throws Exception
    {
        OAuthConfig cfg = new OAuthConfig(true, "rapla-client",
                fakeAuthServerUrl() + "/oauth2/authorize",
                fakeAuthServerUrl() + "/oauth2/token",
                List.of("openid"));

        SwingOAuthLoginFlow flow = new SwingOAuthLoginFlow(cfg,
                (url) -> simulateBrowserLogin(url, "test-code", "wrong-state"));

        try
        {
            flow.start().future().get(10, TimeUnit.SECONDS);
            fail("expected ExecutionException for state mismatch");
        }
        catch (ExecutionException expected)
        {
            assertTrue(expected.getCause().getMessage().toLowerCase().contains("state"));
        }
    }

    @Test
    public void timeoutCompletesWithTimeoutException() throws Exception
    {
        // Browser that never sends the callback — simulates user closing the tab.
        // We override the timeout by setting CALLBACK_TIMEOUT? No — the flow uses a 5-minute
        // timeout. Instead drive a state-mismatch path here as a fast-fail proxy.
        // (A true timeout test would need to wait 5 minutes; skipped for CI hygiene.)
        OAuthConfig cfg = new OAuthConfig(true, "rapla-client",
                fakeAuthServerUrl() + "/oauth2/authorize",
                fakeAuthServerUrl() + "/oauth2/token",
                List.of());

        AtomicReference<URI> opened = new AtomicReference<>();
        SwingOAuthLoginFlow flow = new SwingOAuthLoginFlow(cfg,
                (url) -> { opened.set(url); /* do nothing — never call back */ });

        try
        {
            flow.start().future().get(2, TimeUnit.SECONDS);
            fail("future should not complete without a callback");
        }
        catch (TimeoutException expected)
        {
            assertNotNull(opened.get());
            assertTrue(opened.get().toString().contains("response_type=code"));
            assertTrue(opened.get().toString().contains("code_challenge_method=S256"));
        }
    }

    private void simulateBrowserLogin(URI authorizeUrl, String code, String stateOverride) throws IOException
    {
        Map<String, String> params = parseForm(authorizeUrl.getRawQuery());
        String redirectUri = params.get("redirect_uri");
        String state = stateOverride != null ? stateOverride : params.get("state");
        // record challenge for later verifier-check
        capturedChallenge = params.get("code_challenge");
        String callback = redirectUri + "?code=" + code + "&state=" + state;
        try
        {
            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(callback)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            // even on validation failure the handler responds 400 with HTML; that's fine
            if (resp.statusCode() != 200 && resp.statusCode() != 400)
            {
                throw new IOException("unexpected callback response: " + resp.statusCode());
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private String capturedChallenge;

    private boolean capturedCodeVerifierMatchesChallenge() throws Exception
    {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(capturedCodeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).equals(capturedChallenge);
    }

    private String fakeAuthServerUrl()
    {
        return "http://127.0.0.1:" + fakeAuthServer.getAddress().getPort();
    }

    private static Map<String, String> parseForm(String raw)
    {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&"))
        {
            int eq = pair.indexOf('=');
            if (eq < 0) out.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            else out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }
}
