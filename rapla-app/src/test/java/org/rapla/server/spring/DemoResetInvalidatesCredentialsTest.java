package org.rapla.server.spring;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 118 D8-9 — the nightly reset (stop, copy the seed over the data file, start) invalidates every
 * credential issued during the day: access token, refresh token, remember-me cookie and API key. Real
 * server restarts on a random port, {@code demo} profile. Controls: a restart WITHOUT the copy keeps all
 * four valid (so the rejection is the reset's doing), and a seed that still carries a signing key keeps
 * access tokens alive across the reset (so the seed must be scrubbed).
 */
@Tag("e2e")
class DemoResetInvalidatesCredentialsTest
{
    @TempDir Path tempDir;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    record Server(ConfigurableApplicationContext context, String base) implements AutoCloseable
    {
        @Override
        public void close()
        {
            context.close();
        }
    }

    record Credentials(String accessToken, String refreshToken, String rememberMe, String apiKey, String kid) { }

    record Accepted(boolean access, boolean refresh, boolean rememberMe, boolean apiKey)
    {
        static final Accepted ALL = new Accepted(true, true, true, true);
        static final Accepted NONE = new Accepted(false, false, false, false);
    }

    private Server start(Path dataFile)
    {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(RaplaSpringBootApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("demo")
                .run("--server.port=0", "--rapla.file-datasources.raplafile=" + dataFile.toAbsolutePath(),
                        "--rapla.patch-dir=" + tempDir.resolve("patch").toAbsolutePath());
        String loaded = context.getEnvironment().getProperty("rapla.file-datasources.raplafile");
        if (!dataFile.toAbsolutePath().toString().equals(loaded))
        {
            context.close();
            throw new IllegalStateException("test server must run on its temp data file, not " + loaded);
        }
        return new Server(context, "http://localhost:" + context.getEnvironment().getProperty("local.server.port"));
    }

    private static Map<String, String> cookies(HttpResponse<?> response)
    {
        Map<String, String> out = new LinkedHashMap<>();
        for (String header : response.headers().allValues("Set-Cookie"))
        {
            String pair = header.split(";", 2)[0];
            int eq = pair.indexOf('=');
            out.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return out;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException
    {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String kid(Server server) throws Exception
    {
        String jwks = send(HttpRequest.newBuilder(URI.create(server.base + "/oauth2/jwks")).build()).body();
        Matcher m = Pattern.compile("\"kid\"\\s*:\\s*\"([^\"]+)\"").matcher(jwks);
        assertEquals(true, m.find(), jwks);
        return m.group(1);
    }

    private Credentials issue(Server server) throws Exception
    {
        HttpResponse<String> page = send(HttpRequest.newBuilder(URI.create(server.base + "/login")).build());
        String xsrf = cookies(page).get("XSRF-TOKEN");
        Matcher field = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertEquals(true, field.find(), "login page must carry the CSRF field");
        String form = "username=homer&password=duffs&remember-me=on&_csrf=" + URLEncoder.encode(field.group(1), StandardCharsets.UTF_8);
        HttpResponse<String> login = send(HttpRequest.newBuilder(URI.create(server.base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Cookie", "XSRF-TOKEN=" + xsrf)
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        Map<String, String> issued = cookies(login);
        String access = issued.get("access_token");
        String refresh = issued.get("refresh_token");
        String rememberMe = issued.get("rapla-remember-me");
        assertNotNull(access, login.statusCode() + " " + login.headers().firstValue("Location").orElse("-") + " "
                + login.headers().allValues("Set-Cookie").stream().map(c -> c.replaceAll("=[^;]{12,}", "=…")).toList());
        assertNotNull(refresh, issued.keySet().toString());
        assertNotNull(rememberMe, issued.keySet().toString());

        HttpResponse<String> created = send(HttpRequest.newBuilder(URI.create(server.base + "/api/auth/api-keys"))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", xsrf)
                .header("Cookie", "access_token=" + access + "; XSRF-TOKEN=" + xsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"label\":\"reset\",\"scopes\":[\"read\"]}")).build());
        Matcher key = Pattern.compile("\"key\"\\s*:\\s*\"([^\"]+)\"").matcher(created.body());
        assertEquals(true, key.find(), created.statusCode() + " " + created.body());
        return new Credentials(access, refresh, rememberMe, key.group(1), kid(server));
    }

    /** Persistent remember-me tokens rotate on every use: the probe must continue with the newest cookie. */
    private String rememberMe;

    private Accepted check(Server server, Credentials credentials) throws Exception
    {
        if (rememberMe == null)
        {
            rememberMe = credentials.rememberMe;
        }
        int access = send(HttpRequest.newBuilder(URI.create(server.base + "/api/auth/me"))
                .header("Cookie", "access_token=" + credentials.accessToken).build()).statusCode();
        int apiKey = send(HttpRequest.newBuilder(URI.create(server.base + "/api/auth/me"))
                .header("Authorization", "Bearer " + credentials.apiKey).build()).statusCode();
        HttpResponse<String> page = send(HttpRequest.newBuilder(URI.create(server.base + "/login")).build());
        String xsrf = cookies(page).get("XSRF-TOKEN");
        int refresh = send(HttpRequest.newBuilder(URI.create(server.base + "/api/auth/session/refresh"))
                .header("X-XSRF-TOKEN", xsrf).header("Cookie", "refresh_token=" + credentials.refreshToken + "; XSRF-TOKEN=" + xsrf)
                .POST(HttpRequest.BodyPublishers.noBody()).build()).statusCode();
        boolean rememberMeAccepted = authenticatedPage(server, "rapla-remember-me=" + rememberMe);
        return new Accepted(access == 200, refresh == 200, rememberMeAccepted, apiKey == 200);
    }

    /** {@code /graphiql/} needs a login: anonymous requests are sent to {@code /login}. */
    private boolean authenticatedPage(Server server, String cookie) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.base + "/graphiql/"));
        if (cookie != null)
        {
            request.header("Cookie", cookie);
        }
        HttpResponse<String> response = send(request.build());
        String rotated = cookies(response).get("rapla-remember-me");
        if (cookie != null && rotated != null && !rotated.isEmpty())
        {
            rememberMe = rotated;
        }
        String location = response.headers().firstValue("Location").orElse("");
        return response.statusCode() < 400 && !location.contains("/login");
    }

    private Path seed(boolean scrubbed) throws IOException
    {
        String fixture = DemoSeedCredentialScanTest.fixture();
        Path seed = tempDir.resolve(scrubbed ? "seed-scrubbed.xml" : "seed-keyed.xml");
        Files.writeString(seed, scrubbed ? DemoSeedCredentialScanTest.scrub(fixture) : fixture);
        assertEquals(scrubbed, DemoSeedCredentialScanTest.credentialEntries(Files.readString(seed)).isEmpty());
        return seed;
    }

    private void reset(Path seed, Path dataFile) throws IOException
    {
        Files.copy(seed, dataFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void credentialsFromBeforeTheResetAreRejectedAfterIt() throws Exception
    {
        Path seed = seed(true);
        Path dataFile = tempDir.resolve("data.xml");
        reset(seed, dataFile);
        Credentials credentials;
        try (Server day = start(dataFile))
        {
            credentials = issue(day);
            assertEquals(false, authenticatedPage(day, null), "anonymous /graphiql/ must be sent to the login page");
            assertEquals(Accepted.ALL, check(day, credentials), "issued credentials must work on the same day");
        }
        try (Server restartedWithoutReset = start(dataFile))
        {
            assertEquals(credentials.kid, kid(restartedWithoutReset), "a plain restart keeps the generated key");
            assertEquals(Accepted.ALL, check(restartedWithoutReset, credentials), "a plain restart invalidates nothing");
        }
        reset(seed, dataFile);
        try (Server nextDay = start(dataFile))
        {
            assertNotEquals(credentials.kid, kid(nextDay), "a start from the scrubbed seed generates a new signing key");
            assertEquals(Accepted.NONE, check(nextDay, credentials));
        }
    }

    @Test
    void aSeedThatCarriesASigningKeyKeepsAccessTokensAliveAcrossTheReset() throws Exception
    {
        Path seed = seed(false);
        Path dataFile = tempDir.resolve("data.xml");
        reset(seed, dataFile);
        Credentials credentials;
        try (Server day = start(dataFile))
        {
            credentials = issue(day);
        }
        reset(seed, dataFile);
        try (Server nextDay = start(dataFile))
        {
            assertEquals(credentials.kid, kid(nextDay));
            assertEquals(new Accepted(true, false, false, false), check(nextDay, credentials),
                    "only the stateless access token survives a keyed seed; the stored ones are wiped by the copy");
        }
    }
}
