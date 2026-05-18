package org.rapla.client.internal;

import java.util.List;

/**
 * OAuth discovery config read from {@code GET /api/auth/oauth/config}.
 *
 * <p>Doubles as a per-provider config: the top-level instance carries the
 * embedded rapla SAS endpoints plus a {@link #getProviders()} list, and each
 * element of that list is itself an {@code OAuthConfig} whose flat
 * authorize/token/clientId/scopes fields point at that provider's IdP (rapla,
 * Keycloak, …). {@code SwingOAuthLoginFlow} consumes whichever one the user
 * picks in the login dialog's method dropdown (PRD 029 Phase 4).
 */
public final class OAuthConfig
{
    private final boolean enabled;
    private final String clientId;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String logoutUrl;
    private final List<String> scopes;
    private final boolean showPasteFallback;
    // PRD 029 Phase 3 — admin-selectable legacy Swing login, read from
    // /api/auth/oauth/config. swingLegacyLogin=true → show the legacy
    // username/password dialog instead of auto-firing the browser flow;
    // swingLegacyShowSsoButton (only effective when swingLegacyLogin) → also
    // offer the SSO method dropdown on that dialog.
    private final boolean swingLegacyLogin;
    private final boolean swingLegacyShowSsoButton;
    // PRD 029 Phase 4 — identity of this config when it represents one entry
    // of the providers[] list; null for the top-level config.
    private final String id;
    private final String displayName;
    // PRD 029 Phase 4 — one OAuthConfig per discovery providers[] entry
    // (rapla SAS, Keycloak, …). Empty on a provider-level config.
    private final List<OAuthConfig> providers;

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl, List<String> scopes)
    {
        this(enabled, clientId, authorizeUrl, tokenUrl, null, scopes, false, false, false);
    }

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                       List<String> scopes, boolean showPasteFallback)
    {
        this(enabled, clientId, authorizeUrl, tokenUrl, null, scopes, showPasteFallback, false, false);
    }

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                       String logoutUrl, List<String> scopes, boolean showPasteFallback)
    {
        this(enabled, clientId, authorizeUrl, tokenUrl, logoutUrl, scopes, showPasteFallback, false, false);
    }

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                       String logoutUrl, List<String> scopes, boolean showPasteFallback,
                       boolean swingLegacyLogin, boolean swingLegacyShowSsoButton)
    {
        this(enabled, clientId, authorizeUrl, tokenUrl, logoutUrl, scopes, showPasteFallback,
                swingLegacyLogin, swingLegacyShowSsoButton, null, null, List.of());
    }

    public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                       String logoutUrl, List<String> scopes, boolean showPasteFallback,
                       boolean swingLegacyLogin, boolean swingLegacyShowSsoButton,
                       String id, String displayName, List<OAuthConfig> providers)
    {
        this.enabled = enabled;
        this.clientId = clientId;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.logoutUrl = logoutUrl;
        this.scopes = scopes;
        this.showPasteFallback = showPasteFallback;
        this.swingLegacyLogin = swingLegacyLogin;
        this.swingLegacyShowSsoButton = swingLegacyShowSsoButton;
        this.id = id;
        this.displayName = displayName;
        this.providers = providers == null ? List.of() : providers;
    }

    public boolean isEnabled() { return enabled; }
    public String getClientId() { return clientId; }
    public String getAuthorizeUrl() { return authorizeUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public String getLogoutUrl() { return logoutUrl; }
    public List<String> getScopes() { return scopes; }
    public boolean isShowPasteFallback() { return showPasteFallback; }
    public boolean isSwingLegacyLogin() { return swingLegacyLogin; }
    public boolean isSwingLegacyShowSsoButton() { return swingLegacyShowSsoButton; }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<OAuthConfig> getProviders() { return providers; }
}
