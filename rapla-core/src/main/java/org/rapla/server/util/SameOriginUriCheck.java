package org.rapla.server.util;

import java.net.URI;
import java.util.Collection;

/**
 * Pure URI check for the server's custom OAuth redirect validator: accept a
 * redirect URI if its scheme/host/port match the public origin the auth-server
 * request was received on, AND its path matches one of the client's
 * registered redirect URI paths. Lets the Angular SPA register a single
 * relative-path redirect (e.g. {@code /auth/callback}) without per-deployment
 * configuration of the public host.
 *
 * <p>Lives in rapla-core so both the server validator and its tests can use it.
 */
public final class SameOriginUriCheck
{
    private SameOriginUriCheck() {}

    /**
     * @param redirectUri the candidate redirect URI from the authorize request
     * @param origScheme  the scheme of the request that initiated /oauth2/authorize (e.g. "https")
     * @param origHost    the public host of that request (honoring X-Forwarded-Host if proxied)
     * @param origPort    the public port of that request (honoring X-Forwarded-Port or X-Forwarded-Host)
     * @param registeredUris the redirect URIs configured for the client
     * @return true if same-origin (scheme/host/port match) and path matches a registered URI
     */
    public static boolean isSameOriginRedirect(String redirectUri,
                                               String origScheme,
                                               String origHost,
                                               int origPort,
                                               Collection<String> registeredUris)
    {
        if (redirectUri == null || origScheme == null || origHost == null) return false;
        URI parsed;
        try { parsed = URI.create(redirectUri); }
        catch (Exception e) { return false; }
        if (parsed.getScheme() == null || parsed.getHost() == null) return false;
        if (!origScheme.equalsIgnoreCase(parsed.getScheme())) return false;
        if (!origHost.equalsIgnoreCase(parsed.getHost())) return false;
        int redirectPort = parsed.getPort() == -1 ? defaultPort(parsed.getScheme()) : parsed.getPort();
        int requestPort = origPort <= 0 ? defaultPort(origScheme) : origPort;
        if (redirectPort != requestPort) return false;
        String path = parsed.getPath();
        if (path == null || path.isEmpty()) return false;
        for (String registered : registeredUris)
        {
            try
            {
                URI regUri = URI.create(registered);
                if (path.equals(regUri.getPath()))
                {
                    return true;
                }
            }
            catch (Exception ignore) { /* skip malformed */ }
        }
        return false;
    }

    private static int defaultPort(String scheme)
    {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
