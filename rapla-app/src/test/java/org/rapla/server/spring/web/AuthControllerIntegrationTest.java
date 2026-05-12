package org.rapla.server.spring.web;

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AuthControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Test
    void loginIssuesJwt() throws Exception
    {
        String body = "{\"username\":\"homer\",\"password\":\"duffs\"}";
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void protectedEndpointRequiresAuth() throws Exception
    {
        // /resources is now JWT-gated
        mockMvc.perform(get("/resources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAcceptsBearer() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk())
                .andReturn();
        ObjectMapper mapper = JsonMapper.builder().build();
        String accessToken = mapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get("/resources").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshIssuesNewAccessTokenAndKeepsRefreshWhenNotStale() throws Exception
    {
        // PRD 031 design: rotate the refresh token only when it's within the
        // renewal window (7 days remaining out of 30). A freshly-issued refresh
        // token has 30 days remaining, so refresh should return the same token.
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk())
                .andReturn();
        ObjectMapper mapper = JsonMapper.builder().build();
        JsonNode tokens = mapper.readTree(login.getResponse().getContentAsString());
        String firstAccess = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        MvcResult refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();
        JsonNode rotated = mapper.readTree(refreshed.getResponse().getContentAsString());
        assertNotEquals(firstAccess, rotated.get("accessToken").asText(),
                "access token must rotate on every refresh");
        // Fresh refresh token has >7 days remaining → not rotated (rotation only when stale)
        assertEquals(refreshToken, rotated.get("refreshToken").asText(),
                "refresh token kept when far from expiry (rotate-when-stale)");
    }

    @Test
    void revokedSessionRejectsSubsequentRefresh() throws Exception
    {
        // Single-token-per-user: a second login overwrites the server-side hash,
        // so the first session's refresh token must be rejected on next use.
        ObjectMapper mapper = JsonMapper.builder().build();
        MvcResult login1 = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk()).andReturn();
        String refresh1 = mapper.readTree(login1.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk());

        // The first refresh token's hash is now stale (login overwrote it).
        mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsSessionAndInvalidatesFurtherRefresh() throws Exception
    {
        ObjectMapper mapper = JsonMapper.builder().build();
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode tokens = mapper.readTree(login.getResponse().getContentAsString());
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // After logout, the session entry is gone; refresh attempts must fail.
        mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
