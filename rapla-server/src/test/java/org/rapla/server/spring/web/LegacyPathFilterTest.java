package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * PRD 109 Phase 1 — tier-1 coverage for the legacy context-path filter. Rapla 2.0
 * served the webapp under a servlet context path ({@code /wochenplan}); Rapla 3
 * serves at the root. The filter keeps the published calendar and index URLs
 * answering verbatim under the old prefix and 301-redirects everything else onto
 * the canonical path. No Spring context — pure Servlet-API mocks.
 */
class LegacyPathFilterTest
{
    private final LegacyPathFilter filter = new LegacyPathFilter("/wochenplan");

    private static MockHttpServletRequest get(String uri, String query)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        request.setQueryString(query);
        return request;
    }

    @Test
    void calendarIsRewrittenVerbatimWithoutRedirect() throws Exception
    {
        MockHttpServletRequest request = get("/wochenplan/rapla/calendar", "user=x&file=y");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "no redirect — the published URL must keep answering");
        assertNull(response.getRedirectedUrl());
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertEquals("/rapla/calendar", forwarded.getRequestURI());
        assertEquals("/rapla/calendar", forwarded.getServletPath());
        assertEquals("user=x&file=y", forwarded.getQueryString());
    }

    @Test
    void encryptedQueryStringIsPassedThroughUntouched() throws Exception
    {
        // UrlEncryptor returns "<blob>&salt=<salt>" — re-encoding it breaks decryption.
        String raw = "page=calendar&user=x&key=AbC%2Bd/e%3D&salt=Zz9";
        MockHttpServletRequest request = get("/wochenplan/rapla/calendar", raw);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(raw, ((HttpServletRequest) chain.getRequest()).getQueryString());
    }

    @Test
    void internalCalendarIsRewrittenSoSecurityMatchersStillSeeIt() throws Exception
    {
        MockHttpServletRequest request = get("/wochenplan/rapla/internal_calendar", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Rewritten, NOT forwarded: the same filter chain continues, so the Spring
        // Security chain downstream still applies the /rapla/internal_* auth rule.
        assertEquals("/rapla/internal_calendar", ((HttpServletRequest) chain.getRequest()).getRequestURI());
        assertEquals(200, response.getStatus());
    }

    @Test
    void indexPageIsRewrittenVerbatim() throws Exception
    {
        for (String uri : new String[] { "/wochenplan", "/wochenplan/", "/wochenplan/index", "/wochenplan/rapla/index" })
        {
            MockHttpServletRequest request = get(uri, null);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), uri + " must not redirect");
            String rewritten = ((HttpServletRequest) chain.getRequest()).getRequestURI();
            assertEquals(uri.endsWith("index") ? "/index" : "/", rewritten, uri);
        }
    }

    @Test
    void jnlpRedirectsToTheRootLauncherPath() throws Exception
    {
        MockHttpServletRequest request = get("/wochenplan/rapla/raplaclient.jnlp", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(301, response.getStatus());
        assertEquals("/raplaclient.jnlp", response.getRedirectedUrl());
        assertNull(chain.getRequest(), "redirect must short-circuit the chain");
    }

    @Test
    void everythingElseRedirectsToTheStrippedPathKeepingTheQuery() throws Exception
    {
        String[][] cases = {
                { "/wochenplan/rapla/ical", "user=x", "/rapla/ical?user=x" },
                { "/wochenplan/rapla/storage/refresh", null, "/rapla/storage/refresh" },
                { "/wochenplan/rapla/events", "start=2026-08-28", "/rapla/events?start=2026-08-28" },
                { "/wochenplan/app/", null, "/app/" },
                { "/wochenplan/calendar.css", null, "/calendar.css" },
        };
        for (String[] c : cases)
        {
            MockHttpServletRequest request = get(c[0], c[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(301, response.getStatus(), c[0]);
            assertEquals(c[2], response.getRedirectedUrl(), c[0]);
            assertNull(chain.getRequest(), c[0]);
        }
    }

    @Test
    void unprefixedRequestsPassThroughUnwrapped() throws Exception
    {
        MockHttpServletRequest request = get("/rapla/calendar", "user=x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest(), "canonical URLs must not be touched at all");
        assertEquals(200, response.getStatus());
    }

    @Test
    void prefixMustMatchAWholePathSegment() throws Exception
    {
        MockHttpServletRequest request = get("/wochenplanung/rapla/calendar", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest(), "/wochenplanung is not /wochenplan");
    }

    @Test
    void blankPrefixDisablesTheFilterEntirely() throws Exception
    {
        LegacyPathFilter disabled = new LegacyPathFilter("");
        MockHttpServletRequest request = get("/wochenplan/rapla/calendar", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disabled.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
    }
}
