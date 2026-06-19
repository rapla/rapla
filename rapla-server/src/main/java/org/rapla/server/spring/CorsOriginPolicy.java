package org.rapla.server.spring;

import java.net.URI;
import java.util.Collection;

/**
 * Zero-config CORS origin policy (A2). Replaces the previous
 * {@code allowedOriginPatterns("*") + allowCredentials(true)} — which reflected
 * ANY origin (incl. {@code evil.com}) with credentials.
 *
 * <p>Production is same-origin (SPA + OAuth both served by the one app), so the
 * browser never runs a CORS check there — a restrictive policy doesn't touch it.
 * CORS is only exercised in dev, where the SPA on a dev origin hits {@code /oauth2/*}
 * (and friends) cross-origin. So we allow, with credentials and zero config:
 * <ul>
 *   <li><b>loopback</b> — {@code localhost} / {@code 127.0.0.0/8} / {@code [::1]}, any port</li>
 *   <li><b>WSL bridge</b> — {@code 172.16.0.0/12} (default Hyper-V vSwitch range), any port</li>
 *   <li>any extra origin listed in {@code rapla.cors.allowed-origins} (default none)</li>
 * </ul>
 * Everything else (a remote {@code evil.com}) is rejected.
 */
public final class CorsOriginPolicy
{
    private CorsOriginPolicy() {}

    public static boolean isAllowedOrigin(String origin, Collection<String> configuredOrigins)
    {
        if (origin == null || origin.isBlank())
        {
            return false;
        }
        if (configuredOrigins != null && configuredOrigins.contains(origin))
        {
            return true;
        }
        String host;
        try
        {
            host = URI.create(origin).getHost();
        }
        catch (Exception e)
        {
            return false;
        }
        if (host == null || host.isEmpty())
        {
            return false;
        }
        return isLoopbackHost(host) || isWslBridgeHost(host);
    }

    private static boolean isLoopbackHost(String host)
    {
        if ("localhost".equalsIgnoreCase(host) || "[::1]".equals(host) || "::1".equals(host))
        {
            return true;
        }
        return host.startsWith("127.") && isIpv4(host);
    }

    /** 172.16.0.0/12 — the default Hyper-V vSwitch range used by WSL2. */
    private static boolean isWslBridgeHost(String host)
    {
        if (!host.startsWith("172.") || !isIpv4(host))
        {
            return false;
        }
        int second = Integer.parseInt(host.split("\\.")[1]);
        return second >= 16 && second <= 31;
    }

    private static boolean isIpv4(String host)
    {
        String[] octets = host.split("\\.");
        if (octets.length != 4)
        {
            return false;
        }
        try
        {
            for (String o : octets)
            {
                int v = Integer.parseInt(o);
                if (v < 0 || v > 255)
                {
                    return false;
                }
            }
            return true;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }
}
