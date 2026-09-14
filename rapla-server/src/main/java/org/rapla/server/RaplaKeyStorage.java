package org.rapla.server;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

import java.util.Collection;

public interface RaplaKeyStorage {
    String getRootKeyBase64();
    LoginInfo getSecrets(User user, TypedComponentRole<String> tagName) throws RaplaException;
    void storeLoginInfo(User user, TypedComponentRole<String> tagName, String login, String secret) throws RaplaException;
    void removeLoginInfo(User user, TypedComponentRole<String> tagName) throws RaplaException;

    /**
     * Stores or replaces a per-user API key, identified by {@code clientId}
     * (the storage slot key). Multi-slot: different {@code clientId}s
     * coexist for the same user. Re-storing the same {@code clientId}
     * overwrites the previous value.
     *
     * <p>Used by PRD 043's asymmetric flow ({@code clientId} = RFC 7638
     * thumbprint, value = signed JWT). A historical {@code clientId} =
     * "refreshToken" slot may still exist in older stores (read-only legacy).
     */
    void storeAPIKey(User user, String clientId, String apiKey) throws RaplaException;

    /** All API keys stored for the user — values only, across every {@code clientId}. */
    Collection<String> getAPIKeys(User user) throws RaplaException;

    /** Removes the slot identified by {@code clientId}. Idempotent (no-op if absent). */
    void removeAPIKey(User user, String clientId) throws RaplaException;

    class LoginInfo
    {
    	public String login;
    	public String secret;
    }
}
