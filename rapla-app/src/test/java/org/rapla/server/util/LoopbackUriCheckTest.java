package org.rapla.server.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackUriCheckTest
{
    // Same path allowlist as SameOriginUriCheck and WslBridgeUriCheck — all
    // three validators share the rapla.oauth.same-origin-callback-paths list
    // for path matching. Only host/port treatment differs between them.
    private static final List<String> ALLOWED_PATHS = List.of(
            "/login/oauth2/code/rapla",
            "/app/auth/callback");

    @Test
    void ipv4LoopbackAnyPortInAllowlistAccepted()
    {
        assertTrue(LoopbackUriCheck.isLoopbackRedirect(
                "http://127.0.0.1/app/auth/callback", ALLOWED_PATHS));
        assertTrue(LoopbackUriCheck.isLoopbackRedirect(
                "http://127.0.0.1:8051/app/auth/callback", ALLOWED_PATHS));
        assertTrue(LoopbackUriCheck.isLoopbackRedirect(
                "http://127.0.0.1:54321/login/oauth2/code/rapla", ALLOWED_PATHS));
    }

    @Test
    void ipv6LoopbackAnyPortInAllowlistAccepted()
    {
        assertTrue(LoopbackUriCheck.isLoopbackRedirect(
                "http://[::1]/app/auth/callback", ALLOWED_PATHS));
        assertTrue(LoopbackUriCheck.isLoopbackRedirect(
                "http://[::1]:4200/app/auth/callback", ALLOWED_PATHS));
    }

    @Test
    void loopbackPathNotInAllowlistRejected()
    {
        // Previously the loopback wildcard accepted any path. Now path must
        // be in the allowlist — consistent with the other two validators.
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://127.0.0.1/totally-not-registered", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://127.0.0.1:54321/admin/secret-callback", ALLOWED_PATHS));
    }

    @Test
    void localhostRejected()
    {
        // RFC 8252 §8.3: `localhost` is NOT a loopback alias here (DNS-hijack
        // risk). SameOriginUriCheck's port-relaxation rule handles `localhost`
        // cases when both sides of the origin comparison are loopback variants.
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://localhost/app/auth/callback", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://localhost:4200/app/auth/callback", ALLOWED_PATHS));
    }

    @Test
    void externalHostsRejected()
    {
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "https://rapla.example.com/app/auth/callback", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://192.168.1.5/app/auth/callback", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://10.0.0.1/app/auth/callback", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://172.20.0.5/app/auth/callback", ALLOWED_PATHS));
    }

    @Test
    void httpsLoopbackAccepted()
    {
        // Operators occasionally run loopback over HTTPS for dev TLS testing.
        // Loopback semantics (trust the local machine) don't change with scheme.
        assertTrue(LoopbackUriCheck.isLoopbackRedirect(
                "https://127.0.0.1/app/auth/callback", ALLOWED_PATHS));
    }

    @Test
    void emptyPathRejected()
    {
        // No path → can't be in the allowlist.
        assertFalse(LoopbackUriCheck.isLoopbackRedirect("http://127.0.0.1", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect("http://127.0.0.1/", ALLOWED_PATHS));
    }

    @Test
    void malformedInputsRejected()
    {
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(null, ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect("", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect("not a uri", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect("ftp://", ALLOWED_PATHS));
    }

    @Test
    void schemeRelativeOrPathOnlyRejected()
    {
        assertFalse(LoopbackUriCheck.isLoopbackRedirect("//127.0.0.1/app/auth/callback", ALLOWED_PATHS));
        assertFalse(LoopbackUriCheck.isLoopbackRedirect("/app/auth/callback", ALLOWED_PATHS));
    }

    @Test
    void emptyAllowlistRejectsEverything()
    {
        assertFalse(LoopbackUriCheck.isLoopbackRedirect(
                "http://127.0.0.1/app/auth/callback", List.of()));
    }
}
