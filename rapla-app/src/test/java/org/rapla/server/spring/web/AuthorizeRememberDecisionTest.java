package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 131 R4/D6 — the stay-signed-in choice made on {@code /login} reaches Swing with the login:
 * the authorization code redirect to the loopback listener carries {@code remember=1|0}, read from
 * the choice cookie the login page set.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class AuthorizeRememberDecisionTest
{
    private static final String QUERY = "response_type=code&client_id=rapla-client"
            + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
            + "&scope=openid&state=xyz&redirect_uri=";
    private static final String LOOPBACK = "http://127.0.0.1:54321/login/oauth2/code/rapla";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AuthorizeRememberDecisionTest.class.getResourceAsStream("/testdefault.xml"))
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
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String authorize(MockHttpSession session, Cookie choice, String redirectUri) throws Exception
    {
        var request = get("/oauth2/authorize?" + QUERY + redirectUri).accept(MediaType.TEXT_HTML).session(session);
        if (choice != null) request = request.cookie(choice);
        MvcResult result = mockMvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        return location;
    }

    @Test
    void tickedChoiceReachesTheLoopbackRedirect() throws Exception
    {
        String location = authorize(loginSession(), new Cookie("rapla-remember-choice", "1"), LOOPBACK);
        assertTrue(location.startsWith(LOOPBACK + "?code="), location);
        assertTrue(location.contains("&state=xyz"), "state stays: " + location);
        assertTrue(location.contains("&remember=1"), location);
    }

    @Test
    void noChoiceReachesTheLoopbackRedirectAsZero() throws Exception
    {
        String location = authorize(loginSession(), null, LOOPBACK);
        assertTrue(location.contains("&remember=0"), location);
        assertTrue(location.contains("code="), location);
    }

    @Test
    void foreignCookieValueCountsAsNoChoice() throws Exception
    {
        String location = authorize(loginSession(), new Cookie("rapla-remember-choice", "yes"), LOOPBACK);
        assertTrue(location.contains("&remember=0"), location);
    }
}
