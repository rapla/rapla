package org.rapla.server.spring.oauth.external;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One external OIDC provider entry under {@code rapla.oauth.external.<registrationId>}
 * (PRD 036 Phase 3). A single concrete class — Spring binds
 * {@code Map<String, ProviderDef>} natively — holding the union of the former
 * {@code Microsoft}/{@code Google}/{@code Keycloak} fields plus a {@code type}
 * discriminator. {@link #toProviderConfig(String)} switches on {@code type} to
 * apply the per-type URL derivation + defaults; the {@code registrationId} (the
 * map key) is threaded in as {@link ProviderConfig#id()}.
 *
 * <p>Default field values are deliberately "unset" (empty string / null boxed
 * primitives) so that {@link #toProviderConfig(String)} can apply
 * <em>type-specific</em> defaults — the three former classes had different
 * defaults for the same field (icon, order, scopes, claims, endpoints).
 */
public class ProviderDef
{
    private boolean enabled = false;

    /** microsoft | google | keycloak. Inferred from the map key when omitted. */
    private String type = "";

    // --- common ---
    private String clientId = "";
    private String clientSecret = "";
    private String hostedDomain = "";
    private Boolean autoProvision;        // type default: true
    private String displayName = "";      // type default: "Sign in with <Type>"
    private String icon = "";             // type default: the type id
    private Integer order;                // type default: ms=10, keycloak=15, google=20
    private Boolean webPickerVisible;     // type default: true
    private String usernameClaim = "";    // type default per type
    private String emailClaim = "";       // type default: "email"
    private String externalIdClaim = "";  // type default: ms=oid, google/keycloak=sub
    private String postLogoutRedirectUri = "";
    private List<String> scopes;          // type default per type
    private boolean revokeOnLogout = false;
    // Dev bridge for an IdP whose realm can't register the conformant
    // /login/oauth2/code/{id} (DHBW Mosbach on localhost). At most one provider.
    private boolean legacyCallback = false;

    // --- explicit endpoints (Microsoft/Google may override; Keycloak derives) ---
    private String issuer = "";
    private String authorizeUrl = "";
    private String tokenUrl = "";
    private String jwksUrl = "";
    private String endSessionUrl = "";

    // --- microsoft ---
    private String tenant = "";

    // --- keycloak ---
    private String baseUrl = "";
    private String realm = "";

    // --- google ---
    private Map<String, String> extraAuthorizeParams;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getHostedDomain() { return hostedDomain; }
    public void setHostedDomain(String hostedDomain) { this.hostedDomain = hostedDomain; }
    public Boolean getAutoProvision() { return autoProvision; }
    public void setAutoProvision(Boolean autoProvision) { this.autoProvision = autoProvision; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
    public Boolean getWebPickerVisible() { return webPickerVisible; }
    public void setWebPickerVisible(Boolean webPickerVisible) { this.webPickerVisible = webPickerVisible; }
    public String getUsernameClaim() { return usernameClaim; }
    public void setUsernameClaim(String usernameClaim) { this.usernameClaim = usernameClaim; }
    public String getEmailClaim() { return emailClaim; }
    public void setEmailClaim(String emailClaim) { this.emailClaim = emailClaim; }
    public String getExternalIdClaim() { return externalIdClaim; }
    public void setExternalIdClaim(String externalIdClaim) { this.externalIdClaim = externalIdClaim; }
    public String getPostLogoutRedirectUri() { return postLogoutRedirectUri; }
    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) { this.postLogoutRedirectUri = postLogoutRedirectUri; }
    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }
    public boolean isRevokeOnLogout() { return revokeOnLogout; }
    public void setRevokeOnLogout(boolean revokeOnLogout) { this.revokeOnLogout = revokeOnLogout; }
    public boolean isLegacyCallback() { return legacyCallback; }
    public void setLegacyCallback(boolean legacyCallback) { this.legacyCallback = legacyCallback; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAuthorizeUrl() { return authorizeUrl; }
    public void setAuthorizeUrl(String authorizeUrl) { this.authorizeUrl = authorizeUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getJwksUrl() { return jwksUrl; }
    public void setJwksUrl(String jwksUrl) { this.jwksUrl = jwksUrl; }
    public String getEndSessionUrl() { return endSessionUrl; }
    public void setEndSessionUrl(String endSessionUrl) { this.endSessionUrl = endSessionUrl; }
    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = realm; }
    public Map<String, String> getExtraAuthorizeParams() { return extraAuthorizeParams; }
    public void setExtraAuthorizeParams(Map<String, String> extraAuthorizeParams) { this.extraAuthorizeParams = extraAuthorizeParams; }

    /** Multi-tenant Entra placeholders — see the former {@code Microsoft} class. */
    private static final Set<String> MULTI_TENANT_PLACEHOLDERS = Set.of("common", "organizations", "consumers");

    private static final Pattern ENTRA_TENANT_ISSUER_PATTERN =
            Pattern.compile("^https://login\\.microsoftonline\\.com/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/v2\\.0$");

    /**
     * Resolve the provider type: explicit {@code type:} wins; otherwise infer
     * from the registrationId (so legacy {@code microsoft}/{@code google}/{@code keycloak}
     * keys bind unchanged). Fails fast when an enabled entry has no determinable
     * type — never silently dropped (PRD 036 D-3.3).
     */
    ExternalProviderId resolveType(String registrationId)
    {
        if (type != null && !type.isEmpty())
        {
            return ExternalProviderId.parse(type).orElseThrow(() -> new IllegalStateException(
                    "rapla.oauth.external." + registrationId + ".type=" + type
                            + " is not one of microsoft/google/keycloak"));
        }
        return ExternalProviderId.parse(registrationId).orElseThrow(() -> new IllegalStateException(
                "rapla.oauth.external." + registrationId + " has no 'type:' and the key '"
                        + registrationId + "' is not a known provider type — set 'type: microsoft|google|keycloak'"));
    }

    /** Build the immutable runtime {@link ProviderConfig} for this entry. */
    ProviderConfig toProviderConfig(String registrationId)
    {
        ExternalProviderId t = resolveType(registrationId);
        switch (t)
        {
            case MICROSOFT: return microsoft(registrationId);
            case GOOGLE:    return google(registrationId);
            case KEYCLOAK:  return keycloak(registrationId);
            default: throw new IllegalStateException("Unhandled provider type " + t);
        }
    }

    private ProviderConfig microsoft(String id)
    {
        if (tenant == null || tenant.isEmpty())
        {
            throw new IllegalStateException(
                    "rapla.oauth.external." + id + " (type=microsoft) requires 'tenant'");
        }
        if (clientId == null || clientId.isEmpty())
        {
            throw new IllegalStateException(
                    "rapla.oauth.external." + id + " (type=microsoft) requires 'client-id'");
        }
        boolean multiTenant = MULTI_TENANT_PLACEHOLDERS.contains(tenant);
        String base = "https://login.microsoftonline.com/" + tenant;
        String iss = orDefault(issuer, base + "/v2.0");
        String authorize = orDefault(authorizeUrl, base + "/oauth2/v2.0/authorize");
        String token = orDefault(tokenUrl, base + "/oauth2/v2.0/token");
        String jwks = orDefault(jwksUrl, base + "/discovery/v2.0/keys");
        String endSession = orDefault(endSessionUrl, base + "/oauth2/v2.0/logout");
        Pattern issPattern = multiTenant ? ENTRA_TENANT_ISSUER_PATTERN : null;
        return new ProviderConfig(
                id, ExternalProviderId.MICROSOFT,
                orDefault(displayName, "Sign in with Microsoft"), orDefault(icon, "microsoft"),
                orDefault(order, 10), orDefault(webPickerVisible, true),
                clientId, clientSecret, iss, issPattern, authorize, token, jwks, endSession,
                postLogoutRedirectUri,
                orDefault(scopes, List.of("openid", "profile", "email", "offline_access")),
                new LinkedHashMap<>(),
                orDefault(usernameClaim, "preferred_username"), orDefault(emailClaim, "email"),
                orDefault(externalIdClaim, "oid"),
                hostedDomain, orDefault(autoProvision, true), false, legacyCallback);
    }

    private ProviderConfig google(String id)
    {
        if (clientId == null || clientId.isEmpty())
        {
            throw new IllegalStateException(
                    "rapla.oauth.external." + id + " (type=google) requires 'client-id'");
        }
        return new ProviderConfig(
                id, ExternalProviderId.GOOGLE,
                orDefault(displayName, "Sign in with Google"), orDefault(icon, "google"),
                orDefault(order, 20), orDefault(webPickerVisible, true),
                clientId, clientSecret,
                orDefault(issuer, "https://accounts.google.com"),
                orDefault(authorizeUrl, "https://accounts.google.com/o/oauth2/v2/auth"),
                orDefault(tokenUrl, "https://oauth2.googleapis.com/token"),
                orDefault(jwksUrl, "https://www.googleapis.com/oauth2/v3/certs"),
                endSessionUrl,
                "",
                orDefault(scopes, List.of("openid", "profile", "email")),
                extraAuthorizeParams == null ? defaultGoogleExtraParams() : extraAuthorizeParams,
                orDefault(usernameClaim, "email"), orDefault(emailClaim, "email"),
                orDefault(externalIdClaim, "sub"),
                hostedDomain, orDefault(autoProvision, true), revokeOnLogout, legacyCallback);
    }

    private ProviderConfig keycloak(String id)
    {
        if (baseUrl == null || baseUrl.isEmpty())
        {
            throw new IllegalStateException(
                    "rapla.oauth.external." + id + " (type=keycloak) requires 'base-url'");
        }
        if (realm == null || realm.isEmpty())
        {
            throw new IllegalStateException(
                    "rapla.oauth.external." + id + " (type=keycloak) requires 'realm'");
        }
        if (clientId == null || clientId.isEmpty())
        {
            throw new IllegalStateException(
                    "rapla.oauth.external." + id + " (type=keycloak) requires 'client-id'");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String realmBase = base + "/realms/" + realm;
        String oidc = realmBase + "/protocol/openid-connect";
        return new ProviderConfig(
                id, ExternalProviderId.KEYCLOAK,
                orDefault(displayName, "Sign in with Keycloak"), orDefault(icon, "keycloak"),
                orDefault(order, 15), orDefault(webPickerVisible, true),
                clientId, clientSecret,
                realmBase,
                oidc + "/auth", oidc + "/token", oidc + "/certs", oidc + "/logout",
                postLogoutRedirectUri,
                orDefault(scopes, List.of("openid", "profile", "email")),
                new LinkedHashMap<>(),
                orDefault(usernameClaim, "preferred_username"), orDefault(emailClaim, "email"),
                orDefault(externalIdClaim, "sub"),
                hostedDomain, orDefault(autoProvision, true), false, legacyCallback);
    }

    private static Map<String, String> defaultGoogleExtraParams()
    {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("access_type", "offline");
        m.put("prompt", "consent");
        return m;
    }

    private static String orDefault(String v, String fallback)
    {
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static int orDefault(Integer v, int fallback) { return v == null ? fallback : v; }

    private static boolean orDefault(Boolean v, boolean fallback) { return v == null ? fallback : v; }

    private static List<String> orDefault(List<String> v, List<String> fallback)
    {
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
