package org.rapla.storage.dbrm;

import org.rapla.RaplaResources;
import org.rapla.framework.RaplaException;
import org.rapla.storage.RaplaSecurityException;

/**
 * The OAuth2 password-grant seam — converts {@link LoginCredentials} (username
 * + {@code char[]} password) into a {@link LoginTokens} response by POSTing
 * {@code grant_type=password} to {@code /oauth2/token} (RFC 6749 §4.3,
 * served by Spring Authorization Server). Used by the legacy Swing dialog
 * Login button and by {@code ClientFacadeImpl.login(String, char[])}
 * (test-only bootstrap).
 *
 * <p>PRD 029 Phase 5 (2026-05-25): no interface. One method, one impl, one
 * deployment shape — dropped the {@code RemoteAuthentificationService}
 * interface that wrapped this class. Spring autowires by concrete type.
 *
 * <p>The base URL is read from {@link RemoteConnectionInfo} per call (set
 * after context refresh by {@code RaplaClientServiceImpl}).
 *
 * <p>Refresh-token reauth does NOT live here — {@code MyCustomConnector.refreshUsingToken()}
 * and {@code ClientProxyConfig.RefreshOn401Interceptor.doRefresh()} own that
 * path so they can route refreshes to the provider's token endpoint
 * (Keycloak / Entra / Google / BFF) when an external IdP was used.
 *
 * <p>No JSON library here on purpose — the token response is flat, so two tiny
 * field extractors avoid dragging a mapper into this seam.
 */
public final class OAuth2PasswordLogin
{
    private final RemoteConnectionInfo info;
    private final RaplaResources i18n;

    public OAuth2PasswordLogin(RemoteConnectionInfo info, RaplaResources i18n)
    {
        this.info = info;
        this.i18n = i18n;
    }

    public LoginTokens login(LoginCredentials credentials) throws RaplaException
    {
        // String'ify the char[] only at the URL-encode boundary so the
        // plaintext lives on the heap for the duration of one HTTP call,
        // not the duration of the session. URLEncoder takes String — there
        // is no char[]-native equivalent in the JDK. The transient String
        // becomes GC-eligible the moment tokenRequest() returns.
        String body = "grant_type=password"
                + "&username=" + enc(credentials.getUsername())
                + "&password=" + (credentials.getPassword() == null ? "" : enc(new String(credentials.getPassword())))
                + "&client_id=rapla-client";
        return tokenRequest(body);
    }

    private LoginTokens tokenRequest(String body) throws RaplaException
    {
        String serverUrl = info.getServerURL();
        if (serverUrl == null || serverUrl.isEmpty())
        {
            throw new RaplaException("Server URL not set — cannot reach the OAuth2 token endpoint.");
        }
        String trimmed = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String url = trimmed + "/oauth2/token";
        java.net.http.HttpResponse<String> resp;
        try
        {
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10)).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                            body, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString(
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (java.io.IOException ex)
        {
            String errorString = i18n.format("error.connect", serverUrl);
            throw new RaplaConnectException(errorString + " " + ex.getMessage());
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new RaplaException("Interrupted contacting the OAuth2 token endpoint at " + url, ex);
        }
        int status = resp.statusCode();
        if (status == 400 || status == 401)
        {
            // OAuth2 invalid_grant / invalid credentials. Surface the server's
            // localised body so the user sees the right message (e.g. "Login
            // failed!" / "Login fehlgeschlagen!" instead of just "401"). Falls
            // back to "Login failed." if the body is empty/missing.
            String respBody = resp.body();
            String message = (respBody != null && !respBody.isBlank()) ? respBody : "Login failed.";
            throw new RaplaSecurityException(message);
        }
        if (status / 100 != 2)
        {
            throw new RaplaException("OAuth2 token endpoint error (HTTP " + status + "): " + resp.body());
        }
        String json = resp.body();
        String access = jsonString(json, "access_token");
        if (access == null)
        {
            throw new RaplaException("OAuth2 token response missing access_token: " + json);
        }
        return new LoginTokens(access, jsonString(json, "refresh_token"), jsonNumber(json, "expires_in"));
    }

    private static String enc(String s)
    {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Flat-JSON string-field extractor — the token response is flat, so this
     *  avoids pulling a JSON mapper into the auth seam. */
    private static String jsonString(String json, String field)
    {
        String key = "\"" + field + "\"";
        int k = json.indexOf(key);
        if (k < 0) return null;
        int colon = json.indexOf(':', k + key.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    /** Flat-JSON numeric-field extractor for {@code expires_in}; 0 if absent. */
    private static long jsonNumber(String json, String field)
    {
        String key = "\"" + field + "\"";
        int k = json.indexOf(key);
        if (k < 0) return 0;
        int colon = json.indexOf(':', k + key.length());
        if (colon < 0) return 0;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
        if (j == i) return 0;
        try { return Long.parseLong(json.substring(i, j)); }
        catch (NumberFormatException nfe) { return 0; }
    }
}
