package org.rapla.server.spring.oauth.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Per-provider OIDC config. Each provider block is independently enabled.
 * Default-disabled — a fresh rapla deployment with no external config behaves
 * exactly as before. See PRD 036.
 */
@ConfigurationProperties(prefix = "rapla.oauth.external")
public class ExternalProvidersProperties
{
    private Microsoft microsoft = new Microsoft();
    private Google google = new Google();
    private Keycloak keycloak = new Keycloak();

    public Microsoft getMicrosoft() { return microsoft; }
    public void setMicrosoft(Microsoft microsoft) { this.microsoft = microsoft; }

    public Google getGoogle() { return google; }
    public void setGoogle(Google google) { this.google = google; }

    public Keycloak getKeycloak() { return keycloak; }
    public void setKeycloak(Keycloak keycloak) { this.keycloak = keycloak; }

    public List<ProviderConfig> enabledProviders()
    {
        List<ProviderConfig> out = new ArrayList<>(3);
        if (microsoft.isEnabled()) out.add(microsoft.toProviderConfig());
        if (google.isEnabled()) out.add(google.toProviderConfig());
        if (keycloak.isEnabled()) out.add(keycloak.toProviderConfig());
        return out;
    }

    public Optional<ProviderConfig> byIssuer(String issuer)
    {
        if (issuer == null) return Optional.empty();
        return enabledProviders().stream()
                .filter(p -> p.matchesIssuer(issuer))
                .findFirst();
    }

    public Optional<ProviderConfig> byId(String id)
    {
        if (id == null) return Optional.empty();
        return enabledProviders().stream()
                .filter(p -> id.equals(p.id()))
                .findFirst();
    }

