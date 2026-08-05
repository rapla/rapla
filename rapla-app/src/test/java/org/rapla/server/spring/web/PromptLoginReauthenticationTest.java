package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swing SSO logout bug: {@code /oauth2/revoke} kills refresh tokens but NOT the
 * browser's rapla Spring Security session, so the next {@code /oauth2/authorize}
 * silently re-issued a code for the old user — logout never let you change user.
 * Spring Authorization Server does not implement OIDC {@code prompt=login}
 * re-authentication itself (it only errors on {@code prompt=none}), so rapla
 * must: an authenticated session hitting {@code /oauth2/authorize?prompt=login}
 * is logged out (session + remember-me) and redirected to the same authorize
 * URL without the login prompt, which then lands on the {@code /login} chooser.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class PromptLoginReauthenticationTest
{
    private static final String AUTHORIZE_QUERY = "response_type=code&client_id=rapla-client"
            + "&redirect_uri=http://127.0.0.1:54321/login/oauth2/code/rapla"
            + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
            + "&scope=openid&state=xyz";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PromptLoginReauthenticationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    private MockHttpSession loginSession() throws Exception
    {
        MvcResult login = mockMvc.perform(formLogin("/login").user("monty").password("burns"))
                .andExpect(redirectedUrl("/app/"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "form login must establish a session");
        return session;
    }

    @Test
    void silentSsoWithoutPromptStillIssuesCode() throws Exception
    {
        MockHttpSession session = loginSession();
        MvcResult result = mockMvc.perform(get("/oauth2/authorize?" + AUTHORIZE_QUERY)
                        .accept(MediaType.TEXT_HTML)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("http://127.0.0.1:54321/login/oauth2/code/rapla"),
                "silent SSO must still redirect to the client callback, got: " + location);
        assertTrue(location.contains("code="), "silent SSO must issue a code, got: " + location);
    }

    @Test
    void promptLoginOnAuthenticatedSessionForcesReauthentication() throws Exception
    {
        MockHttpSession session = loginSession();
        MvcResult result = mockMvc.perform(get("/oauth2/authorize?" + AUTHORIZE_QUERY + "&prompt=login")
                        .accept(MediaType.TEXT_HTML)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertFalse(location.contains("code="),
                "prompt=login must NOT silently issue a code for the old session, got: " + location);
        assertTrue(location.contains("/oauth2/authorize"),
                "prompt=login must bounce back into the authorize flow (minus the prompt), got: " + location);
        assertFalse(location.contains("prompt=login"),
                "the re-entry redirect must strip prompt=login (one-shot, no loop), got: " + location);
        assertTrue(session.isInvalid(), "prompt=login must invalidate the old session");
    }

    @Test
    void promptLoginUnauthenticatedIsStrippedBeforeRequestSave() throws Exception
    {
        // No session: prompt=login must be stripped via redirect BEFORE the request
        // cache saves the URL — otherwise the post-login replay of the saved request
        // carries prompt=login into an authenticated session and loops forever
        // between logout and /login.
        MvcResult result = mockMvc.perform(get("/oauth2/authorize?" + AUTHORIZE_QUERY + "&prompt=login")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.contains("/oauth2/authorize"),
                "unauthenticated prompt=login must redirect back into authorize without the prompt, got: " + location);
        assertFalse(location.contains("prompt=login"),
                "prompt=login must never survive into the saved request, got: " + location);
    }
}
