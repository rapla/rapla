package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;

import org.springframework.beans.factory.annotation.Autowired;

public class RemoteConnectionInfo
{
    String accessToken;
    String refreshToken;
    String idToken;
    String logoutUrl;
    String serverURL;
    // PRD 051 — admin "switch to user". When non-null, holds the
    // rapla-SAS-signed impersonation access token; the effective Bearer
    // for outbound requests is this token (carries sub=target, act=admin).
    // No refresh-token counterpart by design — renewal is via another
    // call to /api/auth/impersonate when the token expires.
    // Cleared on switch-back, on logout, and on the auth-dead hook.
    String impersonationToken;
    String impersonationTargetUsername;
    // PRD 029 Phase 4 — the token endpoint + client_id this session must use
    // for refresh-token reauth. For a rapla-SAS / password session these stay
    // null and MyCustomConnector falls back to serverURL + /oauth2/token with
    // client_id=rapla-client. For a browser-OAuth provider login they hold the
    // provider's token endpoint (the BFF URL for a secret-backed provider like
    // Keycloak) and the provider's client_id.
    String refreshUrl;
    String oauthClientId;
    transient StatusUpdater statusUpdater;
    /** Fires when the auth seam discovers BOTH the access token AND the cached
     *  refresh token are dead (refresh request itself returned non-2xx). The
     *  Swing client wires this to {@code fireStorageDisconnected(...)} so the
     *  user gets a re-login dialog instead of seeing the calendar quietly
     *  freeze with logged 401s. Transient — never serialised. */
    transient Runnable onAuthDead;
    ConnectInfo connectInfo;

    @Autowired
    public RemoteConnectionInfo()
    {}

    public void setStatusUpdater(StatusUpdater statusUpdater) {
        this.statusUpdater = statusUpdater;
    }
    
    public StatusUpdater getStatusUpdater() {
        return statusUpdater;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }
    
    public String get()
    {
        return serverURL;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    /** OIDC id_token from the token-endpoint response, used as {@code id_token_hint}
     *  when opening the browser tab to the OIDC end-session endpoint
     *  ({@code /connect/logout}). Without this hint, Spring SAS rejects the logout
     *  request with 404 → the rapla-remember-me cookie survives and the next
     *  OAuth flow silently re-authenticates. */
    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    /** Token endpoint for refresh-token reauth (PRD 029 Phase 4). When a
     *  browser-OAuth provider was used this is the provider's token endpoint —
     *  the BFF URL for a secret-backed provider like Keycloak. Null for
     *  rapla-SAS / password sessions (MyCustomConnector then falls back to
     *  serverURL + /oauth2/token). */
    public void setRefreshUrl(String refreshUrl) {
        this.refreshUrl = refreshUrl;
    }

    public String getRefreshUrl() {
        return refreshUrl;
    }

    /** OAuth client_id to send on the refresh request. Null → rapla-client. */
    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = oauthClientId;
    }

    public String getOauthClientId() {
        return oauthClientId;
    }

    public String getServerURL() {
        return serverURL;
    }

    /** Set a hook fired when refresh-on-401 discovers the session is fully dead
     *  (both access and refresh tokens rejected). Idempotent — the interceptor
     *  fires it once per dead-session detection. */
    public void setOnAuthDead(Runnable onAuthDead) {
        this.onAuthDead = onAuthDead;
    }

    public Runnable getOnAuthDead() {
        return onAuthDead;
    }

    public void setReconnectInfo(ConnectInfo connectInfo)
    {
        this.connectInfo = connectInfo;
    }

    public ConnectInfo getConnectInfo()
    {
        return connectInfo;
    }

    /**
     * PRD 051 — stash the impersonation access token returned by
     * {@code POST /api/auth/impersonate}. The admin's own
     * {@link #accessToken} stays intact for renewal calls; outbound
     * requests use {@link #getEffectiveAccessToken()} which prefers
     * this token when set.
     *
     * @param impersonationToken the rapla-SAS-signed access JWT, or
     *   {@code null} to clear (switch back to admin's identity)
     * @param targetUsername the impersonated user's username, for UI
     *   indicators and for renewal calls that need the {@code
     *   target_username} form parameter
     */
    public void setImpersonationToken(String impersonationToken, String targetUsername)
    {
        this.impersonationToken = impersonationToken;
        this.impersonationTargetUsername = (impersonationToken == null) ? null : targetUsername;
    }

    public void clearImpersonationToken()
    {
        setImpersonationToken(null, null);
    }

    public String getImpersonationToken()
    {
        return impersonationToken;
    }

    public String getImpersonationTargetUsername()
    {
        return impersonationTargetUsername;
    }

    public boolean hasImpersonationToken()
    {
        return impersonationToken != null && !impersonationToken.isEmpty();
    }

    /**
     * The Bearer to send on outbound API requests. Returns the
     * impersonation token when set, otherwise the admin's regular
     * access token. The renewal-on-401 path reads
     * {@link #getAccessToken()} directly (the admin token) so it can
     * authenticate the {@code /api/auth/impersonate} call even while
     * an impersonation is active.
     */
    public String getEffectiveAccessToken()
    {
        return hasImpersonationToken() ? impersonationToken : accessToken;
    }
}