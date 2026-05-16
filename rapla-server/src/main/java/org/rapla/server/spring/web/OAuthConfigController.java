package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the SPA / Swing client's OAuth2 + OIDC endpoint
 * locations. The bundled Spring Authorization Server defaults are computed
 * from the incoming request (so the dev proxy at :4200 produces :4200 URLs
 * once server.forward-headers-strategy=FRAMEWORK is set), but every endpoint
 * can be overridden via {@code rapla.oauth.*-url} properties for deployments
 * that delegate auth to Keycloak / Auth0 / Okta / etc.
 */
@RestController
@RequestMapping(value = "/api/auth/oauth", produces = "application/json")
public class OAuthConfigController
{
    private final boolean enabled;
    private final String clientId;
    private final List<String> scopes;
    private final boolean showPasteFallback;
    private final String publicBaseUrlOverride;
    private final String authorizeUrlOverride;
    private final String tokenUrlOverride;
    // refreshUrl removed in PRD 041 — clients use tokenUrl for both code exchange + refresh.
    private final String logoutUrlOverride;
    private final String jwksUrlOverride;
    private final String userinfoUrlOverride;
    private final String endSessionUrlOverride;
    private final String issuerOverride;
    private final String contextPath;
    private final ExternalProvidersProperties externalProviders;
    private final String pickerMode;
    private final String pickerPrimary;
    private final boolean raplaInPicker;

    public OAuthConfigController(
            @Value("${rapla.oauth.enabled:true}") boolean enabled,
            @Value("${rapla.oauth.client-id:rapla-client}") String clientId,
            @Value("${rapla.oauth.scopes:openid,profile,offline_access}") List<String> scopes,
            @Value("${rapla.oauth.show-paste-fallback:false}") boolean showPasteFallback,
            @Value("${rapla.oauth.public-base-url:}") String publicBaseUrlOverride,
            @Value("${rapla.oauth.authorize-url:}") String authorizeUrlOverride,
            @Value("${rapla.oauth.token-url:}") String tokenUrlOverride,
            @Value("${rapla.oauth.logout-url:}") String logoutUrlOverride,
            @Value("${rapla.oauth.jwks-url:}") String jwksUrlOverride,
            @Value("${rapla.oauth.userinfo-url:}") String userinfoUrlOverride,
            @Value("${rapla.oauth.end-session-url:}") String endSessionUrlOverride,
            @Value("${rapla.oauth.issuer:}") String issuerOverride,
            @Value("${server.servlet.context-path:}") String contextPath,
            @Value("${rapla.oauth.web.picker.mode:auto}") String pickerMode,
            @Value("${rapla.oauth.web.picker.primary:rapla}") String pickerPrimary,
            @Value("${rapla.oauth.web.rapla-in-picker:true}") boolean raplaInPicker,
            ExternalProvidersProperties externalProviders)
    {
        this.enabled = enabled;
        this.clientId = clientId;
        this.scopes = scopes;
        this.showPasteFallback = showPasteFallback;
        this.publicBaseUrlOverride = nullToEmpty(publicBaseUrlOverride);
        this.authorizeUrlOverride = nullToEmpty(authorizeUrlOverride);
        this.tokenUrlOverride = nullToEmpty(tokenUrlOverride);
        this.logoutUrlOverride = nullToEmpty(logoutUrlOverride);
        this.jwksUrlOverride = nullToEmpty(jwksUrlOverride);
        this.userinfoUrlOverride = nullToEmpty(userinfoUrlOverride);
        this.endSessionUrlOverride = nullToEmpty(endSessionUrlOverride);
        this.issuerOverride = nullToEmpty(issuerOverride);
        this.contextPath = nullToEmpty(contextPath);
        this.externalProviders = externalProviders;
        this.pickerMode = pickerMode == null || pickerMode.isEmpty() ? "auto" : pickerMode;
        this.pickerPrimary = pickerPrimary == null || pickerPrimary.isEmpty() ? "rapla" : pickerPrimary;
        this.raplaInPicker = raplaInPicker;
    }

