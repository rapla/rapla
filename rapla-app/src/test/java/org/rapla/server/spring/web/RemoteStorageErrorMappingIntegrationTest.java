package org.rapla.server.spring.web;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 009 Phase 5 — end-to-end verification that the {@link RaplaExceptionHandler}
 * is actually wired into the running app and the previously-failing endpoints from
 * the 2026-05-08 curl sweep now return the expected HTTP status.
 *
 * <p>Sweep findings vs current behavior:
 * <pre>
 *   GET  /storage/user          (no userId)        500 → 400  (MissingServletRequestParameter)
 *   POST /storage/refresh       (no lastValidated) 500 → 400
 *   GET  /resources             (no auth)          ___ → 401  (JWT gate)
 * </pre>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class RemoteStorageErrorMappingIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = RemoteStorageErrorMappingIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    private String adminToken() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
    }

    @Test
    void getUser_withoutUserIdParam_returns400() throws Exception
    {
        String token = adminToken();
        mockMvc.perform(get("/api/storage/user").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postRefresh_withoutLastValidatedParam_returns400() throws Exception
    {
        String token = adminToken();
        mockMvc.perform(post("/api/storage/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getResources_withoutAuth_returns401() throws Exception
    {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postLogin_withBadCredentials_returns400() throws Exception
    {
        // OAuth2 standard /oauth2/token returns 400 invalid_grant on bad creds (RFC 6749 §5.2),
        // not 401. Different from rapla-custom /api/auth/login's 401 — but standard now.
        mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content("grant_type=password&username=homer&password=wrong&client_id=rapla-client"))
                .andExpect(status().isBadRequest());
    }
}
