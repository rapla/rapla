package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;

import org.springframework.beans.factory.annotation.Autowired;

public class RemoteConnectionInfo
{
    String accessToken;
    String refreshToken;
    String idToken;
    String refreshUrl;
    String logoutUrl;
    String serverURL;
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

    public void setRefreshUrl(String refreshUrl) {
        this.refreshUrl = refreshUrl;
    }

    public String getRefreshUrl() {
        return refreshUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }

    public String getLogoutUrl() {
        return logoutUrl;
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