package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaServerProperties;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

/**
 * PRD 118 D8-3 — the allowlist decision on the path the container hands the filter (Tomcat decodes
 * and normalizes the servlet path; the raw request URI keeps what the client sent).
 */
class DemoApiAllowlistFilterTest
{
    /** D8-3a — the demo's lists, as application-demo.yml carries them; the guarded paths are the defaults. */
    static RaplaServerProperties.ApiAllowlist demoLists()
    {
        RaplaServerProperties.ApiAllowlist lists = new RaplaServerProperties.ApiAllowlist();
        lists.setEnabled(true);
        lists.setOpenPaths(List.of("/api/auth/me", "/api/graphql", "/api/graphql/schema", "/api/storage/change/name",
                "/api/storage/profile/capabilities"));
        lists.setOpenPrefixes(List.of("/api/auth/session", "/api/auth/api-keys", "/api/auth/impersonate", "/api/documents",
                "/api/favorites", "/api/recents", "/api/users"));
        return lists;
    }

    private static DemoApiAllowlistFilter demoFilter()
    {
        return new DemoApiAllowlistFilter(demoLists());
    }

    private static boolean passes(String servletPath, String rawUri) throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", rawUri);
        request.setServletPath(servletPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        demoFilter().doFilter(request, response, chain);
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
        demoFilter().doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        MockFilterChain closedChain = new MockFilterChain();
        MockHttpServletRequest closed = new MockHttpServletRequest("GET", "/api/storage/dispatch");
        closed.setServletPath("/api/storage/dispatch");
        demoFilter().doFilter(closed, new MockHttpServletResponse(), closedChain);
        assertNull(closedChain.getRequest());
    }

    @Test
    void offAndNothingOpenByDefault()
    {
        RaplaServerProperties.ApiAllowlist defaults = new RaplaServerProperties.ApiAllowlist();
        assertEquals(false, defaults.isEnabled());
        assertEquals(List.of(), defaults.getOpenPaths());
        assertEquals(List.of(), defaults.getOpenPrefixes());
    }

    @Test
    void withAnEmptyOpenListEverythingGuardedIsClosed() throws Exception
    {
        RaplaServerProperties.ApiAllowlist lists = new RaplaServerProperties.ApiAllowlist();
        lists.setEnabled(true);
        DemoApiAllowlistFilter.validate(lists);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setServletPath("/api/auth/me");
        MockFilterChain chain = new MockFilterChain();
        new DemoApiAllowlistFilter(lists).doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(chain.getRequest());
    }

    @Test
    void invalidOpenEntriesAreRejectedNamingThePropertyAndTheEntry()
    {
        for (String entry : new String[] { "", "/", "/api", "/api/", "/api/*", "/api/documents/*", "*", "/api/documents/..",
                "/api/documents;x", "/api//documents", "/api/%2e%2e", "/app/", "api/documents" })
        {
            RaplaServerProperties.ApiAllowlist prefixes = demoLists();
            prefixes.setOpenPrefixes(List.of("/api/users", entry));
            IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> DemoApiAllowlistFilter.validate(prefixes), entry);
            assertTrue(rejected.getMessage().contains("rapla.api-allowlist.open-prefixes") && rejected.getMessage().contains("\"" + entry + "\""),
                    rejected.getMessage());

            RaplaServerProperties.ApiAllowlist paths = demoLists();
            paths.setOpenPaths(List.of(entry));
            IllegalStateException rejectedPath = assertThrows(IllegalStateException.class, () -> DemoApiAllowlistFilter.validate(paths), entry);
            assertTrue(rejectedPath.getMessage().contains("rapla.api-allowlist.open-paths"), rejectedPath.getMessage());
        }
    }

    @Test
    void theDemoListsAndTheGuardedNonApiPathsAreValid()
    {
        DemoApiAllowlistFilter.validate(demoLists());
        RaplaServerProperties.ApiAllowlist swingOpen = demoLists();
        swingOpen.setOpenPaths(List.of("/raplaclient.jnlp"));
        swingOpen.setOpenPrefixes(List.of("/webclient"));
        DemoApiAllowlistFilter.validate(swingOpen);
    }
}
