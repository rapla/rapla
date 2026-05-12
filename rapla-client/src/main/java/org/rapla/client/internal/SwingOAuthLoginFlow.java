package org.rapla.client.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.rapla.logger.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class SwingOAuthLoginFlow
{
    private static final String CALLBACK_PATH = "/login/oauth2/code/rapla";
    private static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final OAuthConfig config;
    private final Logger logger;
    private final HttpClient http;
    private final BrowserOpener browserOpener;
    private boolean forceLogin = false;

    public SwingOAuthLoginFlow(OAuthConfig config, Logger logger)
    {
        this(config, logger, (url, log) -> BrowserLauncher.open(url, log));
    }

    public SwingOAuthLoginFlow(OAuthConfig config, Logger logger, BrowserOpener browserOpener)
    {
        this.config = config;
        this.logger = logger;
        this.http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        this.browserOpener = browserOpener;
    }

    /**
     * Forces the IdP to re-authenticate the user even if a valid session cookie
     * is present, by adding {@code prompt=login} to the authorize URL. Used
     * after an explicit logout to defeat the race where the browser hasn't
     * finished clearing its session cookie before the new OAuth flow starts.
     * Standard OIDC parameter — supported by Spring Authorization Server,
     * Keycloak, Auth0, and friends.
     */
    public SwingOAuthLoginFlow forceLogin(boolean force)
    {
        this.forceLogin = force;
        return this;
    }

    @FunctionalInterface
    public interface BrowserOpener
    {
        void open(URI url, Logger logger) throws IOException;
    }

    public static final class Session
    {
        private final CompletableFuture<OAuthTokens> future;
        private final URI redirectUri;
        private final HttpClient http;

        Session(CompletableFuture<OAuthTokens> future, URI redirectUri, HttpClient http)
        {
            this.future = future;
            this.redirectUri = redirectUri;
            this.http = http;
        }

        public CompletableFuture<OAuthTokens> future() { return future; }

        public URI redirectUri() { return redirectUri; }

        /**
         * Re-issues the callback locally using the query parameters from a pasted URL.
         * Used when the system browser can't reach the loopback listener directly
         * (e.g. WSL2 NAT mode); the user pastes the URL from their browser and we
         * deliver it to the in-process listener.
         */
        public void deliverPasted(String pastedUrl) throws IOException, InterruptedException
        {
            String trimmed = pastedUrl == null ? "" : pastedUrl.trim();
            if (trimmed.isEmpty())
            {
                throw new IllegalArgumentException("URL is empty");
            }
            URI parsed = URI.create(trimmed);
            String query = parsed.getRawQuery();
            if (query == null || query.isEmpty())
            {
                throw new IllegalArgumentException("URL has no query string with code/state");
            }
            URI local = URI.create(redirectUri.toString() + "?" + query);
            http.send(HttpRequest.newBuilder(local).GET().timeout(java.time.Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }

    public Session start()
    {
        CompletableFuture<OAuthTokens> result = new CompletableFuture<>();
        final String verifier = PkceUtil.randomUrlSafe(32);
        final String challenge = PkceUtil.codeChallenge(verifier);
        final String state = PkceUtil.randomUrlSafe(16);

        // Under WSL2 NAT mode, the Windows-side browser can't reach the WSL2
        // listener on 127.0.0.1:<random-port> (NAT forwarder doesn't pick up
        // dynamically-bound ports reliably). Falls back to binding on 0.0.0.0
        // and advertising the WSL bridge IP (172.x.x.x) as the redirect host:
        // Windows routes directly to that IP via the Hyper-V vSwitch, bypassing
        // the forwarder. Requires the server to accept WSL bridge IPs in its
        // redirect validator — see AuthorizationServerConfig.
        final String wslIp = BrowserLauncher.isWsl() ? discoverWslBridgeIp(logger) : null;
        final String bindHost = wslIp != null ? "0.0.0.0" : "127.0.0.1";
        final String redirectHost = wslIp != null ? wslIp : "127.0.0.1";

        final HttpServer server;
        final int port;
        try
        {
            server = HttpServer.create(new InetSocketAddress(bindHost, 0), 0);
            port = server.getAddress().getPort();
        }
        catch (IOException e)
        {
            result.completeExceptionally(e);
            return new Session(result, URI.create("http://127.0.0.1:0" + CALLBACK_PATH), http);
        }
        final String redirectUri = "http://" + redirectHost + ":" + port + CALLBACK_PATH;

        server.createContext(CALLBACK_PATH, new CallbackHandler(state, verifier, redirectUri, result));
        server.setExecutor(null);
        server.start();
        if (logger != null) logger.info("OAuth loopback listener bound on " + redirectUri);

        result.whenComplete((tokens, err) -> server.stop(0));

        URI authorize = URI.create(buildAuthorizeUrl(redirectUri, challenge, state));
        try
        {
            browserOpener.open(authorize, logger);
        }
        catch (IOException e)
        {
            result.completeExceptionally(e);
            return new Session(result, URI.create(redirectUri), http);
        }

        scheduleTimeout(result);
        return new Session(result, URI.create(redirectUri), http);
    }

    private static String discoverWslBridgeIp(Logger logger)
    {
        try
        {
            // Read the WSL2 eth0 IP. `hostname -I` returns space-separated v4
            // addresses; the first is the bridge address Windows can route to.
            Process p = new ProcessBuilder("hostname", "-I").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();
            if (out.isEmpty()) return null;
            String first = out.split("\\s+")[0];
            // Must be in 172.16.0.0/12 to match the server's WSL-bridge allowance
            String[] octets = first.split("\\.");
            if (octets.length != 4) return null;
            int second = Integer.parseInt(octets[1]);
            if (!first.startsWith("172.") || second < 16 || second > 31)
            {
                if (logger != null) logger.debug("WSL bridge IP " + first + " is not in 172.16.0.0/12 — falling back to loopback");
                return null;
            }
            if (logger != null) logger.info("WSL detected; OAuth loopback will use bridge IP " + first);
            return first;
        }
        catch (Exception e)
        {
            if (logger != null) logger.debug("WSL bridge IP detection failed: " + e.getMessage());
            return null;
        }
    }

    private String buildAuthorizeUrl(String redirectUri, String challenge, String state)
    {
        StringBuilder url = new StringBuilder(config.getAuthorizeUrl());
        url.append('?').append("response_type=code");
        url.append('&').append("client_id=").append(enc(config.getClientId()));
        url.append('&').append("redirect_uri=").append(enc(redirectUri));
        url.append('&').append("code_challenge=").append(enc(challenge));
        url.append('&').append("code_challenge_method=S256");
        url.append('&').append("state=").append(enc(state));
        if (config.getScopes() != null && !config.getScopes().isEmpty())
        {
            url.append('&').append("scope=").append(enc(String.join(" ", config.getScopes())));
        }
        if (forceLogin)
        {
            // OIDC: force the IdP to re-authenticate the user regardless of an
            // existing session cookie. Used post-logout to avoid the silent-cookie-reuse race.
            url.append('&').append("prompt=login");
        }
        return url.toString();
    }

    private void scheduleTimeout(CompletableFuture<OAuthTokens> result)
    {
        CompletableFuture.delayedExecutor(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (!result.isDone())
                    {
                        result.completeExceptionally(new TimeoutException("OAuth login timed out after " + CALLBACK_TIMEOUT));
                    }
                });
    }

    private final class CallbackHandler implements HttpHandler
    {
        private final String expectedState;
        private final String verifier;
        private final String redirectUri;
        private final CompletableFuture<OAuthTokens> result;

        CallbackHandler(String expectedState, String verifier, String redirectUri, CompletableFuture<OAuthTokens> result)
        {
            this.expectedState = expectedState;
            this.verifier = verifier;
            this.redirectUri = redirectUri;
            this.result = result;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String error = params.get("error");
            if (error != null)
            {
                respond(exchange, 400, "Login failed: " + escape(error) + " — you can close this tab.");
                result.completeExceptionally(new IllegalStateException("OAuth error: " + error));
                return;
            }
            String code = params.get("code");
            String returnedState = params.get("state");
            if (code == null || returnedState == null || !expectedState.equals(returnedState))
            {
                respond(exchange, 400, "Invalid OAuth callback. You can close this tab.");
                result.completeExceptionally(new IllegalStateException("OAuth callback missing code or state mismatch"));
                return;
            }
            respond(exchange, 200, "Signed in. You can close this tab.");
            try
            {
                OAuthTokens tokens = exchangeCodeForTokens(code);
                result.complete(tokens);
            }
            catch (Exception e)
            {
                result.completeExceptionally(e);
            }
        }

        private OAuthTokens exchangeCodeForTokens(String code) throws IOException, InterruptedException
        {
            String body = "grant_type=authorization_code"
                    + "&code=" + enc(code)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&client_id=" + enc(config.getClientId())
                    + "&code_verifier=" + enc(verifier);
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.getTokenUrl()))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2)
            {
                throw new IOException("Token exchange failed (" + resp.statusCode() + "): " + resp.body());
            }
            JsonNode tree = JSON.readTree(resp.body());
            JsonNode access = tree.get("access_token");
            if (access == null)
            {
                throw new IOException("Token response missing access_token: " + resp.body());
            }
            JsonNode refresh = tree.get("refresh_token");
            JsonNode expires = tree.get("expires_in");
            if (logger != null)
            {
                logger.info("token endpoint returned: keys=" + tree.propertyNames()
                        + (refresh == null ? " (NO refresh_token)" : " (refresh_token present)"));
            }
            return new OAuthTokens(
                    access.asString(),
                    refresh != null ? refresh.asString() : null,
                    expires != null ? expires.asLong() : 0L);
        }
    }

    private static void respond(HttpExchange exchange, int code, String message) throws IOException
    {
        byte[] body = htmlPage(message).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(body);
        }
    }

    private static String htmlPage(String message)
    {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Rapla login</title></head>"
                + "<body style=\"font-family:sans-serif;padding:2em;\">"
                + "<h2>Rapla</h2><p>" + message + "</p></body></html>";
    }

    private static Map<String, String> parseQuery(String raw)
    {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&"))
        {
            int eq = pair.indexOf('=');
            if (eq < 0)
            {
                out.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
            else
            {
                out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String enc(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escape(String s)
    {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
