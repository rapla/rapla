package org.rapla.server.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WslBridgeUriCheckTest
{
    // Path-only allowlist (matches the new rapla.oauth.same-origin-callback-paths
    // shape — same source as SameOriginUriCheck).
    private static final List<String> ALLOWED_PATHS = List.of(
            "/login/oauth2/code/rapla",
            "/app/auth/callback");

    @Test
    void wslBridgeHostsInRangeAccepted()
    {
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.16.0.1:1/login/oauth2/code/rapla", ALLOWED_PATHS));
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/login/oauth2/code/rapla", ALLOWED_PATHS));
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.31.255.254:65535/login/oauth2/code/rapla", ALLOWED_PATHS));
    }

    @Test
    void hostsOutsideRangeRejected()
    {
        // 172.15 and 172.32 are outside 172.16.0.0/12
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.15.0.1:54321/login/oauth2/code/rapla", ALLOWED_PATHS));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.32.0.1:54321/login/oauth2/code/rapla", ALLOWED_PATHS));
        // Other private ranges must not pass — only 172.16.0.0/12
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://192.168.1.5:54321/login/oauth2/code/rapla", ALLOWED_PATHS));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://10.0.0.5:54321/login/oauth2/code/rapla", ALLOWED_PATHS));
        // Public IPs
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://203.0.113.5:54321/login/oauth2/code/rapla", ALLOWED_PATHS));
    }

    @Test
    void pathMustBeInAllowlist()
    {
        // Right host, wrong path
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/some/other/path", ALLOWED_PATHS));
        // Right host, path that's in the allowlist
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/app/auth/callback", ALLOWED_PATHS));
    }

    @Test
    void malformedInputsRejected()
    {
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(null, ALLOWED_PATHS));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect("not a url", ALLOWED_PATHS));
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect("http://172.18.x.21:54321/", ALLOWED_PATHS));
    }

    @Test
    void worksWithSetCollection()
    {
        Set<String> asSet = Set.copyOf(ALLOWED_PATHS);
        assertTrue(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/app/auth/callback", asSet));
    }

    @Test
    void emptyAllowlistRejectsEverything()
    {
        assertFalse(WslBridgeUriCheck.isWslBridgeRedirect(
                "http://172.18.115.21:54321/app/auth/callback", List.of()));
    }
}
