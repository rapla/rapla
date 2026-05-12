package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/auth/oauth")
public class OAuthConfigController
{
    private final boolean enabled;
    private final String clientId;
    private final List<String> scopes;
    private final boolean showPasteFallback;
    private final String contextPath;

    // Properties resolved by rapla.oauth.* in application.yml; deployments
    // override via env vars (RAPLA_OAUTH_CLIENT_ID=...) or profile yaml.
    public OAuthConfigController(
            @Value("${rapla.oauth.enabled:true}") boolean enabled,
            @Value("${rapla.oauth.client-id:rapla-client}") String clientId,
            @Value("${rapla.oauth.scopes:openid,profile}") List<String> scopes,
            @Value("${rapla.oauth.show-paste-fallback:false}") boolean showPasteFallback,
            @Value("${server.servlet.context-path:}") String contextPath)
    {
        this.enabled = enabled;
        this.clientId = clientId;
        this.scopes = scopes;
        this.showPasteFallback = showPasteFallback;
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    @GetMapping("/config")
    public OAuthConfig config(HttpServletRequest request)
    {
        if (!enabled)
        {
            return new OAuthConfig(false, null, null, null, List.of(), false);
        }
        String base = baseUrl(request);
        return new OAuthConfig(
                true,
                clientId,
                base + "/oauth2/authorize",
                base + "/oauth2/token",
                scopes,
                showPasteFallback);
    }

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
        public final String authorizeUrl;
        public final String tokenUrl;
        public final List<String> scopes;
        public final boolean showPasteFallback;

        public OAuthConfig(boolean enabled, String clientId, String authorizeUrl, String tokenUrl,
                           List<String> scopes, boolean showPasteFallback)
        {
            this.enabled = enabled;
            this.clientId = clientId;
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.scopes = scopes;
            this.showPasteFallback = showPasteFallback;
        }

        public boolean isEnabled() { return enabled; }
        public String getClientId() { return clientId; }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public List<String> getScopes() { return scopes; }
        public boolean isShowPasteFallback() { return showPasteFallback; }
    }
}
