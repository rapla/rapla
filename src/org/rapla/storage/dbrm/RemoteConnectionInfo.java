package org.rapla.storage.dbrm;

import org.rapla.rest.gwtjsonrpc.common.FutureResult;

public class RemoteConnectionInfo 
{
    String accessToken;
    FutureResult<String> reAuthenticateCommand;
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

    public void setReAuthenticateCommand(FutureResult<String> reAuthenticateCommand) {
        this.reAuthenticateCommand = reAuthenticateCommand;
    }

    public String getRefreshToken() throws Exception {
        return reAuthenticateCommand.get();
    }
    
    public FutureResult<String> getReAuthenticateCommand()
    {
        return reAuthenticateCommand;
    }

    public String getAccessToken() {
        return accessToken;
    }


    public String getServerURL() {
        return serverURL;
    }
    
}