package org.rapla.storage.dbrm;

import org.rapla.rest.gwtjsonrpc.common.FutureResult;

public class RemoteConnectionInfo 
{
    String accessToken;
    FutureResult<String> refreshCommand;
    String serverURL;
    StatusUpdater statusUpdater;
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

    public void setRefreshCommand(FutureResult<String> refreshCommand) {
        this.refreshCommand = refreshCommand;
    }

    public String getRefreshToken() throws Exception {
        return refreshCommand.get();
    }
    
    public FutureResult<String> getRefreshCommand()
    {
        return refreshCommand;
    }

    public String getAccessToken() {
        return accessToken;
    }


    public String getServerURL() {
        return serverURL;
    }
    
}