    @GetMapping("/config")
    public OAuthConfig config(HttpServletRequest request)
    {
        if (!enabled)
        {
            return new OAuthConfig(false, null, null, null, null, null, null, null, null,
                    List.of(), false, new Picker("never", "rapla"), List.of());
        }
        // App-facing base: respects X-Forwarded-* so dev proxy on :4200 produces
        // :4200 URLs. Used for the rapla REST API (/api/auth/refresh, /logout).
        String appBase = baseUrl(request);
        // OAuth-facing base: bypasses the dev proxy by default. When
        // rapla.oauth.public-base-url is set, OAuth endpoints (authorize, token,
        // jwks, userinfo, end-session) point at that absolute URL — typically
        // http://localhost:8051 in dev, https://idp.example.com for external
        // Keycloak. Empty falls back to appBase (production same-origin case).
        String oauthBase = publicBaseUrlOverride.isEmpty() ? appBase : publicBaseUrlOverride;
        String issuer = issuerOverride.isEmpty() ? oauthBase : issuerOverride;
        String authorizeUrl = authorizeUrlOverride.isEmpty() ? oauthBase + "/oauth2/authorize" : authorizeUrlOverride;
        String tokenUrl = tokenUrlOverride.isEmpty() ? oauthBase + "/oauth2/token" : tokenUrlOverride;
        // PRD 041: refresh consolidated onto /oauth2/token (OAuth 2.1 standard,
        // form-encoded body) — same URL as tokenUrl. The legacy refreshUrl
        // field was removed; clients use tokenUrl for both code exchange + refresh.
        // OIDC RP-initiated logout endpoint (OpenID Connect RP-Initiated Logout 1.0).
        // Spring Authorization Server defaults to /connect/logout — also advertised
        // by .well-known/openid-configuration as end_session_endpoint. Both the
        // Swing client and Angular SPA hit this URL on logout: it clears the
        // HttpSession AND honors post_logout_redirect_uri. Callers should include
        // id_token_hint (or client_id) so the OP can authenticate the logout
        // request per the spec. Override for external IdPs (Keycloak:
        // /realms/{realm}/protocol/openid-connect/logout).
        String logoutUrl = logoutUrlOverride.isEmpty() ? oauthBase + "/connect/logout" : logoutUrlOverride;
        String jwksUrl = jwksUrlOverride.isEmpty() ? oauthBase + "/oauth2/jwks" : jwksUrlOverride;
        String userinfoUrl = userinfoUrlOverride.isEmpty() ? oauthBase + "/userinfo" : userinfoUrlOverride;
        String endSessionUrl = endSessionUrlOverride.isEmpty() ? oauthBase + "/connect/logout" : endSessionUrlOverride;

        // Top-level fields always reflect the rapla embedded SAS so that Swing
        // (which is being deprecated — PRD 036) sees today's discovery shape
        // unchanged regardless of whether external providers are enabled.
        List<ProviderEntry> providers = buildProviders(
                clientId, issuer, authorizeUrl, tokenUrl, endSessionUrl, jwksUrl, scopes, appBase);

        return new OAuthConfig(
                true,
                clientId,
                issuer,
                authorizeUrl,
                tokenUrl,
                logoutUrl,
                jwksUrl,
                userinfoUrl,
                endSessionUrl,
                scopes,
                showPasteFallback,
                new Picker(pickerMode, pickerPrimary),
                providers);
    }

