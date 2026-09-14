package org.rapla.server.spring;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 097 D6a — a rendered document page carries the sandbox policy, not the JSON-API one.
 * The sandbox (no {@code allow-same-origin}) is what makes admin-authored HTML safe to show to
 * other users: the page holds an opaque origin, so it cannot reach the reader's rapla session.
 */
class RaplaCspHeaderWriterTest
{
    private final RaplaCspHeaderWriter writer = new RaplaCspHeaderWriter("default-src 'self'");

    @Test
    void documentPagesAreSandboxedScriptlessAndOffline()
    {
        String policy = enforcedPolicyFor("/api/documents/leihschein");
        assertTrue(policy.contains("sandbox"), policy);
        assertTrue(policy.contains("script-src 'none'"), policy);
        assertTrue(policy.contains("connect-src 'none'"), policy);
        assertTrue(policy.contains("style-src 'unsafe-inline'"), policy);
        assertTrue(policy.contains("frame-ancestors 'none'"), policy);
    }

    @Test
    void theDocumentPolicyNeverGrantsSameOriginOrScripts()
    {
        String policy = enforcedPolicyFor("/api/documents/leihschein");
        assertTrue(!policy.contains("allow-same-origin"), policy);
        assertTrue(!policy.contains("script-src 'unsafe-inline'"), policy);
    }

    @Test
    void otherApiPathsKeepTheJsonApiPolicy()
    {
        assertEquals(CspPolicyBuilder.jsonApiPolicy(), enforcedPolicyFor("/api/graphql"));
    }

    // PRD 102 Phase 1 — the SPA's non-script policy is ENFORCED on /app (side-effect-free: it carries
    // no script-src/style-src, so Material inline styles + any inline bootstrap script are untouched).
    @Test
    void theAppIsEnforced()
    {
        MockHttpServletResponse response = write("/app/index.html");
        assertEquals("default-src 'self'", response.getHeader("Content-Security-Policy"));
        assertNull(response.getHeader("Content-Security-Policy-Report-Only"));
    }

    // /login (server-rendered, inline script) and the explorers (CDN-loaded) stay report-only until
    // their own enforce pass — enforcing /app must not flip them.
    @Test
    void theLoginPageStaysReportOnly()
    {
        MockHttpServletResponse response = write("/login");
        assertNull(response.getHeader("Content-Security-Policy"));
        assertEquals("default-src 'self'", response.getHeader("Content-Security-Policy-Report-Only"));
    }

    @Test
    void theExplorersStayReportOnly()
    {
        for (String uri : new String[]{"/graphiql/", "/swagger-ui/index.html"})
        {
            MockHttpServletResponse response = write(uri);
            assertNull(response.getHeader("Content-Security-Policy"), uri);
            assertEquals("default-src 'self'", response.getHeader("Content-Security-Policy-Report-Only"), uri);
        }
    }

    // PRD 097 D6c (2) — the controller marks a response as script-bearing (yml switch on AND the
    // document is not public); the writer then serves the scripted sandbox variant: scripts run,
    // still no same-origin, no forms (an auto-submitted credentialed form would be a write
    // primitive), no third-party script hosts.
    @Test
    void aScriptedDocumentResponseGetsTheScriptedSandboxVariant()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/leihschein");
        request.setAttribute(RaplaCspHeaderWriter.SCRIPTED_DOCUMENT, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.writeHeaders(request, response);
        String policy = response.getHeader("Content-Security-Policy");
        assertTrue(policy.contains("sandbox allow-scripts"), policy);
        assertTrue(policy.contains("script-src 'self' 'unsafe-inline'"), policy);
        assertTrue(policy.contains("form-action 'none'"), policy);
        assertTrue(policy.contains("connect-src 'none'"), policy);
        assertTrue(!policy.contains("allow-same-origin"), policy);
        assertTrue(!policy.contains("allow-forms"), policy);
        assertTrue(!policy.contains("allow-popups"), policy);
        assertTrue(!policy.contains("allow-top-navigation"), policy);
    }

    @Test
    void theScriptedMarkerIsIgnoredOutsideDocumentPages()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/graphql");
        request.setAttribute(RaplaCspHeaderWriter.SCRIPTED_DOCUMENT, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.writeHeaders(request, response);
        assertEquals(CspPolicyBuilder.jsonApiPolicy(), response.getHeader("Content-Security-Policy"));
    }

    private String enforcedPolicyFor(String uri)
    {
        return write(uri).getHeader("Content-Security-Policy");
    }

    private MockHttpServletResponse write(String uri)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.writeHeaders(request, response);
        return response;
    }
}
