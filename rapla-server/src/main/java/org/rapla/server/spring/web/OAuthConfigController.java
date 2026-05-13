package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Single source of truth for the SPA / Swing client's OAuth2 + OIDC endpoint
 * locations. The bundled Spring Authorization Server defaults are computed
 * from the incoming request (so the dev proxy at :4200 produces :4200 URLs
 * once server.forward-headers-strategy=FRAMEWORK is set), but every endpoint
 * can be overridden via {@code rapla.oauth.*-url} properties for deployments
 * that delegate auth to Keycloak / Auth0 / Okta / etc.
 */
@RestController
@RequestMapping(value = "/auth/oauth", produces = "application/json")
public class OAuthConfigController
{
    private final boolean enabled;
    private final String clientId;
    private final List<String> scopes;
    private final boolean showPasteFallback;
    private final String publicBaseUrlOverride;
    private final String authorizeUrlOverride;
    private final String tokenUrlOverride;
    private final String refreshUrlOverride;
    private final String logoutUrlOverride;
    private final String jwksUrlOverride;
    private final String userinfoUrlOverride;
    private final String endSessionUrlOverride;
    private final String issuerOverride;
    private final String contextPath;

    public OAuthConfigController(
            @Value("${rapla.oauth.enabled:true}") boolean enabled,
            @Value("${rapla.oauth.client-id:rapla-client}") String clientId,
            @Value("${rapla.oauth.scopes:openid,profile,offline_access}") List<String> scopes,
            @Value("${rapla.oauth.show-paste-fallback:false}") boolean showPasteFallback,
            @Value("${rapla.oauth.public-base-url:}") String publicBaseUrlOverride,
            @Value("${rapla.oauth.authorize-url:}") String authorizeUrlOverride,
            @Value("${rapla.oauth.token-url:}") String tokenUrlOverride,
            @Value("${rapla.oauth.refresh-url:}") String refreshUrlOverride,
            @Value("${rapla.oauth.logout-url:}") String logoutUrlOverride,
            @Value("${rapla.oauth.jwks-url:}") String jwksUrlOverride,
            @Value("${rapla.oauth.userinfo-url:}") String userinfoUrlOverride,
            @Value("${rapla.oauth.end-session-url:}") String endSessionUrlOverride,
            @Value("${rapla.oauth.issuer:}") String issuerOverride,
            @Value("${server.servlet.context-path:}") String contextPath)
    {
        this.enabled = enabled;
        this.clientId = clientId;
        this.scopes = scopes;
        this.showPasteFallback = showPasteFallback;
        this.publicBaseUrlOverride = nullToEmpty(publicBaseUrlOverride);
        this.authorizeUrlOverride = nullToEmpty(authorizeUrlOverride);
        this.tokenUrlOverride = nullToEmpty(tokenUrlOverride);
        this.refreshUrlOverride = nullToEmpty(refreshUrlOverride);
        this.logoutUrlOverride = nullToEmpty(logoutUrlOverride);
        this.jwksUrlOverride = nullToEmpty(jwksUrlOverride);
        this.userinfoUrlOverride = nullToEmpty(userinfoUrlOverride);
        this.endSessionUrlOverride = nullToEmpty(endSessionUrlOverride);
        this.issuerOverride = nullToEmpty(issuerOverride);
        this.contextPath = nullToEmpty(contextPath);
    }

    @GetMapping("/config")
    public OAuthConfig config(HttpServletRequest request)
    {
        if (!enabled)
        {
            return new OAuthConfig(false, null, null, null, null, null, null, null, null, null, List.of(), false);
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
        // PRD 031 Phase 2: REST auth moved under /api/. Stays on app origin.
        String refreshUrl = refreshUrlOverride.isEmpty() ? appBase + "/api/auth/refresh" : refreshUrlOverride;
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
        return new OAuthConfig(
                true,
                clientId,
                issuer,
                authorizeUrl,
                tokenUrl,
                refreshUrl,
                logoutUrl,
                jwksUrl,
                userinfoUrl,
                endSessionUrl,
                scopes,
                showPasteFallback);
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
        public final String refreshUrl;
        public final String logoutUrl;
        public final String jwksUrl;
        public final String userinfoUrl;
        public final String endSessionUrl;
        public final List<String> scopes;
        public final boolean showPasteFallback;

        public OAuthConfig(boolean enabled, String clientId, String issuer, String authorizeUrl,
                           String tokenUrl, String refreshUrl, String logoutUrl, String jwksUrl,
                           String userinfoUrl, String endSessionUrl, List<String> scopes,
                           boolean showPasteFallback)
        {
            this.enabled = enabled;
            this.clientId = clientId;
            this.issuer = issuer;
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.refreshUrl = refreshUrl;
            this.logoutUrl = logoutUrl;
            this.jwksUrl = jwksUrl;
            this.userinfoUrl = userinfoUrl;
            this.endSessionUrl = endSessionUrl;
            this.scopes = scopes;
            this.showPasteFallback = showPasteFallback;
        }

        public boolean isEnabled() { return enabled; }
        public String getClientId() { return clientId; }
        public String getIssuer() { return issuer; }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public String getRefreshUrl() { return refreshUrl; }
        public String getLogoutUrl() { return logoutUrl; }
        public String getJwksUrl() { return jwksUrl; }
        public String getUserinfoUrl() { return userinfoUrl; }
        public String getEndSessionUrl() { return endSessionUrl; }
        public List<String> getScopes() { return scopes; }
        public boolean isShowPasteFallback() { return showPasteFallback; }
    }
}
