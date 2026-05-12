package org.rapla.client.internal;

import java.util.List;

public final class OAuthConfig
{
    private final boolean enabled;
    private final String clientId;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String refreshUrl;
    private final String logoutUrl;
    private final List<String> scopes;
    private final boolean showPasteFallback;

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl, List<String> scopes)
    {
        this(enabled, clientId, authorizeUrl, tokenUrl, null, null, scopes, false);
    }

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                       List<String> scopes, boolean showPasteFallback)
    {
        this(enabled, clientId, authorizeUrl, tokenUrl, null, null, scopes, showPasteFallback);
    }

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                       String refreshUrl, List<String> scopes, boolean showPasteFallback)
    {
        this(enabled, clientId, authorizeUrl, tokenUrl, refreshUrl, null, scopes, showPasteFallback);
    }

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                       String refreshUrl, String logoutUrl, List<String> scopes, boolean showPasteFallback)
    {
        this.enabled = enabled;
        this.clientId = clientId;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.refreshUrl = refreshUrl;
        this.logoutUrl = logoutUrl;
        this.scopes = scopes;
        this.showPasteFallback = showPasteFallback;
    }

    public boolean isEnabled() { return enabled; }
    public String getClientId() { return clientId; }
    public String getAuthorizeUrl() { return authorizeUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public String getRefreshUrl() { return refreshUrl; }
    public String getLogoutUrl() { return logoutUrl; }
    public List<String> getScopes() { return scopes; }
    public boolean isShowPasteFallback() { return showPasteFallback; }
}
