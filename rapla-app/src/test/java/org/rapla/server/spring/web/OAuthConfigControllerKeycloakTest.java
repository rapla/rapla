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
 * PRD 036 Phase 2.1 — with Keycloak enabled server-side,
 * {@code /api/auth/oauth/config} emits a {@code rapla + keycloak}
 * {@code providers[]} array whose Keycloak entry carries OIDC URLs derived
 * from {@code base-url + realm}. The flat top-level fields stay rapla SAS.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class OAuthConfigControllerKeycloakTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OAuthConfigControllerKeycloakTest.class.getResourceAsStream("/testdefault.xml"))
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
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "http://localhost:8080");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "rapla");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> "rapla-app");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void topLevelFieldsStayRaplaSas() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("rapla-client"))
                .andExpect(jsonPath("$.authorizeUrl").value(org.hamcrest.Matchers.endsWith("/oauth2/authorize")))
                .andExpect(jsonPath("$.authorizeUrl").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/realms/"))));
    }

    @Test
    void providersArrayContainsRaplaAndKeycloak() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(2))
                // Sorted by `order`: rapla(0) < keycloak(15)
                .andExpect(jsonPath("$.providers[0].id").value("rapla"))
                .andExpect(jsonPath("$.providers[1].id").value("keycloak"));
    }

    @Test
    void keycloakEntryHasUrlsDerivedFromBaseUrlAndRealm() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[1].id").value("keycloak"))
                .andExpect(jsonPath("$.providers[1].clientId").value("rapla-app"))
                .andExpect(jsonPath("$.providers[1].issuer").value(
                        "http://localhost:8080/realms/rapla"))
                .andExpect(jsonPath("$.providers[1].authorizeUrl").value(
                        "http://localhost:8080/realms/rapla/protocol/openid-connect/auth"))
                // Public Keycloak client — no secret — so the SPA POSTs to the
                // IdP token endpoint directly (no BFF /api/auth/oauth/exchange).
                .andExpect(jsonPath("$.providers[1].tokenUrl").value(
                        "http://localhost:8080/realms/rapla/protocol/openid-connect/token"))
                .andExpect(jsonPath("$.providers[1].jwksUrl").value(
                        "http://localhost:8080/realms/rapla/protocol/openid-connect/certs"))
                .andExpect(jsonPath("$.providers[1].endSessionUrl").value(
                        "http://localhost:8080/realms/rapla/protocol/openid-connect/logout"))
                .andExpect(jsonPath("$.providers[1].webPickerVisible").value(true))
                .andExpect(jsonPath("$.providers[1].displayName").value("Sign in with Keycloak"))
                .andExpect(jsonPath("$.providers[1].icon").value("keycloak"));
    }

    @Test
    void discoveryNeverEmitsClientSecret() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[*].clientSecret").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }
}
