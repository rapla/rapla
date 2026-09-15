package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PRD 118 D8-11 — an {@code access_token} cookie that no longer verifies (signed by a key that was
 * rotated away, e.g. by the demo's nightly reset) must not lock the browser out: it is treated as
 * anonymous and expired, so {@code /login} and other permitAll pages answer, protected HTML pages
 * redirect to {@code /login}, and {@code /api} answers 401 JSON as for any anonymous call.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Tag("e2e")
class InvalidAccessCookieTest extends IsolatedDefaultDatasetTest
{
    @Autowired MockMvc mockMvc;

    private String validToken() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "admin", "");
    }

    /**
     * Same token with one signature character changed — what a cookie signed by a rotated key looks like to the
     * server. A character in the MIDDLE of the base64url signature: the last one carries padding bits, so
     * flipping it can leave the signature bytes (and its validity) unchanged.
     */
    private String foreignSignature() throws Exception
    {
        String token = validToken();
        int i = token.length() - 20;
        char c = token.charAt(i);
        return token.substring(0, i) + (c == 'A' ? 'Q' : 'A') + token.substring(i + 1);
    }

    private MockHttpServletResponse html(String path, Cookie cookie) throws Exception
    {
        return mockMvc.perform(get(path).accept(MediaType.TEXT_HTML).cookie(cookie)).andReturn().getResponse();
    }

    @Test
    void theLoginPageAnswersDespiteAForeignCookie() throws Exception
    {
        MockHttpServletResponse response = html("/login", new Cookie("access_token", foreignSignature()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void aProtectedHtmlPageRedirectsToLoginInsteadOf401() throws Exception
    {
        MockHttpServletResponse response = html("/graphiql/index.html", new Cookie("access_token", foreignSignature()));
        assertEquals(302, response.getStatus());
        assertNotNull(response.getRedirectedUrl());
        assertTrue(response.getRedirectedUrl().endsWith("/login"), response.getRedirectedUrl());
    }

    @Test
    void theApiAnswers401JsonAsForAnyAnonymousCall() throws Exception
    {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/me").accept(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("access_token", foreignSignature()))).andReturn().getResponse();
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\""), response.getContentAsString());
    }

    @Test
    void theForeignCookieIsExpiredButTheRefreshCookieIsLeftAlone() throws Exception
    {
        MockHttpServletResponse response = html("/login", new Cookie("access_token", foreignSignature()));
        List<String> cookies = response.getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("access_token=") && c.contains("Max-Age=0")), cookies.toString());
        assertTrue(cookies.stream().noneMatch(c -> c.startsWith("refresh_token=")), cookies.toString());
    }

    @Test
    void aMalformedCookieIsTreatedTheSameWay() throws Exception
    {
        assertEquals(200, html("/login", new Cookie("access_token", "not-a-jwt")).getStatus());
    }

    @Test
    void aValidCookieStillAuthorizesTheApi() throws Exception
    {
        int status = mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", validToken())))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    /**
     * The path every demo visitor takes after the nightly reset: the SPA refreshes with a stale access cookie. The
     * filter's expiring Set-Cookie comes first, the fresh token after it — the LAST access_token header must carry
     * the new token, and that token must authorize the API.
     */
    @Test
    void refreshWithAStaleAccessCookieEndsWithTheFreshToken() throws Exception
    {
        OAuthTestSupport.TokenPair pair = OAuthTestSupport.loginAsWithRefresh(mockMvc, "admin", "");
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/session/refresh").with(csrf())
                .cookie(new Cookie("access_token", foreignSignature()), new Cookie("refresh_token", pair.refreshToken())))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        List<String> access = response.getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("access_token=")).toList();
        assertTrue(access.size() >= 1, access.toString());
        String last = access.get(access.size() - 1);
        String value = last.substring("access_token=".length(), last.indexOf(';'));
        assertTrue(!value.isEmpty() && !last.contains("Max-Age=0"), last);
        int me = mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", value))).andReturn().getResponse().getStatus();
        assertEquals(200, me);
    }

    /** Sibling audit: the other auth cookies never promote to a bearer — a bogus value must not block /login either. */
    @Test
    void bogusRefreshAndRememberMeCookiesDoNotBlockTheLoginPage() throws Exception
    {
        assertEquals(200, html("/login", new Cookie("refresh_token", "bogus")).getStatus());
        assertEquals(200, html("/login", new Cookie("rapla-remember-me", "Ym9ndXM6Ym9ndXM")).getStatus());
    }
}
