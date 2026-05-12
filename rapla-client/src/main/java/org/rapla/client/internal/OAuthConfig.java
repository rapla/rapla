package org.rapla.client.internal;

import java.util.List;

public final class OAuthConfig
{
    private final boolean enabled;
    private final String clientId;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final List<String> scopes;

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl, List<String> scopes)
    {
        this.enabled = enabled;
        this.clientId = clientId;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.scopes = scopes;
    }

    public boolean isEnabled() { return enabled; }
    public String getClientId() { return clientId; }
    public String getAuthorizeUrl() { return authorizeUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public List<String> getScopes() { return scopes; }
}
