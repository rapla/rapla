package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;

import org.springframework.beans.factory.annotation.Autowired;

public class RemoteConnectionInfo
{
    String accessToken;
    String refreshToken;
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