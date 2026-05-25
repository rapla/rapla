package org.rapla;

/**
 * Everything needed to start a rapla session: access + refresh tokens AND the
 * provider routing (refresh URL + OAuth client_id) so that mid-session refresh
 * can hit the right provider even after a context restart.
 *
 * <p>PRD 029 Phase 5 (2026-05-25): username/password fields were removed; the
 * password flow lives in exactly two production sites (the legacy Swing dialog
 * Login button and {@code RemoteAuthentificationService.login()}) which
 * exchange password for tokens at the user-input boundary and never thread a
 * password reference through any other layer.
 *
 * <p>The {@code refreshUrl} + {@code oauthClientId} pair carries provider
 * routing across the close+recreate context boundary (PRD 052 Phase 2). Without
 * them, the new context after a switch-back falls back to rapla SAS defaults
 * even when the admin's session was Keycloak — and admin's Keycloak refresh
 * token is rejected by rapla SAS. Carrying them via {@link ConnectInfo} means
 * the post-restart context restores admin's full session, including provider
 * routing, so mid-session refresh works without re-login.
 *
 * <p>{@link #toString()} masks both tokens.
 */
public class ConnectInfo {
    private final String accessToken;
    private final String refreshToken;
    private final String refreshUrl;
    private final String oauthClientId;

    /** Two-arg constructor for rapla-SAS / password sessions (refresh URL and
     *  client_id default to rapla-SAS conventions — see PRD 029 Phase 5). */
    public ConnectInfo(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, null, null);
    }

    /** Four-arg constructor for external-provider sessions (Keycloak / Entra /
     *  Google). {@code refreshUrl} is the provider's token endpoint (or rapla's
     *  BFF URL for secret-backed providers); {@code oauthClientId} is the
     *  client_id the provider expects on refresh-token grants. Null/empty for
     *  either falls back to rapla-SAS defaults. */
    public ConnectInfo(String accessToken, String refreshToken, String refreshUrl, String oauthClientId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.refreshUrl = refreshUrl;
        this.oauthClientId = oauthClientId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    /** Provider token endpoint (Keycloak's, or rapla's BFF). Null → use
     *  rapla-SAS default ({@code serverURL + /oauth2/token}). */
    public String getRefreshUrl() {
        return refreshUrl;
    }

    /** OAuth client_id for refresh-token grants. Null/empty → {@code rapla-client}. */
    public String getOauthClientId() {
        return oauthClientId;
    }

    /** Source-compat factory for the legacy two-token constructor. */
    public static ConnectInfo withAccessToken(String accessToken, String refreshToken) {
        return new ConnectInfo(accessToken, refreshToken);
    }

    @Override
    public String toString() {
        return "ConnectInfo[accessToken=***, refreshToken=" + (refreshToken == null ? "null" : "***")
                + ", refreshUrl=" + refreshUrl + ", oauthClientId=" + oauthClientId + "]";
    }
}
