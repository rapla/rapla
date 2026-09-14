package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PRD 118 D8-3 — the allowlist decision on the path the container hands the filter (Tomcat decodes
 * and normalizes the servlet path; the raw request URI keeps what the client sent).
 */
class DemoApiAllowlistFilterTest
{
    private static boolean passes(String servletPath, String rawUri) throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", rawUri);
        request.setServletPath(servletPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new DemoApiAllowlistFilter().doFilter(request, response, chain);
        if (chain.getRequest() == null)
        {
            assertEquals(404, response.getStatus());
            assertEquals("", response.getContentAsString());
            return false;
        }
        return true;
    }

    @Test
    void allowlistedPathsPass() throws Exception
    {
        for (String path : new String[] { "/api/auth/me", "/api/graphql", "/api/graphql/schema", "/api/documents", "/api/documents/x/csv",
                "/api/users", "/api/favorites/F", "/api/auth/session/refresh", "/api/storage/change/name", "/api/storage/profile/capabilities" })
        {
            assertEquals(true, passes(path, path), path);
        }
    }

    @Test
    void everythingElseUnderTheGuardedPathsIsClosed() throws Exception
    {
        for (String path : new String[] { "/api", "/api/", "/api/storage/dispatch", "/api/storage/change/password", "/api/documentsx",
                "/api/auth/meta", "/api/graphqlx", "/api/plugins/x/enabled", "/raplaclient", "/raplaclient.jnlp", "/webclient/rapla.jar" })
        {
            assertEquals(false, passes(path, path), path);
        }
    }

    @Test
    void pathsOutsideTheGuardPass() throws Exception
    {
        for (String path : new String[] { "/app/", "/login", "/oauth2/token", "/graphiql/", "/rapla/calendar", "/rapla/ical", "/apix" })
        {
            assertEquals(true, passes(path, path), path);
        }
    }

    @Test
    void anAllowlistedPrefixWithTraversalOrEncodingInTheRawUriIsClosed() throws Exception
    {
        assertEquals(false, passes("/api/storage/dispatch", "/api/documents/..;/storage/dispatch"));
        assertEquals(false, passes("/api/storage/dispatch", "/api/documents/%2e%2e/storage/dispatch"));
        assertEquals(false, passes("/api/documents/x", "/api/documents;jsessionid=1/x"));
        assertEquals(false, passes("/api/documents/a/b", "/api/documents/a%2Fb"));
        assertEquals(false, passes("/api/documents/x", "/api//documents/x"));
    }

    @Test
    void theFilterForwardsAllowedRequestsUnchanged() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setServletPath("/api/auth/me");
        MockFilterChain chain = new MockFilterChain();
        new DemoApiAllowlistFilter().doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        MockFilterChain closedChain = new MockFilterChain();
        MockHttpServletRequest closed = new MockHttpServletRequest("GET", "/api/storage/dispatch");
        closed.setServletPath("/api/storage/dispatch");
        new DemoApiAllowlistFilter().doFilter(closed, new MockHttpServletResponse(), closedChain);
        assertNull(closedChain.getRequest());
    }
}