    public static class Microsoft
    {
        private boolean enabled = false;
        private String tenant = "";
        private String clientId = "";
        // For Entra "SPA" platform clients this stays empty (PKCE-only, public).
        // For "Web" platform clients (confidential) supply the registered secret.
        private String clientSecret = "";
        private String hostedDomain = "";
        // Matches the rapla LDAP precedent (RaplaAuthentificationService.authenticate
        // auto-creates a User on successful external auth). Single-tenant Entra is
        // already scoped to the configured directory, so accepting all directory
        // members is the expected SSO behaviour.
        private boolean autoProvision = true;
        private String displayName = "Sign in with Microsoft";
        private String icon = "microsoft";
        private int order = 10;
        private boolean webPickerVisible = true;
        private String authorizeUrl = "";
        private String tokenUrl = "";
        private String jwksUrl = "";
        private String endSessionUrl = "";
        private String issuer = "";
        private String postLogoutRedirectUri = "";
        private String usernameClaim = "preferred_username";
        private String emailClaim = "email";
        private String externalIdClaim = "oid";
        private List<String> scopes = List.of("openid", "profile", "email", "offline_access");

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTenant() { return tenant; }
        public void setTenant(String tenant) { this.tenant = tenant; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getHostedDomain() { return hostedDomain; }
        public void setHostedDomain(String hostedDomain) { this.hostedDomain = hostedDomain; }
        public boolean isAutoProvision() { return autoProvision; }
        public void setAutoProvision(boolean autoProvision) { this.autoProvision = autoProvision; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public boolean isWebPickerVisible() { return webPickerVisible; }
        public void setWebPickerVisible(boolean webPickerVisible) { this.webPickerVisible = webPickerVisible; }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public void setAuthorizeUrl(String authorizeUrl) { this.authorizeUrl = authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getJwksUrl() { return jwksUrl; }
        public void setJwksUrl(String jwksUrl) { this.jwksUrl = jwksUrl; }
        public String getEndSessionUrl() { return endSessionUrl; }
        public void setEndSessionUrl(String endSessionUrl) { this.endSessionUrl = endSessionUrl; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getPostLogoutRedirectUri() { return postLogoutRedirectUri; }
        public void setPostLogoutRedirectUri(String postLogoutRedirectUri) { this.postLogoutRedirectUri = postLogoutRedirectUri; }
        public String getUsernameClaim() { return usernameClaim; }
        public void setUsernameClaim(String usernameClaim) { this.usernameClaim = usernameClaim; }
        public String getEmailClaim() { return emailClaim; }
        public void setEmailClaim(String emailClaim) { this.emailClaim = emailClaim; }
        public String getExternalIdClaim() { return externalIdClaim; }
        public void setExternalIdClaim(String externalIdClaim) { this.externalIdClaim = externalIdClaim; }
        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }

        /**
         * Multi-tenant Entra placeholders that route through
         * {@code login.microsoftonline.com/<placeholder>/...}. When tenant
         * matches one of these, rapla switches to multi-tenant mode:
         * tokens come back with the user's *home tenant GUID* in {@code iss},
         * so validation uses a pattern matcher (any tenant GUID) instead of
         * exact equals, and JWKS comes from the multi-tenant endpoint that
         * serves keys for all tenants.
         */
        private static final Set<String> MULTI_TENANT_PLACEHOLDERS = Set.of("common", "organizations", "consumers");

        /** Matches {@code https://login.microsoftonline.com/<tenant-guid>/v2.0}. */
        private static final Pattern ENTRA_TENANT_ISSUER_PATTERN =
                Pattern.compile("^https://login\\.microsoftonline\\.com/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/v2\\.0$");

        ProviderConfig toProviderConfig()
        {
            if (tenant == null || tenant.isEmpty())
            {
                throw new IllegalStateException(
                        "rapla.oauth.external.microsoft.enabled=true requires rapla.oauth.external.microsoft.tenant");
            }
            if (clientId == null || clientId.isEmpty())
            {
                throw new IllegalStateException(
                        "rapla.oauth.external.microsoft.enabled=true requires rapla.oauth.external.microsoft.client-id");
            }
            boolean multiTenant = MULTI_TENANT_PLACEHOLDERS.contains(tenant);
            String base = "https://login.microsoftonline.com/" + tenant;
            String iss = orDefault(issuer, base + "/v2.0");
            String authorize = orDefault(authorizeUrl, base + "/oauth2/v2.0/authorize");
            String token = orDefault(tokenUrl, base + "/oauth2/v2.0/token");
            // Multi-tenant: the per-tenant /discovery/v2.0/keys endpoint resolves
            // to the same shared keyset that signs tokens from any tenant. Using
            // /common (or /organizations etc.) for JWKS picks up all the keys
            // we need. Single-tenant: scoped to that tenant's keys.
            String jwks = orDefault(jwksUrl, base + "/discovery/v2.0/keys");
            String endSession = orDefault(endSessionUrl, base + "/oauth2/v2.0/logout");
            Pattern issPattern = multiTenant ? ENTRA_TENANT_ISSUER_PATTERN : null;
            return new ProviderConfig(
                    ExternalProviderId.MICROSOFT,
                    displayName, icon, order, webPickerVisible,
                    clientId, clientSecret, iss, issPattern, authorize, token, jwks, endSession,
                    postLogoutRedirectUri,
                    scopes,
                    new LinkedHashMap<>(),
                    usernameClaim, emailClaim, externalIdClaim,
                    hostedDomain, autoProvision, false);
        }
    }

    public static class Google
    {
        private boolean enabled = false;
        private String clientId = "";
        // Google requires this on the token endpoint even for PKCE-protected
        // SPAs when the client is registered as "Web application". The
        // "secret" is not really a secret in the SPA context — PKCE provides
        // the actual security — but Google rejects token requests without it.
        // For "Desktop app" client type, leave empty.
        private String clientSecret = "";
        private String hostedDomain = "";
        // Matches LDAP precedent — see Microsoft.autoProvision. For Google, set
        // a `hosted-domain` (Workspace) to scope auto-provisioning to a single
        // org; otherwise any verified Google account on Earth gets a rapla user.
        private boolean autoProvision = true;
        private boolean revokeOnLogout = false;
        private String displayName = "Sign in with Google";
        private String icon = "google";
        private int order = 20;
        private boolean webPickerVisible = true;
        private String authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        private String tokenUrl = "https://oauth2.googleapis.com/token";
        private String jwksUrl = "https://www.googleapis.com/oauth2/v3/certs";
        private String issuer = "https://accounts.google.com";
        private String endSessionUrl = "";
        private String usernameClaim = "email";
        private String emailClaim = "email";
        private String externalIdClaim = "sub";
        private List<String> scopes = List.of("openid", "profile", "email");
        private Map<String, String> extraAuthorizeParams = defaultGoogleExtraParams();

        private static Map<String, String> defaultGoogleExtraParams()
        {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("access_type", "offline");
            m.put("prompt", "consent");
            return m;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getHostedDomain() { return hostedDomain; }
        public void setHostedDomain(String hostedDomain) { this.hostedDomain = hostedDomain; }
        public boolean isAutoProvision() { return autoProvision; }
        public void setAutoProvision(boolean autoProvision) { this.autoProvision = autoProvision; }
        public boolean isRevokeOnLogout() { return revokeOnLogout; }
        public void setRevokeOnLogout(boolean revokeOnLogout) { this.revokeOnLogout = revokeOnLogout; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public boolean isWebPickerVisible() { return webPickerVisible; }
        public void setWebPickerVisible(boolean webPickerVisible) { this.webPickerVisible = webPickerVisible; }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public void setAuthorizeUrl(String authorizeUrl) { this.authorizeUrl = authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getJwksUrl() { return jwksUrl; }
        public void setJwksUrl(String jwksUrl) { this.jwksUrl = jwksUrl; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getEndSessionUrl() { return endSessionUrl; }
        public void setEndSessionUrl(String endSessionUrl) { this.endSessionUrl = endSessionUrl; }
        public String getUsernameClaim() { return usernameClaim; }
        public void setUsernameClaim(String usernameClaim) { this.usernameClaim = usernameClaim; }
        public String getEmailClaim() { return emailClaim; }
        public void setEmailClaim(String emailClaim) { this.emailClaim = emailClaim; }
        public String getExternalIdClaim() { return externalIdClaim; }
        public void setExternalIdClaim(String externalIdClaim) { this.externalIdClaim = externalIdClaim; }
        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }
        public Map<String, String> getExtraAuthorizeParams() { return extraAuthorizeParams; }
        public void setExtraAuthorizeParams(Map<String, String> extraAuthorizeParams) { this.extraAuthorizeParams = extraAuthorizeParams; }

        ProviderConfig toProviderConfig()
        {
            if (clientId == null || clientId.isEmpty())
            {
                throw new IllegalStateException(
                        "rapla.oauth.external.google.enabled=true requires rapla.oauth.external.google.client-id");
            }
            return new ProviderConfig(
                    ExternalProviderId.GOOGLE,
                    displayName, icon, order, webPickerVisible,
                    clientId, clientSecret, issuer, authorizeUrl, tokenUrl, jwksUrl, endSessionUrl,
                    "",
                    scopes,
                    extraAuthorizeParams == null ? new LinkedHashMap<>() : extraAuthorizeParams,
                    usernameClaim, emailClaim, externalIdClaim,
                    hostedDomain, autoProvision, revokeOnLogout);
        }
    }

    /**
     * Keycloak — a self-hosted OIDC provider (PRD 036 Phase 2.1). Every OIDC
     * endpoint is derived from {@code base-url + realm}; a Keycloak realm is
     * already a tenant, so one rapla deployment maps to one realm. A "public"
     * Keycloak client (PKCE, no secret) uses the direct token route; a
     * "confidential" client (secret set) routes through the BFF — same
     * per-secret logic as Google "Web application".
     */
    public static class Keycloak
    {
        private boolean enabled = false;
        // The Keycloak server's public base URL, e.g. https://keycloak.example.com.
        private String baseUrl = "";
        // The realm name. issuer = {base-url}/realms/{realm}.
        private String realm = "";
        private String clientId = "";
        // Empty for a Keycloak "public" client (PKCE-only). Set for a
        // "confidential" client — then the BFF adds it server-side.
        private String clientSecret = "";
        private String hostedDomain = "";
        // Matches the rapla LDAP / Entra / Google precedent — see Microsoft.autoProvision.
        private boolean autoProvision = true;
        private String displayName = "Sign in with Keycloak";
        private String icon = "keycloak";
        private int order = 15;
        private boolean webPickerVisible = true;
        // Keycloak issues the standard OIDC claims out of the box.
        private String usernameClaim = "preferred_username";
        private String emailClaim = "email";
        private String externalIdClaim = "sub";
        private String postLogoutRedirectUri = "";
        private List<String> scopes = List.of("openid", "profile", "email");

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getRealm() { return realm; }
        public void setRealm(String realm) { this.realm = realm; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getHostedDomain() { return hostedDomain; }
        public void setHostedDomain(String hostedDomain) { this.hostedDomain = hostedDomain; }
        public boolean isAutoProvision() { return autoProvision; }
        public void setAutoProvision(boolean autoProvision) { this.autoProvision = autoProvision; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public boolean isWebPickerVisible() { return webPickerVisible; }
        public void setWebPickerVisible(boolean webPickerVisible) { this.webPickerVisible = webPickerVisible; }
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

        ProviderConfig toProviderConfig()
        {
            if (baseUrl == null || baseUrl.isEmpty())
            {
                throw new IllegalStateException(
                        "rapla.oauth.external.keycloak.enabled=true requires rapla.oauth.external.keycloak.base-url");
            }
            if (realm == null || realm.isEmpty())
            {
                throw new IllegalStateException(
                        "rapla.oauth.external.keycloak.enabled=true requires rapla.oauth.external.keycloak.realm");
            }
            if (clientId == null || clientId.isEmpty())
            {
                throw new IllegalStateException(
                        "rapla.oauth.external.keycloak.enabled=true requires rapla.oauth.external.keycloak.client-id");
            }
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String realmBase = base + "/realms/" + realm;
            String oidc = realmBase + "/protocol/openid-connect";
            return new ProviderConfig(
                    ExternalProviderId.KEYCLOAK,
                    displayName, icon, order, webPickerVisible,
                    clientId, clientSecret,
                    realmBase,
                    oidc + "/auth", oidc + "/token", oidc + "/certs", oidc + "/logout",
                    postLogoutRedirectUri,
                    scopes,
                    new LinkedHashMap<>(),
                    usernameClaim, emailClaim, externalIdClaim,
                    hostedDomain, autoProvision, false);
        }
    }

    private static String orDefault(String v, String fallback)
    {
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
