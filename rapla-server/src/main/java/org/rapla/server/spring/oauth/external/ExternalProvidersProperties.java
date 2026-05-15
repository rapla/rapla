package org.rapla.server.spring.oauth.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public Microsoft getMicrosoft() { return microsoft; }
    public void setMicrosoft(Microsoft microsoft) { this.microsoft = microsoft; }

    public Google getGoogle() { return google; }
    public void setGoogle(Google google) { this.google = google; }

    public List<ProviderConfig> enabledProviders()
    {
        List<ProviderConfig> out = new ArrayList<>(2);
        if (microsoft.isEnabled()) out.add(microsoft.toProviderConfig());
        if (google.isEnabled()) out.add(google.toProviderConfig());
        return out;
    }

    public Optional<ProviderConfig> byIssuer(String issuer)
    {
        if (issuer == null) return Optional.empty();
        return enabledProviders().stream()
                .filter(p -> issuer.equals(p.issuer()))
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
            String base = "https://login.microsoftonline.com/" + tenant;
            String iss = orDefault(issuer, base + "/v2.0");
            String authorize = orDefault(authorizeUrl, base + "/oauth2/v2.0/authorize");
            String token = orDefault(tokenUrl, base + "/oauth2/v2.0/token");
            String jwks = orDefault(jwksUrl, base + "/discovery/v2.0/keys");
            String endSession = orDefault(endSessionUrl, base + "/oauth2/v2.0/logout");
            return new ProviderConfig(
                    ExternalProviderId.MICROSOFT,
                    displayName, icon, order, webPickerVisible,
                    clientId, clientSecret, iss, authorize, token, jwks, endSession,
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

    private static String orDefault(String v, String fallback)
    {
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
