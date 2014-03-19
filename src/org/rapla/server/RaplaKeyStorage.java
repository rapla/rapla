package org.rapla.server;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;

public interface RaplaKeyStorage {
    public String getRootKeyBase64();
    public LoginInfo getSecrets(User user, String tagName) throws RaplaException;
    public void storeLoginInfo(User user,String tagName,String login,String secret) throws RaplaException;
    public void removeLoginInfo(User user, String tagName) throws RaplaException;
    public class LoginInfo
    {
    	public String login;
    	public String secret;
    }
}
