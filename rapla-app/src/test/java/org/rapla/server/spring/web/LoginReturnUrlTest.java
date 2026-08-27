package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Login deep-link: after logging in, the browser must return to the URL that triggered
 * the login, not unconditionally to the SPA at {@code /app/}.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginReturnUrlTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginReturnUrlTest.class.getResourceAsStream("/testdefault.xml"))
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

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    /** The built-in path: the security entry point caches the request, the success handler resumes it. */
    @Test
    void graphiqlDeepLinkReturnsAfterFormLogin() throws Exception
    {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/graphiql/").accept(BROWSER_ACCEPT).session(session))
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/login").param("username", "monty").param("password", "burns")
                        .with(csrf()).session(session))
                .andExpect(redirectedUrl("http://localhost/graphiql/?continue"));
    }

    /**
     * A document URL reached with a session/remember-me credential that no longer resolves to a
     * rapla user passes the {@code authenticated()} gate, so the controller — not the entry point —
     * decides. It must hand the request back to Spring Security rather than build its own 302,
     * otherwise no request is cached and the login lands on /app/ with the deep link lost.
     */
    @Test
    void documentDeepLinkReturnsAfterFormLogin() throws Exception
    {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/api/documents/no-such-document").accept(BROWSER_ACCEPT)
                        .session(session).with(user("stale-remember-me")))
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/login").param("username", "monty").param("password", "burns")
                        .with(csrf()).session(session))
                .andExpect(redirectedUrl("http://localhost/api/documents/no-such-document?continue"));
    }
}
