package org.rapla.server.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SameOriginUriCheckTest
{
    // The allowlist is now PATHS, not URIs. SameOriginUriCheck reads
    // rapla.oauth.same-origin-callback-paths directly; previously it extracted
    // paths from URIs in spring...redirect-uris (the double-duty pattern).
    private static final List<String> ALLOWED_PATHS = List.of(
            "/login/oauth2/code/rapla",
            "/app/auth/callback");

    @Test
    void httpsProdRedirectAcceptedWhenRequestArrivedSameOrigin()
    {
        // Auth server received request as https://rapla.uni.de:443/oauth2/authorize.
        // Angular SPA registers /app/auth/callback path; redirect to the same origin
        // with that path must be accepted.
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/app/auth/callback",
                "https", "rapla.uni.de", 443, ALLOWED_PATHS));
    }

    @Test
    void httpDevAtNonStandardPortAccepted()
    {
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "http://localhost:4200/app/auth/callback",
                "http", "localhost", 4200, ALLOWED_PATHS));
    }

    @Test
    void differentSchemeRejected()
    {
        // Auth server reachable via HTTPS but redirect URI uses HTTP — reject.
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "http://rapla.uni.de/app/auth/callback",
                "https", "rapla.uni.de", 443, ALLOWED_PATHS));
    }

    @Test
    void differentHostRejected()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://evil.example.com/app/auth/callback",
                "https", "rapla.uni.de", 443, ALLOWED_PATHS));
    }

    @Test
    void differentPortRejected()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de:8443/app/auth/callback",
                "https", "rapla.uni.de", 443, ALLOWED_PATHS));
    }

    @Test
    void pathMustBeInAllowlist()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/some/other/path",
                "https", "rapla.uni.de", 443, ALLOWED_PATHS));
    }

    @Test
    void defaultPortsHandledForHttpsAndHttp()
    {
        // Redirect URI without explicit port should compare as default port.
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/app/auth/callback",
                "https", "rapla.uni.de", 443, ALLOWED_PATHS));
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "http://rapla.uni.de/app/auth/callback",
                "http", "rapla.uni.de", 80, ALLOWED_PATHS));
    }

    @Test
    void malformedInputsRejected()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                null, "https", "rapla.uni.de", 443, ALLOWED_PATHS));
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/app/auth/callback", null, "rapla.uni.de", 443, ALLOWED_PATHS));
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/app/auth/callback", "https", null, 443, ALLOWED_PATHS));
    }

    @Test
    void emptyAllowlistRejectsEverything()
    {
        // A misconfigured deployment (no callback paths configured) should
        // never accept a same-origin redirect.
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/app/auth/callback",
                "https", "rapla.uni.de", 443, List.of()));
    }

    // The next three tests cover the loopback port-relaxation rule (added so the
    // dev SPA at localhost:4200 can use OAuth against the AS at localhost:8051
    // without an explicit YAML registration). When BOTH sides are loopback
    // hostnames (127.0.0.1, localhost, [::1]) the port mismatch is allowed;
    // path-allowlist still gates everything.

    @Test
    void loopbackToLoopbackAcrossPortsAccepted()
    {
        // SPA at :4200 calls AS at :8051, registers /app/auth/callback on :4200.
        // Both sides are `localhost` → port mismatch tolerated, path-allowlist
        // gates.
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "http://localhost:4200/app/auth/callback",
                "http", "localhost", 8051, ALLOWED_PATHS));
    }

    @Test
    void loopbackVariantsCrossPortAccepted()
    {
        // Request arrives via 127.0.0.1:8051; redirect URI uses `localhost:4200`.
        // Both hosts are loopback variants → still accepted, even though
        // 127.0.0.1 != localhost as literal strings.
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "http://localhost:4200/app/auth/callback",
                "http", "127.0.0.1", 8051, ALLOWED_PATHS));
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "http://127.0.0.1:4200/app/auth/callback",
                "http", "localhost", 8051, ALLOWED_PATHS));
    }

    @Test
    void loopbackRedirectFromProductionOriginRejected()
    {
        // Negative: the request came in on https://prod.example.com but the
        // redirect URI is localhost. One side is loopback, the other is not —
        // STRICT port (and host) match applies → reject. Prevents a remote
        // attacker from tricking the AS into emitting a code redirected to
        // localhost (which the attacker can't reach anyway, but enforcing the
        // rule keeps the trust boundary clean).
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "http://localhost:4200/app/auth/callback",
                "https", "prod.example.com", 443, ALLOWED_PATHS));
    }

    @Test
    void portMismatchOnProductionStillRejected()
    {
        // The port-relaxation rule must NOT extend to non-loopback hosts.
        // A request on https://rapla.uni.de:443 with a redirect to
        // https://rapla.uni.de:8443 is still rejected.
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de:8443/app/auth/callback",
                "https", "rapla.uni.de", 443, ALLOWED_PATHS));
    }
}
