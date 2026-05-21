package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for the stale-JWT-blocks-OAuth-login bug surfaced 2026-05-21.
 * Two-layer fix; this test covers the server-side belt-and-braces half.
 *
 * Before the fix: if any client (the SPA's interceptor used to do this) sent
 * an invalid Authorization: Bearer ... header to /api/auth/oauth/exchange/*,
 * Spring's resource-server JwtAuthenticationFilter rejected the request with
 * 401 before the controller could run — even though /api/auth/** is permitAll.
 * Classic gotcha: permitAll means "auth not required", not "auth not checked
 * when offered".
 *
 * After the fix: /api/auth/oauth/** is served by a dedicated SecurityFilterChain
 * with no oauth2ResourceServer, so stale Bearers are ignored. The controller is
 * reached and produces its normal error (400 invalid_request for an unknown
 * provider in the default test profile).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class OAuthExchangeStaleTokenAnonymousTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OAuthExchangeStaleTokenAnonymousTest.class.getResourceAsStream("/testdefault.xml"))
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

    private static final String STALE_BEARER =
            "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzdGFsZSJ9.signature-garbage";

    @Test
    void exchangeIgnoresStaleBearerInsteadOf401() throws Exception
    {
        mockMvc.perform(post("/api/auth/oauth/exchange/keycloak")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization", STALE_BEARER)
                        .content("grant_type=authorization_code&code=test&code_verifier=v"))
                // The controller is reached: keycloak provider isn't configured in
                // the default test profile, so we get 400 invalid_request — NOT 401
                // from the resource-server JWT filter rejecting the stale Bearer.
                .andExpect(status().isBadRequest());
    }

    @Test
    void configReachableWithStaleBearer() throws Exception
    {
        // /api/auth/oauth/config is the SPA's pre-login discovery probe. A stale
        // Bearer from a prior session must not block it either.
        mockMvc.perform(get("/api/auth/oauth/config")
                        .header("Authorization", STALE_BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void regularApiStillRejectsStaleBearer() throws Exception
    {
        // Sanity: the JWT filter still guards everything ELSE. A stale Bearer
        // sent to a regular authenticated endpoint must still get 401 — we are
        // narrowing the carve-out, not removing it.
        mockMvc.perform(get("/api/auth/api-keys")
                        .header("Authorization", STALE_BEARER))
                .andExpect(status().is(not(200)))
                .andExpect(status().isUnauthorized());
    }
}
