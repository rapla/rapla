package org.rapla.server.util;

import java.net.URI;
import java.util.Collection;

/**
 * Pure URI check used by the server's custom OAuth redirect validator
 * (and unit-testable without any Spring scaffolding). A redirect URI is
 * a "WSL bridge" URI when its host is in <b>172.16.0.0/12</b> (the default
 * Hyper-V vSwitch range used by WSL2) and its path matches the path of one
 * of the client's registered redirect URIs.
 *
 * <p>Lives in rapla-core so both the server validator and its tests can
 * use it.
 */
public final class WslBridgeUriCheck
{
    private WslBridgeUriCheck() {}

    /**
     * @param redirectUri the candidate redirect URI from the authorize request
     * @param registeredUris the redirect URIs configured for the client
     * @return true if the URI is on the WSL bridge subnet AND its path matches one of the registered URIs
     */
    public static boolean isWslBridgeRedirect(String redirectUri, Collection<String> registeredUris)
    {
        if (redirectUri == null) return false;
        URI parsed;
        try { parsed = URI.create(redirectUri); }
        catch (Exception e) { return false; }
        if (!isWslBridgeHost(parsed.getHost())) return false;
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
            catch (Exception ignore) { /* skip malformed registered */ }
        }
        return false;
    }

    static boolean isWslBridgeHost(String host)
    {
        if (host == null || !host.startsWith("172.")) return false;
        String[] octets = host.split("\\.");
        if (octets.length != 4) return false;
        try
        {
            Integer.parseInt(octets[0]);
            int second = Integer.parseInt(octets[1]);
            Integer.parseInt(octets[2]);
            Integer.parseInt(octets[3]);
            return second >= 16 && second <= 31;
        }
        catch (NumberFormatException e) { return false; }
    }
}
