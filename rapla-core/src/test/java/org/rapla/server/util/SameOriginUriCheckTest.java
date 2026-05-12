package org.rapla.server.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SameOriginUriCheckTest
{
    private static final List<String> REGISTERED = List.of(
            "http://127.0.0.1/login/oauth2/code/rapla",
            "http://localhost/auth/callback",
            "http://127.0.0.1/auth/callback");

    @Test
    void httpsProdRedirectAcceptedWhenRequestArrivedSameOrigin()
    {
        // Auth server received request as https://rapla.uni.de:443/oauth2/authorize.
        // Angular SPA registers /auth/callback path; redirect to the same origin
        // with that path must be accepted.
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/auth/callback",
                "https", "rapla.uni.de", 443, REGISTERED));
    }

    @Test
    void httpDevAtNonStandardPortAccepted()
    {
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "http://localhost:4200/auth/callback",
                "http", "localhost", 4200, REGISTERED));
    }

    @Test
    void differentSchemeRejected()
    {
        // Auth server reachable via HTTPS but redirect URI uses HTTP — reject.
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "http://rapla.uni.de/auth/callback",
                "https", "rapla.uni.de", 443, REGISTERED));
    }

    @Test
    void differentHostRejected()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://evil.example.com/auth/callback",
                "https", "rapla.uni.de", 443, REGISTERED));
    }

    @Test
    void differentPortRejected()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de:8443/auth/callback",
                "https", "rapla.uni.de", 443, REGISTERED));
    }

    @Test
    void pathMustMatchRegisteredUri()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/some/other/path",
                "https", "rapla.uni.de", 443, REGISTERED));
    }

    @Test
    void defaultPortsHandledForHttpsAndHttp()
    {
        // Redirect URI without explicit port should compare as default port.
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/auth/callback",
                "https", "rapla.uni.de", 443, REGISTERED));
        assertTrue(SameOriginUriCheck.isSameOriginRedirect(
                "http://rapla.uni.de/auth/callback",
                "http", "rapla.uni.de", 80, REGISTERED));
    }

    @Test
    void malformedInputsRejected()
    {
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                null, "https", "rapla.uni.de", 443, REGISTERED));
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/auth/callback", null, "rapla.uni.de", 443, REGISTERED));
        assertFalse(SameOriginUriCheck.isSameOriginRedirect(
                "https://rapla.uni.de/auth/callback", "https", null, 443, REGISTERED));
    }
}
