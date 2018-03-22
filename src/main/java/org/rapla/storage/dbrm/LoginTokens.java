package org.rapla.storage.dbrm;

import jsinterop.annotations.JsMethod;

import java.util.Date;

public class LoginTokens {
    String accessToken;
    Date validUntil;

    public LoginTokens() {
        this("",null);
    }

    public LoginTokens(String accessToken, Date validUntil) {
        this.accessToken = accessToken;
        this.validUntil = validUntil;
    }

    @JsMethod
    public String getAccessToken()
    {
        return accessToken;
    }

    @JsMethod
    public Date getValidUntil()
    {
        return validUntil;
    }
    
    public String toString()
    {
        return accessToken + "#" + validUntil.getTime();
    }
   
    public static LoginTokens fromString(String s){
    	if(s.startsWith("\""))s=s.replaceFirst("\"", "");
    	if(s.endsWith("\""))s=s.substring(0, s.length()-1);
        String[] split = s.split("#");
        String accessToken2 = split[0];
        long parseLong = Long.parseLong(split[1]);
        Date validUntil2 = new Date(parseLong);
        return       new LoginTokens(accessToken2, validUntil2);
    }

    public boolean isValid() {
        long currentTimeMillis = System.currentTimeMillis();
        long time = this.validUntil.getTime();
        boolean valid = currentTimeMillis < time;
        return valid;
    }
}
