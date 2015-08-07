package org.rapla.server;

import java.util.Collection;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

public interface RaplaKeyStorage {
    public String getRootKeyBase64();
    public LoginInfo getSecrets(User user, TypedComponentRole<String> tagName) throws RaplaException;
    public void storeLoginInfo(User user,TypedComponentRole<String> tagName,String login,String secret) throws RaplaException;
    public void removeLoginInfo(User user, TypedComponentRole<String> tagName) throws RaplaException;
    public void storeAPIKey(User user,String clientId,String apiKey) throws RaplaException;
    public Collection<String> getAPIKeys(User user) throws RaplaException;
    public void removeAPIKey(User user,String key) throws RaplaException;
    public class LoginInfo
    {
    	public String login;
    	public String secret;
    }
}
