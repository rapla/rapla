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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 036 regression: with Microsoft + Google enabled server-side,
 * {@code /api/auth/oauth/config} emits a three-entry {@code providers[]}
 * array, but the flat top-level fields STILL reflect the rapla embedded SAS —
 * so the Swing client's discovery probe sees today's URLs unchanged.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class OAuthConfigControllerExternalProvidersTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OAuthConfigControllerExternalProvidersTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.oauth.external.microsoft.enabled", () -> "true");
        registry.add("rapla.oauth.external.microsoft.tenant", () -> "test-tenant-guid");
        registry.add("rapla.oauth.external.microsoft.client-id", () -> "test-microsoft-client");
        registry.add("rapla.oauth.external.google.enabled", () -> "true");
        registry.add("rapla.oauth.external.google.client-id", () -> "test-google-client");
        // Stub secret so the BFF routing branch is exercised. Google's
        // "Web application" client type requires this in production.
        registry.add("rapla.oauth.external.google.client-secret", () -> "test-google-secret");
        registry.add("rapla.oauth.web.picker.mode", () -> "always");
        registry.add("rapla.oauth.web.picker.primary", () -> "microsoft");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void topLevelFieldsAreRaplaSasEvenWithExternalProvidersEnabled() throws Exception
    {
        // The flat fields are what the Swing client reads. They must reflect
        // rapla's embedded SAS regardless of which external providers are on.
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("rapla-client"))
                .andExpect(jsonPath("$.authorizeUrl").value(org.hamcrest.Matchers.endsWith("/oauth2/authorize")))
                .andExpect(jsonPath("$.tokenUrl").value(org.hamcrest.Matchers.endsWith("/oauth2/token")))
                .andExpect(jsonPath("$.authorizeUrl").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("microsoftonline"))))
                .andExpect(jsonPath("$.authorizeUrl").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("accounts.google"))));
    }

    @Test
    void providersArrayContainsAllThreeProviders() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(3))
                // Sorted by `order`: rapla(0) < microsoft(10) < google(20)
                .andExpect(jsonPath("$.providers[0].id").value("rapla"))
                .andExpect(jsonPath("$.providers[1].id").value("microsoft"))
                .andExpect(jsonPath("$.providers[2].id").value("google"));
    }

    @Test
    void microsoftEntryHasEntraUrlsAndOidcDefaults() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[1].id").value("microsoft"))
                .andExpect(jsonPath("$.providers[1].clientId").value("test-microsoft-client"))
                .andExpect(jsonPath("$.providers[1].issuer").value(
                        "https://login.microsoftonline.com/test-tenant-guid/v2.0"))
                .andExpect(jsonPath("$.providers[1].authorizeUrl").value(
                        "https://login.microsoftonline.com/test-tenant-guid/oauth2/v2.0/authorize"))
                // No client_secret configured in this test (mirrors Entra's
                // "Single-page application" platform — PKCE-only, no secret).
                // With no secret to add, the BFF is bypassed and the SPA POSTs
                // to Entra's token endpoint directly. Entra's SPA platform
                // requires this (AADSTS9002327 — cross-origin only). See
                // OAuthConfigController.buildProviders().
                .andExpect(jsonPath("$.providers[1].tokenUrl").value(
                        "https://login.microsoftonline.com/test-tenant-guid/oauth2/v2.0/token"))
                .andExpect(jsonPath("$.providers[1].jwksUrl").value(
                        "https://login.microsoftonline.com/test-tenant-guid/discovery/v2.0/keys"))
                .andExpect(jsonPath("$.providers[1].endSessionUrl").value(
                        "https://login.microsoftonline.com/test-tenant-guid/oauth2/v2.0/logout"))
                .andExpect(jsonPath("$.providers[1].webPickerVisible").value(true))
                .andExpect(jsonPath("$.providers[1].displayName").value("Sign in with Microsoft"))
                .andExpect(jsonPath("$.providers[1].icon").value("microsoft"));
    }

    @Test
    void googleEntryHasGoogleUrlsAndExtraAuthorizeParams() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[2].id").value("google"))
                .andExpect(jsonPath("$.providers[2].clientId").value("test-google-client"))
                .andExpect(jsonPath("$.providers[2].issuer").value("https://accounts.google.com"))
                .andExpect(jsonPath("$.providers[2].authorizeUrl").value(
                        "https://accounts.google.com/o/oauth2/v2/auth"))
                .andExpect(jsonPath("$.providers[2].tokenUrl").value(
                        org.hamcrest.Matchers.endsWith("/api/auth/oauth/exchange/google")))
                .andExpect(jsonPath("$.providers[2].jwksUrl").value(
                        "https://www.googleapis.com/oauth2/v3/certs"))
                // Google needs access_type=offline + prompt=consent for refresh tokens
                .andExpect(jsonPath("$.providers[2].extraAuthorizeParams.access_type").value("offline"))
                .andExpect(jsonPath("$.providers[2].extraAuthorizeParams.prompt").value("consent"));
    }

    @Test
    void discoveryNeverEmitsClientSecret() throws Exception
    {
        // BFF design: client_secret stays server-side. If a future change
        // accidentally puts it on the wire, this test should catch it.
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[*].clientSecret").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void pickerConfigIsReflected() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picker.mode").value("always"))
                .andExpect(jsonPath("$.picker.primary").value("microsoft"));
    }
}
