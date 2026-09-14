package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 072 Phase 1 — the entry-point CONTRACT with {@code oauth2Login()} active:
 * JSON/Bearer {@code /api/**} → 401, HTML navigation → 302 {@code /login}.
 *
 * <p>Honest scope (review SF2): this asserts the observable contract, not that the
 * explicitly-pinned entry point is the <i>only</i> thing producing it. In the
 * default chain composition the resource server's own
 * {@code BearerTokenAuthenticationEntryPoint} already yields 401 for {@code /api/**},
 * so the pin is defensive/redundant today (it makes the 401 explicit + order-independent
 * for future refactors that might drop the resource server). The HTML→302 leg DOES
 * depend on the pin (without {@code oauth2Login}, an unauthenticated HTML nav would 401).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginEntryPointContractTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginEntryPointContractTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        // Enable Keycloak so oauth2Login() is ACTIVE — the wired state under test.
        // (Review SF2: with the resource server present, /api/** already 401s via
        // the Bearer entry point; the pin's load-bearing leg is the HTML→302.)
        registry.add("rapla.oauth.external.keycloak.enabled", () -> "true");
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "https://kc.example.com");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "rapla-test");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> "rapla-app");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void unauthenticatedApiWithJsonAcceptGets401() throws Exception
    {
        mockMvc.perform(get("/api/auth/api-keys")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedHtmlNavigationRedirectsToLogin() throws Exception
    {
        // A real browser navigation: Accept includes text/html, no Bearer.
        mockMvc.perform(get("/some-protected-page")
                        .accept(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML, MediaType.ALL))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")));
    }
}
