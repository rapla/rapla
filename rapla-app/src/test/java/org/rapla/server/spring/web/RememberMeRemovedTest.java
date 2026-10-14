package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.HtmlUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PRD 131 D13/D1 — Spring's persistent {@code rapla-remember-me} cookie is gone: the
 * "stay signed in" tick only records the {@code rapla-remember-choice} cookie, a stale
 * remember-me cookie from an older release authenticates nothing, and the logged-out login
 * page says the session ended on every device.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class RememberMeRemovedTest extends IsolatedDefaultDatasetTest
{
    private static final String AUTHORIZE_QUERY = "response_type=code&client_id=rapla-client"
            + "&redirect_uri=http://127.0.0.1:54321/login/oauth2/code/rapla"
            + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
            + "&scope=openid&state=xyz";

    @Autowired
    MockMvc mockMvc;

    private MvcResult tickedLogin() throws Exception
    {
        return mockMvc.perform(post("/login")
                        .param("username", "admin").param("password", "")
                        .param("remember-me", "on")
                        .with(csrf()).session(new MockHttpSession()))
                .andReturn();
    }

    @Test
    void tickedLoginIssuesNoRememberMeCookieButKeepsTheChoice() throws Exception
    {
        MvcResult login = tickedLogin();

        assertTrue(login.getResponse().getHeaders("Set-Cookie").stream()
                        .noneMatch(h -> h.startsWith("rapla-remember-me=")),
                "no rapla-remember-me cookie, was: " + login.getResponse().getHeaders("Set-Cookie"));
        assertNotNull(login.getResponse().getCookie("rapla-remember-choice"), "the tick is still recorded");
        assertNotNull(login.getResponse().getCookie("refresh_token"), "the refresh cookie carries the session");
    }

    @Test
    void staleRememberMeCookieAuthenticatesNothing() throws Exception
    {
        Cookie issuedByAnOlderRelease = tickedLogin().getResponse().getCookie("rapla-remember-me");
        Cookie stale = new Cookie("rapla-remember-me",
                issuedByAnOlderRelease != null ? issuedByAnOlderRelease.getValue() : "c3RhbGU6c3RhbGU");

        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize?" + AUTHORIZE_QUERY)
                        .accept(MediaType.TEXT_HTML).cookie(stale))
                .andReturn();
        String authorizeTo = authorize.getResponse().getHeader("Location");
        assertNotNull(authorizeTo, "unauthenticated authorize must redirect, status " + authorize.getResponse().getStatus());
        assertFalse(authorizeTo.contains("code="), "a remember-me cookie must not yield a code, was: " + authorizeTo);
        assertTrue(authorizeTo.endsWith("/login"), "authorize must be sent to the login page, was: " + authorizeTo);

        MvcResult explorer = mockMvc.perform(get("/graphiql/").accept(MediaType.TEXT_HTML).cookie(stale)).andReturn();
        String explorerTo = explorer.getResponse().getHeader("Location");
        assertTrue(explorerTo != null && explorerTo.endsWith("/login"),
                "the explorer pages must be sent to the login page, status " + explorer.getResponse().getStatus()
                        + " location " + explorerTo);
    }

    @Test
    void loggedOutLoginPageSaysAllDevices() throws Exception
    {
        assertEquals("Sie wurden auf allen Geräten abgemeldet.", logoutBanner("de"));
        assertEquals("You have been logged out on all devices.", logoutBanner("en"));
    }

    @Test
    void ordinaryLoginPageHasNoLogoutHint() throws Exception
    {
        String html = mockMvc.perform(get("/login?lang=de").accept(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();
        assertFalse(HtmlUtils.htmlUnescape(html).contains("abgemeldet"), html);
    }

    private String logoutBanner(String lang) throws Exception
    {
        String html = HtmlUtils.htmlUnescape(mockMvc.perform(get("/login?logout=&lang=" + lang).accept(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString());
        String open = "<p style=\"color:#080;\">";
        int start = html.indexOf(open);
        assertTrue(start >= 0, "logout banner missing, was: " + html);
        return html.substring(start + open.length(), html.indexOf("</p>", start));
    }
}
