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
    // PRD 029 Phase 4 — the token endpoint + client_id this session must use
    // for refresh-token reauth. For a rapla-SAS / password session these stay
    // null and MyCustomConnector falls back to serverURL + /oauth2/token with
    // client_id=rapla-client. For a browser-OAuth provider login they hold the
    // provider's token endpoint (the BFF URL for a secret-backed provider like
    // Keycloak) and the provider's client_id.
    String refreshUrl;
    String oauthClientId;
    transient StatusUpdater statusUpdater;
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

    public void setReconnectInfo(ConnectInfo connectInfo) 
    {
        this.connectInfo = connectInfo;
    }
    
    public ConnectInfo getConnectInfo() 
    {
        return connectInfo;
    }
    
}