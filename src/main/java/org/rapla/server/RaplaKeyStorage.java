package org.rapla.server;

import java.util.Collection;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

public interface RaplaKeyStorage {
    String getRootKeyBase64();
    LoginInfo getSecrets(User user, TypedComponentRole<String> tagName) throws RaplaException;
    void storeLoginInfo(User user, TypedComponentRole<String> tagName, String login, String secret) throws RaplaException;
    void removeLoginInfo(User user, TypedComponentRole<String> tagName) throws RaplaException;
    void storeAPIKey(User user, String clientId, String apiKey) throws RaplaException;
    Collection<String> getAPIKeys(User user) throws RaplaException;
    void removeAPIKey(User user, String key) throws RaplaException;
    class LoginInfo
    {
    	public String login;
    	public String secret;
    }
}
