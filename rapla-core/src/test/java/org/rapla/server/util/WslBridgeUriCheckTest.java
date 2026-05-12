package org.rapla.server.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WslBridgeUriCheckTest
{
    private static final List<String> REGISTERED = List.of(
            "http://127.0.0.1/login/oauth2/code/rapla",
            "http://localhost/auth/callback",
            "http://127.0.0.1/auth/callback");

    @Test
    void wslBridgeHostsInRangeAccepted()
    {
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.16.0.1:1/login/oauth2/code/rapla", REGISTERED));
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/login/oauth2/code/rapla", REGISTERED));
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.31.255.254:65535/login/oauth2/code/rapla", REGISTERED));
    }

    @Test
    void hostsOutsideRangeRejected()
    {
        // 172.15 and 172.32 are outside 172.16.0.0/12
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.15.0.1:54321/login/oauth2/code/rapla", REGISTERED));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.32.0.1:54321/login/oauth2/code/rapla", REGISTERED));
        // Other private ranges must not pass — only 172.16.0.0/12
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://192.168.1.5:54321/login/oauth2/code/rapla", REGISTERED));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://10.0.0.5:54321/login/oauth2/code/rapla", REGISTERED));
        // Public IPs
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://203.0.113.5:54321/login/oauth2/code/rapla", REGISTERED));
    }

    @Test
    void pathMustMatchRegisteredUri()
    {
        // Right host, wrong path
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/some/other/path", REGISTERED));
        // Right host, path that matches a different registered URI
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/auth/callback", REGISTERED));
    }

    @Test
    void malformedInputsRejected()
    {
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(null, REGISTERED));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect("not a url", REGISTERED));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect("http://172.18.x.21:54321/", REGISTERED));
    }

    @Test
    void worksWithSetCollection()
    {
        Set<String> asSet = Set.copyOf(REGISTERED);
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/auth/callback", asSet));
    }
}
