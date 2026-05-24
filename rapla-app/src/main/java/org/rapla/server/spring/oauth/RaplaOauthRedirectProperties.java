package org.rapla.server.spring.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the custom redirect-URI validators in
 * {@code AuthorizationServerConfig.redirectUriAndScopeValidator}. Bound from
 * {@code rapla.oauth.*} in application.yml. See docs/authentication.md
 * §"Spring redirect URIs" for the design rationale.
 *
 * <p>Note: other {@code rapla.oauth.*} keys (e.g. enabled, client-id,
 * swing-legacy-login) are read elsewhere via {@code @Value} on
 * {@code OAuthConfigController}. {@code @ConfigurationProperties} is
 * non-exclusive — they continue to work independently.
 */
@ConfigurationProperties(prefix = "rapla.oauth")
public class RaplaOauthRedirectProperties
{
    /** Accept any path on 127.0.0.1 / [::1] regardless of port. Default on. */
    private boolean allowLoopbackRedirects = true;

    /** Accept hosts in 172.16.0.0/12 if path is in {@link #sameOriginCallbackPaths}. Default on. */
    private boolean allowWslBridgeRedirects = true;

    /**
     * Accept URIs whose scheme/host/port match the AS request's public origin
     * (honoring X-Forwarded-*) if path is in {@link #sameOriginCallbackPaths}.
     * Default on.
     */
    private boolean allowSameOriginRedirects = true;

    /**
     * Path allowlist used by both the WSL-bridge and same-origin validators —
     * the single source of truth for "what paths can the rapla client
     * redirect to". Defaults to an empty list so a misconfigured deployment
     * fails closed (the validator rejects everything that isn't covered by
     * Spring AS's exact-match against redirect-uris or by the loopback wildcard).
     */
    private List<String> sameOriginCallbackPaths = new ArrayList<>();

    public boolean isAllowLoopbackRedirects() { return allowLoopbackRedirects; }
    public void setAllowLoopbackRedirects(boolean v) { this.allowLoopbackRedirects = v; }

    public boolean isAllowWslBridgeRedirects() { return allowWslBridgeRedirects; }
    public void setAllowWslBridgeRedirects(boolean v) { this.allowWslBridgeRedirects = v; }

    public boolean isAllowSameOriginRedirects() { return allowSameOriginRedirects; }
    public void setAllowSameOriginRedirects(boolean v) { this.allowSameOriginRedirects = v; }

    public List<String> getSameOriginCallbackPaths() { return sameOriginCallbackPaths; }
    public void setSameOriginCallbackPaths(List<String> v) { this.sameOriginCallbackPaths = v; }
}
