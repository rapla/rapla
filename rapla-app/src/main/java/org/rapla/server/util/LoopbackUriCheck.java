package org.rapla.server.util;

import java.net.URI;
import java.util.Collection;

/**
 * Pure URI check for the server's custom OAuth redirect validator: accept a
 * redirect URI on a loopback IP literal ({@code 127.0.0.1} or {@code [::1]})
 * at any port, PROVIDED its path is in the configured allowlist
 * ({@code rapla.oauth.same-origin-callback-paths}, shared with the same-origin
 * and WSL-bridge validators). Local processes on the loopback interface are
 * trusted, so we accept any port; the path allowlist is the security gate
 * that's consistent across all three rapla custom validators.
 *
 * <p>Hostnames that <em>resolve to</em> loopback (notably {@code localhost})
 * are NOT accepted here. RFC 8252 §8.3 explicitly discourages {@code localhost}
 * because DNS lookups can be hijacked. {@link SameOriginUriCheck}'s
 * loopback-port-relaxation rule handles {@code localhost} cases where both
 * sides of the origin comparison are loopback variants.
 *
 * <p>Lives in rapla-core so both the server validator and its tests can use it.
 */
public final class LoopbackUriCheck
{
    private LoopbackUriCheck() {}

    /**
     * @param redirectUri  the candidate redirect URI from the authorize request
     * @param allowedPaths the configured callback paths from
     *                     {@code rapla.oauth.same-origin-callback-paths}
     * @return true if the URI's host is the IPv4 loopback ({@code 127.0.0.1})
     *         or IPv6 loopback ({@code [::1]}) AND its path is in the allowlist
     *         (any port is accepted)
     */
    public static boolean isLoopbackRedirect(String redirectUri, Collection<String> allowedPaths)
    {
        if (redirectUri == null || redirectUri.isEmpty()) return false;
        URI parsed;
        try { parsed = URI.create(redirectUri); }
        catch (Exception e) { return false; }
        if (parsed.getScheme() == null) return false;
        String host = parsed.getHost();
        if (host == null) return false;
        if (!"127.0.0.1".equals(host) && !"[::1]".equals(host)) return false;
        String path = parsed.getPath();
        if (path == null || path.isEmpty()) return false;
        return allowedPaths.contains(path);
    }
}
