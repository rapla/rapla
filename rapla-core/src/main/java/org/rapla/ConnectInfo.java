package org.rapla;

/**
 * Everything needed to start a rapla session: access + refresh tokens.
 *
 * <p>PRD 029 Phase 5 (2026-05-25): username/password fields were removed; the
 * password flow lives in exactly two production sites (the legacy Swing dialog
 * Login button and {@code RemoteAuthentificationService.login()}) which
 * exchange password for tokens at the user-input boundary and never thread a
 * password reference through any other layer.
 *
 * <p>PRD 072 Phase 5 (Swing A+Y): rapla is now Swing's single federating
 * Authorization Server (identity broker). Every Swing login — including
 * Keycloak/Google/Microsoft via the server-side {@code /login} chooser —
 * yields a rapla-issuer token, and every refresh hits rapla's own
 * {@code /oauth2/token}. The provider-routing fields ({@code refreshUrl} /
 * {@code oauthClientId}) that carried an external IdP's token endpoint across
 * the close+recreate context boundary are gone: there is no longer any
 * non-rapla token endpoint to route to.
 *
 * <p>{@link #toString()} masks both tokens.
 */
public class ConnectInfo {
    private final String accessToken;
    private final String refreshToken;
    private final Boolean remember;

    /** Tokens-only session start. Refresh always goes to rapla's
     *  {@code serverURL + /oauth2/token} with {@code client_id=rapla-client}
     *  (PRD 072 Phase 5). */
    public ConnectInfo(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, null);
    }

    /** @param remember the stay-signed-in decision of the session, carried across a context rebuild (PRD 131 OQ10); null = none yet */
    public ConnectInfo(String accessToken, String refreshToken, Boolean remember) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.remember = remember;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Boolean getRemember() {
        return remember;
    }

    /** Source-compat factory for the two-token constructor. */
    public static ConnectInfo withAccessToken(String accessToken, String refreshToken) {
        return new ConnectInfo(accessToken, refreshToken);
    }

    @Override
    public String toString() {
        return "ConnectInfo[accessToken=***, refreshToken=" + (refreshToken == null ? "null" : "***") + "]";
    }
}
