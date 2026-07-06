package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpSession;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * B3 — the change-password nag: a Spring {@code /login} (browser/SPA) login by a user
 * whose password is empty is redirected to {@code /change-password}; a user with a real
 * password is not.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ChangePasswordNagFlowTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ChangePasswordNagFlowTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    CachableStorageOperator operator;

    @Test
    void changePasswordPageRenders() throws Exception
    {
        mockMvc.perform(get("/change-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Set a password")));
    }

    @Test
    void emptyPasswordLoginIsRedirectedToChangePassword() throws Exception
    {
        // give homer an empty password (the "unset" state)
        User homer = operator.getUser("homer");
        operator.changePassword(homer, "duffs".toCharArray(), new char[0]);

        mockMvc.perform(formLogin("/login").user("homer").password(""))
                .andExpect(redirectedUrl("/change-password"));
    }

    @Test
    void realPasswordLoginIsNotNagged() throws Exception
    {
        // monty keeps a real password → straight through to the app, never the nag page
        mockMvc.perform(formLogin("/login").user("monty").password("burns"))
                .andExpect(redirectedUrl("/app/"));
    }

    /**
     * Swing "Sign in with browser" bug: the OAuth {@code /oauth2/authorize} request is
     * saved before {@code /login}; an empty-password user is bounced to the nag page.
     * Skipping (or setting) the password must resume the SAVED OAuth flow so the browser
     * redirects to the Swing loopback callback — NOT to the SPA at {@code /app/}.
     */
    @Test
    void changePasswordSkipHonoursSavedOAuthRequest() throws Exception
    {
        User homer = operator.getUser("homer");
        operator.changePassword(homer, "duffs".toCharArray(), new char[0]);

        // 1. Swing opens the authorize URL unauthenticated → the ExceptionTranslationFilter
        //    saves it in the request cache and bounces to /login. Reproduce that saved state.
        org.springframework.mock.web.MockHttpServletRequest authorizeReq =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/oauth2/authorize");
        authorizeReq.setServerName("localhost");
        authorizeReq.setServerPort(8051);
        authorizeReq.setQueryString("response_type=code&client_id=rapla-client"
                + "&redirect_uri=http://127.0.0.1:54321/callback"
                + "&code_challenge=abc&code_challenge_method=S256&scope=openid");
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        authorizeReq.setSession(session);
        new org.springframework.security.web.savedrequest.HttpSessionRequestCache()
                .saveRequest(authorizeReq, new org.springframework.mock.web.MockHttpServletResponse());

        // 2. Form login with empty password on that session → nagged to /change-password
        mockMvc.perform(post("/login").param("username", "homer").param("password", "").with(csrf())
                        .session(session))
                .andExpect(redirectedUrl("/change-password"));

        // 3. Skip the nag → must resume the saved /oauth2/authorize flow, not land on /app/
        mockMvc.perform(post("/change-password").param("skip", "1").with(csrf())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/oauth2/authorize")));
    }
}
