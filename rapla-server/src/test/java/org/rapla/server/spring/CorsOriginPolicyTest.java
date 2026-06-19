package org.rapla.server.spring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2 (security): the CORS policy must allow credentialed cross-origin requests
 * only from loopback + the WSL bridge (172.16/12) + explicitly configured
 * origins — never from an arbitrary remote origin like {@code evil.com}.
 */
class CorsOriginPolicyTest
{
    private static final List<String> NO_EXTRAS = List.of();

    @Test
    void loopbackOriginsAllowed()
    {
        assertTrue(CorsOriginPolicy.isAllowedOrigin("http://localhost:4200", NO_EXTRAS));
        assertTrue(CorsOriginPolicy.isAllowedOrigin("http://127.0.0.1:9999", NO_EXTRAS));
        assertTrue(CorsOriginPolicy.isAllowedOrigin("https://localhost:8443", NO_EXTRAS));
        assertTrue(CorsOriginPolicy.isAllowedOrigin("http://[::1]:4200", NO_EXTRAS));
    }

    @Test
    void wslBridgeOriginsAllowed()
    {
        assertTrue(CorsOriginPolicy.isAllowedOrigin("http://172.16.0.1:4200", NO_EXTRAS));
        assertTrue(CorsOriginPolicy.isAllowedOrigin("http://172.31.81.90:8051", NO_EXTRAS));
        assertTrue(CorsOriginPolicy.isAllowedOrigin("http://172.24.5.6:3000", NO_EXTRAS));
    }

    @Test
    void wslBridgeBoundariesRejected()
    {
        // 172.15.x and 172.32.x are just outside 172.16.0.0/12
        assertFalse(CorsOriginPolicy.isAllowedOrigin("http://172.15.0.1:4200", NO_EXTRAS));
        assertFalse(CorsOriginPolicy.isAllowedOrigin("http://172.32.0.1:4200", NO_EXTRAS));
    }

    @Test
    void otherPrivateRangesRejected()
    {
        // 10/8 and 192.168/16 are NOT needed (prod is same-origin) → attack surface only
        assertFalse(CorsOriginPolicy.isAllowedOrigin("http://10.0.0.5:4200", NO_EXTRAS));
        assertFalse(CorsOriginPolicy.isAllowedOrigin("http://192.168.1.50:4200", NO_EXTRAS));
    }

    @Test
    void remoteOriginsRejected()
    {
        assertFalse(CorsOriginPolicy.isAllowedOrigin("https://evil.com", NO_EXTRAS));
        assertFalse(CorsOriginPolicy.isAllowedOrigin("https://rapla.evil.com", NO_EXTRAS));
        assertFalse(CorsOriginPolicy.isAllowedOrigin("http://example.org:8051", NO_EXTRAS));
    }

    @Test
    void nullOrMalformedRejected()
    {
        assertFalse(CorsOriginPolicy.isAllowedOrigin(null, NO_EXTRAS));
        assertFalse(CorsOriginPolicy.isAllowedOrigin("", NO_EXTRAS));
        assertFalse(CorsOriginPolicy.isAllowedOrigin("not a url", NO_EXTRAS));
    }

    @Test
    void configuredExtraOriginAllowed()
    {
        Set<String> extras = Set.of("https://rapla.partner.example");
        assertTrue(CorsOriginPolicy.isAllowedOrigin("https://rapla.partner.example", extras));
        // still rejects others
        assertFalse(CorsOriginPolicy.isAllowedOrigin("https://evil.com", extras));
    }
}
