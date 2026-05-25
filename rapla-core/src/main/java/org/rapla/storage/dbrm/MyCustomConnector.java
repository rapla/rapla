package org.rapla.storage.dbrm;

import org.rapla.RaplaResources;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.rest.SerializableExceptionInformation;
import org.rapla.rest.client.AuthenticationException;
import org.rapla.rest.client.CustomConnector;
import org.rapla.rest.client.RemoteConnectException;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaInvalidTokenException;
import org.rapla.storage.RaplaSecurityException;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;

public class MyCustomConnector implements CustomConnector
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MyCustomConnector.class);

    private final RemoteConnectionInfo remoteConnectionInfo;
    private final TokenStore tokenStore;
    //private final String errorString;
    private final CommandScheduler commandQueue;
    Supplier<RaplaResources> i18n;

    @Autowired public MyCustomConnector(RemoteConnectionInfo remoteConnectionInfo, Supplier<RaplaResources> i18n,
            CommandScheduler commandQueue, TokenStore tokenStore)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.tokenStore = tokenStore == null ? TokenStores.noOp() : tokenStore;
        this.commandQueue = commandQueue;
        this.i18n = i18n;
    }


    @Override public String reauth(Class proxy) throws Exception
    {
        // PRD 029 Phase 5 (2026-05-25): RemoteAuthentificationService was
        // dropped. The legacy guard against recursive reauth-on-auth-proxy is
        // no-op now — OAuth2PasswordLogin doesn't go through the RPC proxy
        // mechanism (it builds its own HTTP request directly). Kept as a
        // belt-and-suspenders check against any future seam that gets RPC-proxied.
        if (proxy.getCanonicalName().contains(OAuth2PasswordLogin.class.getCanonicalName()))
        {
            return null;
        }

        // PRD 051 — step 0: impersonation renewal. If an impersonation
        // is active, the 401 we just got is for the impersonation token;
        // try to renew it via /api/auth/impersonate with the admin's
        // Bearer. On success return the new impersonation token; on
        // failure fall into the existing refresh-then-password chain
        // (which renews the admin Bearer, after which the next 401
        // round-trip will retry impersonation step 0).
        if (remoteConnectionInfo.isImpersonating())
        {
            try
            {
                String renewed = renewImpersonationToken();
                if (renewed != null)
                {
                    return renewed;
                }
            }
            catch (Exception ex)
            {
                LOGGER.info("impersonation renewal failed ({}), falling back to admin refresh", ex.getMessage());
            }
        }

        // Refresh-token reauth — works for both password and OAuth-logged-in
        // sessions because OAuth2 password grant returns a refresh token too.
        // No password fallback: when the refresh fails (expired, revoked, server
        // hash-store wiped), surface session-expired and let the higher layers
        // re-show the login dialog. Caching the password to silently re-login
        // gained ~one corner case (refresh expired + password unchanged + server
        // still up) at the cost of keeping cleartext in JVM memory for the full
        // session lifetime — not worth it. Removed 2026-05-25.
        final String refreshToken = remoteConnectionInfo.getRefreshToken();
        if (refreshToken != null && !refreshToken.isEmpty())
        {
            try
            {
                String newAccessToken = refreshUsingToken(refreshToken);
                if (newAccessToken != null) return newAccessToken;
            }
            catch (Exception refreshFailed)
            {
                LOGGER.info("refresh-token reauth failed ({}) — session expired", refreshFailed.getMessage());
            }
        }
        throw new RaplaSecurityException("Your session has expired. Please sign in again.");
    }

    /**
     * Calls the OAuth2 token endpoint with the stored refresh token and
     * stashes the resulting access + refresh tokens on {@link #remoteConnectionInfo}.
     *
     * <p>PRD 041: refresh uses the standard {@code grant_type=refresh_token}
     * form-encoded body. PRD 029 Phase 4: the endpoint + client_id are taken
     * from {@link RemoteConnectionInfo} when a browser-OAuth provider was used
     * — for a secret-backed provider like Keycloak that is the BFF URL
     * ({@code /api/auth/oauth/exchange/{id}}), which injects the server-held
     * {@code client_secret}. For a rapla-SAS / password session those fields
     * are null and we fall back to {@code serverURL + /oauth2/token} with
     * {@code client_id=rapla-client}.
     *
     * @return the new access token, or null if refresh isn't available
     */
    private String refreshUsingToken(String refreshToken) throws Exception
    {
        String url = remoteConnectionInfo.getRefreshUrl();
        if (url == null || url.isEmpty())
        {
            String serverUrl = remoteConnectionInfo.getServerURL();
            if (serverUrl == null || serverUrl.isEmpty()) return null;
            url = serverUrl + "/oauth2/token";
        }
        String clientId = remoteConnectionInfo.getOauthClientId();
        if (clientId == null || clientId.isEmpty()) clientId = "rapla-client";
        String encodedRefresh = java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8);
        String body = "grant_type=refresh_token&refresh_token=" + encodedRefresh
                + "&client_id=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8);
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                .build();
        java.net.http.HttpResponse<String> resp = http.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2)
        {
            throw new RaplaException("refresh failed (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        String respBody = resp.body();
        String newAccess = extractJsonString(respBody, "accessToken");
        if (newAccess == null) newAccess = extractJsonString(respBody, "access_token");
        if (newAccess == null) throw new RaplaException("refresh response missing accessToken: " + respBody);
        String newRefresh = extractJsonString(respBody, "refreshToken");
        if (newRefresh == null) newRefresh = extractJsonString(respBody, "refresh_token");
        remoteConnectionInfo.setAccessToken(newAccess);
        if (newRefresh != null)
        {
            remoteConnectionInfo.setRefreshToken(newRefresh);
            tokenStore.tryWrite(newRefresh);
        }
        LOGGER.info("refresh-token reauth succeeded against {}", url);
        return newAccess;
    }

    /**
     * PRD 051 — call {@code POST /api/auth/impersonate} with the admin's
     * current Bearer to mint a fresh impersonation access token. On
     * success, stashes the new token on {@link #remoteConnectionInfo}
     * and returns it; the caller (the {@code reauth} chain) treats it
     * as the new Bearer to retry the failing request with. On failure
     * (no admin Bearer, no target name, non-2xx response), returns
     * null so the caller falls through to admin-refresh.
     */
    private String renewImpersonationToken() throws Exception
    {
        String adminToken = remoteConnectionInfo.getAccessToken();
        if (adminToken == null || adminToken.isEmpty()) return null;
        String target = remoteConnectionInfo.getImpersonationTargetUsername();
        if (target == null || target.isEmpty()) return null;
        String serverUrl = remoteConnectionInfo.getServerURL();
        if (serverUrl == null || serverUrl.isEmpty()) return null;
        String trimmed = serverUrl.endsWith("/")
                ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String url = trimmed + "/api/auth/impersonate";
        String body = "target_username="
                + java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8);
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        body, java.nio.charset.StandardCharsets.UTF_8))
                .build();
        java.net.http.HttpResponse<String> resp = http.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) return null;
        String respBody = resp.body();
        String newToken = extractJsonString(respBody, "access_token");
        if (newToken == null) return null;
        remoteConnectionInfo.setImpersonationAccessToken(newToken, target);
        LOGGER.info("impersonation renewal succeeded (target={})", target);
        return newToken;
    }

    /** Minimal JSON field extractor — both rapla and OAuth refresh responses
     *  have flat top-level shape so we can avoid pulling in a JSON dependency. */
    private static String extractJsonString(String body, String field)
    {
        String marker = "\"" + field + "\"";
        int i = body.indexOf(marker);
        if (i < 0) return null;
        int colon = body.indexOf(':', i + marker.length());
        if (colon < 0) return null;
        int firstQuote = body.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int closingQuote = body.indexOf('"', firstQuote + 1);
        while (closingQuote > 0 && body.charAt(closingQuote - 1) == '\\')
        {
            closingQuote = body.indexOf('"', closingQuote + 1);
        }
        if (closingQuote < 0) return null;
        return body.substring(firstQuote + 1, closingQuote);
    }

    @Override public Exception deserializeException(SerializableExceptionInformation exe, int statusCode)

    {
        final String message = exe.getMessage();
        final String exceptionClass = exe.getExceptionClass();
        if ( exceptionClass == null)
        {
            return new RaplaException("An error occured. No additinal Exception information: "+ message);
        }
        if (exceptionClass.equals(RaplaInvalidTokenException.class.getName()))
        {
            return new AuthenticationException(message);
        }
        if ( exceptionClass.equals(RemoteConnectException.class.getName()))
        {
            String server = remoteConnectionInfo.getServerURL();
            final RaplaResources raplaResources = i18n.get();
            String errorString = raplaResources.format("error.connect", server) + " ";
            return new RaplaConnectException(errorString + exe.getMessage());
        }
        final RaplaExceptionDeserializer raplaExceptionDeserializer = new RaplaExceptionDeserializer();
        RaplaException ex = raplaExceptionDeserializer.deserializeException(exe,statusCode);
        return ex;
    }


    @Override
    public <T> CompletablePromise<T> createCompletable()
    {
        return commandQueue.createCompletable();
    }

    @Override
    public <T> Promise<T> call(CommandScheduler.Callable<T> callable)
    {
        return commandQueue.supply( callable);
    }
    /*
    public Exception getConnectError(IOException ex)
    {
        String server = remoteConnectionInfo.getServerURL();
        final RaplaResources raplaResources = i18n.get();
        String errorString = raplaResources.format("error.connect", server) + " ";
        return new RaplaConnectException(errorString + ex.getMessage());
    }
    */

    @Override public String getAccessToken()
    {
        // PRD 051 — use the effective Bearer (impersonation if active,
        // else admin's). The reauth() chain reads getAccessToken() on
        // RemoteConnectionInfo directly when it needs the admin token
        // for the renewal call.
        return remoteConnectionInfo.getEffectiveAccessToken();
    }

    @Override
    public String getFullQualifiedUrl(String relativePath)
    {
        return remoteConnectionInfo.getServerURL() + "/" + relativePath;
    }

}
