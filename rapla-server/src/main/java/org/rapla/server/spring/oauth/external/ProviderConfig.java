package org.rapla.server.spring.oauth.external;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable runtime view of one external OIDC provider. Built from
 * {@link ExternalProvidersProperties} by applying provider-specific URL
 * derivations (Entra endpoints from {@code tenant}) and defaults.
 */
public final class ProviderConfig
{
    private final ExternalProviderId provider;
    private final String displayName;
    private final String icon;
    private final int order;
    private final boolean webPickerVisible;
    private final String clientId;
    private final String clientSecret;
    private final String issuer;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String jwksUrl;
    private final String endSessionUrl;
    private final String postLogoutRedirectUri;
    private final List<String> scopes;
    private final Map<String, String> extraAuthorizeParams;
    private final String usernameClaim;
    private final String emailClaim;
    private final String externalIdClaim;
    private final String hostedDomain;
    private final boolean autoProvision;
    private final boolean revokeOnLogout;

    public ProviderConfig(
            ExternalProviderId provider,
            String displayName,
            String icon,
            int order,
            boolean webPickerVisible,
            String clientId,
            String clientSecret,
            String issuer,
            String authorizeUrl,
            String tokenUrl,
            String jwksUrl,
            String endSessionUrl,
            String postLogoutRedirectUri,
            List<String> scopes,
            Map<String, String> extraAuthorizeParams,
            String usernameClaim,
            String emailClaim,
            String externalIdClaim,
            String hostedDomain,
            boolean autoProvision,
            boolean revokeOnLogout)
    {
        this.provider = provider;
        this.displayName = displayName;
        this.icon = icon;
        this.order = order;
        this.webPickerVisible = webPickerVisible;
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.issuer = issuer;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.jwksUrl = jwksUrl;
        this.endSessionUrl = endSessionUrl;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
        this.scopes = List.copyOf(scopes);
        this.extraAuthorizeParams = Collections.unmodifiableMap(new LinkedHashMap<>(extraAuthorizeParams));
        this.usernameClaim = usernameClaim;
        this.emailClaim = emailClaim;
        this.externalIdClaim = externalIdClaim;
        this.hostedDomain = hostedDomain;
        this.autoProvision = autoProvision;
        this.revokeOnLogout = revokeOnLogout;
    }

    public ExternalProviderId provider() { return provider; }
    public String id() { return provider.id(); }
    public String displayName() { return displayName; }
    public String icon() { return icon; }
    public int order() { return order; }
    public boolean webPickerVisible() { return webPickerVisible; }
    public String clientId() { return clientId; }
    public String clientSecret() { return clientSecret; }
    public String issuer() { return issuer; }
    public String authorizeUrl() { return authorizeUrl; }
    public String tokenUrl() { return tokenUrl; }
    public String jwksUrl() { return jwksUrl; }
    public String endSessionUrl() { return endSessionUrl; }
    public String postLogoutRedirectUri() { return postLogoutRedirectUri; }
    public List<String> scopes() { return scopes; }
    public Map<String, String> extraAuthorizeParams() { return extraAuthorizeParams; }
    public String usernameClaim() { return usernameClaim; }
    public String emailClaim() { return emailClaim; }
    public String externalIdClaim() { return externalIdClaim; }
    public String hostedDomain() { return hostedDomain; }
    public boolean autoProvision() { return autoProvision; }
    public boolean revokeOnLogout() { return revokeOnLogout; }

    public String externalIdPreferenceKey()
    {
        return "org.rapla.auth.external-id." + provider.id();
    }
}
