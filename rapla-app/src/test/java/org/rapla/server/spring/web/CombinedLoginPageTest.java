package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 072 Phase 1 — the ONE combined {@code /login} page. With Keycloak +
 * Google enabled server-side, the page must render an SSO link per
 * {@code enabledProviders()} (each pointing at
 * {@code /oauth2/authorization/<id>}) AND the inline username/password form,
 * because the new {@code rapla.oauth.web.password-login} flag defaults ON.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class CombinedLoginPageTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = CombinedLoginPageTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.oauth.external.keycloak.enabled", () -> "true");
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "https://kc.example.com");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "rapla-test");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> "rapla-app");
        registry.add("rapla.oauth.external.google.enabled", () -> "true");
        registry.add("rapla.oauth.external.google.client-id", () -> "test-google-client");
        registry.add("rapla.oauth.external.google.client-secret", () -> "test-google-secret");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void ssoButtonPrefixIsLocalized() throws Exception
    {
        // the conventional "Sign in with <name>" displayName gets its prefix
        // localized; the provider name part stays as configured
        mockMvc.perform(get("/login").header("Accept-Language", "de"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anmelden mit Keycloak")))
                .andExpect(content().string(containsString("Anmelden mit Google")))
                .andExpect(content().string(not(containsString("Sign in with"))));
    }

    @Test
    void loginPageShowsSsoLinksForEachEnabledProvider() throws Exception
    {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/keycloak")))
                .andExpect(content().string(containsString("/oauth2/authorization/google")))
                .andExpect(content().string(containsString("Sign in with Keycloak")))
                .andExpect(content().string(containsString("Sign in with Google")));
    }

    @Test
    void plainLoginPageAlwaysPromptsKeycloakButNotGoogle() throws Exception
    {
        // PRD 072 follow-up: Keycloak links ALWAYS carry ?prompt=login (re-prompt on
        // every login — no silent SSO that would defeat sign-out / account-switch).
        // Other providers (Google) keep silent SSO on an ordinary login.
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/keycloak?prompt=login")))
                .andExpect(content().string(containsString("/oauth2/authorization/google\"")))
                .andExpect(content().string(not(containsString("/oauth2/authorization/google?prompt=login"))));
    }

    @Test
    void postLogoutLoginPageSsoLinksAllCarryPromptLogin() throws Exception
    {
        // After an explicit logout (/login?logout) ALL providers re-prompt — Google
        // too — so the sign-out can't be silently undone for any of them.
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/keycloak?prompt=login")))
                .andExpect(content().string(containsString("/oauth2/authorization/google?prompt=login")));
    }

    @Test
    void loginPageShowsPasswordFormWhenFlagOnByDefault() throws Exception
    {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                // the inline username/password form posts to /login
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("action=\"/login\"")));
    }
}
