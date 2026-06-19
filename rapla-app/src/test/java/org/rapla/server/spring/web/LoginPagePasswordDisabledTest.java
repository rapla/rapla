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
 * PRD 072 Phase 1 — with {@code rapla.oauth.web.password-login=false}, the
 * combined {@code /login} page drops the inline username/password form but
 * STILL renders the SSO chooser, and the rapla SAS password grant remains
 * reachable at {@code /oauth2/token} (break-glass for integrators, untouched
 * by this flag).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPagePasswordDisabledTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPagePasswordDisabledTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.oauth.web.password-login", () -> "false");
        registry.add("rapla.oauth.external.keycloak.enabled", () -> "true");
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "https://kc.example.com");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "rapla-test");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> "rapla-app");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void passwordFormAbsentWhenFlagOff() throws Exception
    {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"password\""))))
                // SSO chooser still present
                .andExpect(content().string(containsString("/oauth2/authorization/keycloak")));
    }

    @Test
    void raplaSasPasswordGrantStillReachable() throws Exception
    {
        // The password-login web flag does NOT gate the SAS direct grant. A
        // bad-credentials password grant reaches the token endpoint and returns
        // an OAuth error (400), NOT a 404 / 302-to-login. We assert it is NOT a
        // redirect to the login page and the endpoint exists.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/oauth2/token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=password&client_id=rapla-client&username=admin&password=wrong"))
                .andExpect(status().is(not(404)))
                .andExpect(status().is(not(302)));
    }
}
