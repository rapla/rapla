package org.rapla.server.util;

import java.net.URI;
import java.util.Collection;

/**
 * Pure URI check for the server's custom OAuth redirect validator: accept a
 * redirect URI if its scheme/host/port match the public origin the auth-server
 * request was received on, AND its path is in the configured allowlist.
 *
 * <p>Lets the Angular SPA register a single relative-path redirect
 * (e.g. {@code /app/auth/callback}) without per-deployment configuration of
 * the public host. The path allowlist is configured directly via
 * {@code rapla.oauth.same-origin-callback-paths} in {@code application.yml}.
 *
 * <p>Lives in rapla-core so both the server validator and its tests can use it.
 *
 * <p><b>Why this is non-standard:</b> RFC 9700 (OAuth 2.0 Security BCP) and
 * OAuth 2.1 require exact string matching for redirect URIs. This relaxation
 * trades that spec-strictness for zero-config multi-deployment operation, and
 * is bounded by (a) the request-origin / X-Forwarded-* trust boundary
 * (controlled by the reverse proxy) and (b) mandatory PKCE
 * ({@code require-proof-key: true} on the Spring AS client). See
 * {@code docs/authentication.md} for the full design rationale.
 */
public final class SameOriginUriCheck
{
    private SameOriginUriCheck() {}

    /**
     * @param redirectUri  the candidate redirect URI from the authorize request
     * @param origScheme   the scheme of the request that initiated /oauth2/authorize (e.g. "https")
     * @param origHost     the public host of that request (honoring X-Forwarded-Host if proxied)
     * @param origPort     the public port of that request (honoring X-Forwarded-Port or X-Forwarded-Host)
     * @param allowedPaths the configured set of paths the SPA / clients are permitted to redirect to
     *                     (from {@code rapla.oauth.same-origin-callback-paths})
     * @return true if same-origin (scheme/host/port match) and path is in the allowlist
     */
    public static boolean isSameOriginRedirect(String redirectUri,
                                               String origScheme,
                                               String origHost,
                                               int origPort,
                                               Collection<String> allowedPaths)
    {
        if (redirectUri == null || origScheme == null || origHost == null) return false;
        URI parsed;
        try { parsed = URI.create(redirectUri); }
        catch (Exception e) { return false; }
        if (parsed.getScheme() == null || parsed.getHost() == null) return false;
        if (!origScheme.equalsIgnoreCase(parsed.getScheme())) return false;
        boolean bothLoopback = isLoopbackHost(parsed.getHost()) && isLoopbackHost(origHost);
        // For non-loopback origins (production deployments behind a hostname),
        // host AND port must match exactly. For loopback↔loopback the host
        // names are treated as interchangeable variants of "local machine"
        // and port mismatch is allowed — supports the dev SPA at :4200
        // talking to the AS at :8051 without an explicit port registration
        // in YAML. The path allowlist still gates everything.
        if (!bothLoopback)
        {
            if (!origHost.equalsIgnoreCase(parsed.getHost())) return false;
            int redirectPort = parsed.getPort() == -1 ? defaultPort(parsed.getScheme()) : parsed.getPort();
            int requestPort = origPort <= 0 ? defaultPort(origScheme) : origPort;
            if (redirectPort != requestPort) return false;
        }
        String path = parsed.getPath();
        if (path == null || path.isEmpty()) return false;
        return allowedPaths.contains(path);
    }

    private static int defaultPort(String scheme)
    {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static boolean isLoopbackHost(String host)
    {
        if (host == null) return false;
        return "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "[::1]".equals(host);
    }
}
