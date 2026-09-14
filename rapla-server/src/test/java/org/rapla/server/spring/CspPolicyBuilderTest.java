package org.rapla.server.spring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 071 Phase 2: the CSP base policy is strict + deployment-independent, and
 * connect-src is extended only with the origins of configured IdP endpoints.
 */
class CspPolicyBuilderTest
{
    @Test
    void basePolicyIsStrict()
    {
        String p = CspPolicyBuilder.build(null);
        // The lowest-criticality resource directives (style-src/img-src/font-src) are
        // deliberately NOT in this policy: they can't lead to code execution (the real XSS
        // defense is script-src, owned + enforced by the autoCsp <meta>), so restricting them
        // is pure noise — and dropping style-src removes the runtime inline-<style>
        // false-positive that otherwise blocks ever flipping this header to enforce. With no
        // default-src fallback here, those resource types are simply unrestricted. The
        // remaining directives below stay (report-only).
        assertTrue(!p.contains("style-src"), p);
        assertTrue(!p.contains("img-src"), p);
        assertTrue(!p.contains("font-src"), p);
        assertTrue(p.contains("object-src 'none'"), p);
        // base-uri 'self' — the SPA's <base href="/app/"> needs it (walk finding 2026-06-19);
        // the /api + /rapla policies keep base-uri 'none' (no <base> there).
        assertTrue(p.contains("base-uri 'self'"), p);
        assertTrue(p.contains("frame-ancestors 'none'"), p);
        assertTrue(p.contains("frame-src 'none'"), p);
        assertTrue(p.contains("form-action 'self'"), p);
        assertTrue(p.contains("connect-src 'self'"), p);
        // script-src is owned by the Angular autoCsp <meta>, NOT this header
        assertTrue(!p.contains("script-src") && !p.contains("default-src"), p);
        // no wildcard, no inline/eval in the base
        assertTrue(!p.contains("'unsafe-inline'") && !p.contains("'unsafe-eval'") && !p.contains(" *"), p);
    }

    @Test
    void connectSrcGetsIdpOriginOnly()
    {
        String p = CspPolicyBuilder.build(List.of(
                "https://login.example.com/realms/dhbw",
                "https://login.example.com/realms/dhbw/protocol/openid-connect/token"));
        assertTrue(p.contains("connect-src 'self' https://login.example.com"), p);
        // path stripped, deduped to the single origin
        assertTrue(!p.contains("/realms/"), p);
    }

    @Test
    void originOfStripsPathAndHandlesGarbage()
    {
        assertTrue("https://h.example:8443".equals(CspPolicyBuilder.originOf("https://h.example:8443/a/b")));
        assertTrue("https://h.example".equals(CspPolicyBuilder.originOf("https://h.example/a")));
        assertNull(CspPolicyBuilder.originOf(null));
        assertNull(CspPolicyBuilder.originOf(""));
        assertNull(CspPolicyBuilder.originOf("not a url"));
    }
}
