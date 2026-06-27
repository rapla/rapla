package org.rapla.server.spring.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PRD 072 / 036 Phase 3 — tier-1 coverage for the per-provider legacy-callback
 * bridge. The filter 302-redirects {@code GET /app/auth/callback?…} (the only
 * redirect URI the legacy IdP whitelists) onto the target provider's conformant
 * {@code /login/oauth2/code/{registrationId}} endpoint, preserving the query
 * string, and passes every other path straight through. The target registrationId
 * is per-provider (constructor arg), not hardcoded. No Spring context — pure
 * Servlet-API mocks.
 */
class LegacyAppCallbackBridgeFilterTest
{
    private final LegacyAppCallbackBridgeFilter filter = new LegacyAppCallbackBridgeFilter("keycloak");

    @Test
    void legacyCallbackWithQueryRedirectsAndPreservesQuery() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/auth/callback");
        request.setQueryString("code=abc&state=xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(302, response.getStatus());
        assertEquals("/login/oauth2/code/keycloak?code=abc&state=xyz", response.getRedirectedUrl());
        // The chain must NOT continue — the request is short-circuited into the redirect.
        assertNull(chain.getRequest(), "filter chain must not be continued on a bridged callback");
    }

    @Test
    void legacyCallbackWithoutQueryRedirectsToBarePath() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/auth/callback");
        // No query string set.
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(302, response.getStatus());
        assertEquals("/login/oauth2/code/keycloak", response.getRedirectedUrl());
        assertNull(chain.getRequest(), "filter chain must not be continued on a bridged callback");
    }

    @Test
    void targetRegistrationIdIsPerProviderNotHardcoded() throws Exception
    {
        // Phase 3: a provider named something other than "keycloak" bridges to ITS id.
        LegacyAppCallbackBridgeFilter dhbwFilter = new LegacyAppCallbackBridgeFilter("dhbw");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/auth/callback");
        request.setQueryString("code=abc&state=xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        dhbwFilter.doFilter(request, response, chain);

        assertEquals(302, response.getStatus());
        assertEquals("/login/oauth2/code/dhbw?code=abc&state=xyz", response.getRedirectedUrl());
    }

    @Test
    void unrelatedPathPassesThrough() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/reservations");
        request.setQueryString("foo=bar");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // No redirect, chain continued to the wrapped request.
        assertNull(response.getRedirectedUrl(), "an unrelated path must not be redirected");
        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest(), "filter chain must be continued for an unrelated path");
    }
}
