package org.rapla.server.spring.oauth.external;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Immutable runtime view of one external OIDC provider. Built from
 * {@link ExternalProvidersProperties} by applying provider-specific URL
 * derivations (Entra endpoints from {@code tenant}) and defaults.
 *
 * <p>An {@code issuerPattern} can be set instead of relying solely on the
 * literal {@code issuer} for matching. Used by Entra multi-tenant
 * ({@code tenant=common}/{@code organizations}/{@code consumers}) where the
 * {@code iss} claim in tokens carries the *user's home tenant*, not the
 * configured "common" placeholder. With a pattern, {@link #matchesIssuer}
 * accepts any tenant GUID at the matching position.
 */
public final class ProviderConfig
{
    private final String registrationId;
    private final ExternalProviderId provider;
    private final String displayName;
    private final String icon;
    private final int order;
    private final boolean webPickerVisible;
    private final String clientId;
    private final String clientSecret;
    private final String issuer;
    private final Pattern issuerPattern;
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
    private final boolean legacyCallback;

    public ProviderConfig(
            String registrationId,
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
            boolean revokeOnLogout,
            boolean legacyCallback)
    {
        this(registrationId, provider, displayName, icon, order, webPickerVisible, clientId, clientSecret,
                issuer, null, authorizeUrl, tokenUrl, jwksUrl, endSessionUrl,
                postLogoutRedirectUri, scopes, extraAuthorizeParams,
                usernameClaim, emailClaim, externalIdClaim, hostedDomain,
                autoProvision, revokeOnLogout, legacyCallback);
    }

    public ProviderConfig(
            String registrationId,
            ExternalProviderId provider,
            String displayName,
            String icon,
            int order,
            boolean webPickerVisible,
            String clientId,
            String clientSecret,
            String issuer,
            Pattern issuerPattern,
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
            boolean revokeOnLogout,
            boolean legacyCallback)
    {
        this.registrationId = registrationId;
        this.provider = provider;
        this.displayName = displayName;
        this.icon = icon;
        this.order = order;
        this.webPickerVisible = webPickerVisible;
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.issuer = issuer;
        this.issuerPattern = issuerPattern;
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
        this.legacyCallback = legacyCallback;
    }

    public ExternalProviderId provider() { return provider; }
    /** The provider <em>type</em> (microsoft/google/keycloak) — alias of {@link #provider()}. */
    public ExternalProviderId type() { return provider; }
    /** The {@code registrationId} (map key) — unique per registration, drives the callback path. */
    public String id() { return registrationId; }
    public String displayName() { return displayName; }
    public String icon() { return icon; }
    public int order() { return order; }
    public boolean webPickerVisible() { return webPickerVisible; }
    public String clientId() { return clientId; }
    public String clientSecret() { return clientSecret; }
    public String issuer() { return issuer; }
    public Pattern issuerPattern() { return issuerPattern; }
    public boolean isMultiTenant() { return issuerPattern != null; }
    /**
     * True if {@code iss} matches this provider — either by exact-equals on
     * {@link #issuer()} for fixed-issuer providers, or by regex match on
     * {@link #issuerPattern()} for multi-tenant providers.
     */
    public boolean matchesIssuer(String iss)
    {
        if (iss == null) return false;
        if (issuerPattern != null) return issuerPattern.matcher(iss).matches();
        return issuer != null && issuer.equals(iss);
    }
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
    /**
     * PRD 072 / 036 Phase 3 — when true, this provider's {@code ClientRegistration}
     * sends the legacy {@code /app/auth/callback} redirect_uri instead of the
     * conformant {@code /login/oauth2/code/{registrationId}}, and
     * {@link org.rapla.server.spring.oauth.LegacyAppCallbackBridgeFilter} bridges
     * the return to this provider's per-registration callback. Per-provider, dev
     * bridge for an IdP whose realm can't register the conformant URI (DHBW Mosbach
     * on localhost). At most one provider may set this (single {@code /app/auth/callback}).
     */
    public boolean legacyCallback() { return legacyCallback; }

    public String externalIdPreferenceKey()
    {
        return "org.rapla.auth.external-id." + provider.id();
    }
}
