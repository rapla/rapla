package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RemoteConnectionInfo
{
    String accessToken;
    String serverURL;
    transient StatusUpdater statusUpdater;
    ConnectInfo connectInfo;

    @Inject
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