    private List<ProviderEntry> buildProviders(String localClientId, String localIssuer,
                                               String localAuthorize, String localToken,
                                               String localEndSession, String localJwks,
                                               List<String> localScopes, String appBase)
    {
        List<ProviderEntry> out = new ArrayList<>();
        // The rapla SAS entry exposes the real local token endpoint — no BFF
        // needed because there's no client_secret in the rapla SAS path.
        // Visible in the web picker by default (rapla.oauth.web.rapla-in-picker
        // defaults to true) — every deployment has at least one rapla-local
        // admin account, and break-glass access matters when external IdPs
        // are misconfigured or unreachable. With only the rapla provider
        // enabled and picker mode=auto, this still doesn't render a picker
        // (mode=auto needs ≥2 visible providers); the SPA auto-fires
        // rapla SAS. Set false for strict SSO-only deployments.
        out.add(new ProviderEntry(
                "rapla",
                "Sign in with rapla password",
                "rapla",
                0,
                raplaInPicker,
                localClientId,
                localIssuer,
                localAuthorize,
                localToken,
                localJwks,
                localEndSession,
                localScopes,
                new LinkedHashMap<>()));
        if (externalProviders != null)
        {
            for (ProviderConfig p : externalProviders.enabledProviders())
            {
                // Route choice per-provider:
                //   - client_secret configured → BFF (server adds the secret
                //     and forwards to the IdP). Used for Google "Web app" and
                //     Entra "Web" platform clients.
                //   - no client_secret → SPA POSTs to the IdP directly. Required
                //     for Entra "Single-page application" platform — Entra rejects
                //     server-side token requests for SPA clients with
                //     AADSTS9002327 ("must be cross-origin"). PKCE is the security
                //     in this case; no secret to leak.
                String tokenUrl = p.clientSecret().isEmpty()
                        ? p.tokenUrl()
                        : appBase + "/api/auth/oauth/exchange/" + p.id();
                out.add(new ProviderEntry(
                        p.id(),
                        p.displayName(),
                        p.icon(),
                        p.order(),
                        p.webPickerVisible(),
                        p.clientId(),
                        p.issuer(),
                        p.authorizeUrl(),
                        tokenUrl,
                        p.jwksUrl(),
                        p.endSessionUrl(),
                        p.scopes(),
                        new LinkedHashMap<>(p.extraAuthorizeParams())));
            }
        }
        out.sort(Comparator.comparingInt(ProviderEntry::getOrder));
        return out;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private String baseUrl(HttpServletRequest request)
    {
        URI requestUri = URI.create(request.getRequestURL().toString());
        StringBuilder b = new StringBuilder();
        b.append(requestUri.getScheme()).append("://").append(requestUri.getHost());
        if (requestUri.getPort() != -1 && requestUri.getPort() != defaultPort(requestUri.getScheme()))
        {
            b.append(':').append(requestUri.getPort());
        }
        if (!contextPath.isEmpty() && !"/".equals(contextPath))
        {
            b.append(contextPath);
        }
        return b.toString();
    }

    private static int defaultPort(String scheme)
    {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    public static final class OAuthConfig
    {
        public final boolean enabled;
        public final String clientId;
        public final String issuer;
        public final String authorizeUrl;
        public final String tokenUrl;
        public final String logoutUrl;
        public final String jwksUrl;
        public final String userinfoUrl;
        public final String endSessionUrl;
        public final List<String> scopes;
        public final boolean showPasteFallback;
        public final Picker picker;
        public final List<ProviderEntry> providers;

        public OAuthConfig(boolean enabled, String clientId, String issuer, String authorizeUrl,
                           String tokenUrl, String logoutUrl, String jwksUrl,
                           String userinfoUrl, String endSessionUrl, List<String> scopes,
                           boolean showPasteFallback, Picker picker, List<ProviderEntry> providers)
        {
            this.enabled = enabled;
            this.clientId = clientId;
            this.issuer = issuer;
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.logoutUrl = logoutUrl;
            this.jwksUrl = jwksUrl;
            this.userinfoUrl = userinfoUrl;
            this.endSessionUrl = endSessionUrl;
            this.scopes = scopes;
            this.showPasteFallback = showPasteFallback;
            this.picker = picker;
            this.providers = providers;
        }

        public boolean isEnabled() { return enabled; }
        public String getClientId() { return clientId; }
        public String getIssuer() { return issuer; }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public String getLogoutUrl() { return logoutUrl; }
        public String getJwksUrl() { return jwksUrl; }
        public String getUserinfoUrl() { return userinfoUrl; }
        public String getEndSessionUrl() { return endSessionUrl; }
        public List<String> getScopes() { return scopes; }
        public boolean isShowPasteFallback() { return showPasteFallback; }
        public Picker getPicker() { return picker; }
        public List<ProviderEntry> getProviders() { return providers; }
    }

    public static final class Picker
    {
        public final String mode;
        public final String primary;

        public Picker(String mode, String primary)
        {
            this.mode = mode;
            this.primary = primary;
        }

        public String getMode() { return mode; }
        public String getPrimary() { return primary; }
    }

    public static final class ProviderEntry
    {
        public final String id;
        public final String displayName;
        public final String icon;
        public final int order;
        public final boolean webPickerVisible;
        public final String clientId;
        public final String issuer;
        public final String authorizeUrl;
        public final String tokenUrl;
        public final String jwksUrl;
        public final String endSessionUrl;
        public final List<String> scopes;
        public final Map<String, String> extraAuthorizeParams;

        public ProviderEntry(String id, String displayName, String icon, int order,
                             boolean webPickerVisible, String clientId,
                             String issuer, String authorizeUrl, String tokenUrl, String jwksUrl,
                             String endSessionUrl, List<String> scopes,
                             Map<String, String> extraAuthorizeParams)
        {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.order = order;
            this.webPickerVisible = webPickerVisible;
            this.clientId = clientId;
            this.issuer = issuer;
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.jwksUrl = jwksUrl;
            this.endSessionUrl = endSessionUrl;
            this.scopes = scopes;
            this.extraAuthorizeParams = extraAuthorizeParams;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getIcon() { return icon; }
        public int getOrder() { return order; }
        public boolean isWebPickerVisible() { return webPickerVisible; }
        public String getClientId() { return clientId; }
        public String getIssuer() { return issuer; }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public String getJwksUrl() { return jwksUrl; }
        public String getEndSessionUrl() { return endSessionUrl; }
        public List<String> getScopes() { return scopes; }
        public Map<String, String> getExtraAuthorizeParams() { return extraAuthorizeParams; }
    }
}
