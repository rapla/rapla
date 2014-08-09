package org.rapla.storage.dbrm;

import java.util.Date;

public class LoginTokens {
    String accessToken;
    Date validUntil;
    
    public LoginTokens() {
    }
    
    public LoginTokens(String accessToken, Date validUntil) {
        this.accessToken = accessToken;
        this.validUntil = validUntil;
    }
    
    public String getAccessToken()
    {
        return accessToken;
    }
    
    public Date getValidUntil()
    {
        return validUntil;
    }
}
