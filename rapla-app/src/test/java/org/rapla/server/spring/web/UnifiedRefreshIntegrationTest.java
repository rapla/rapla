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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the PRD 031 unified-refresh design: a refresh token issued at
 * {@code /auth/login} can be redeemed at {@code /auth/refresh}, yielding a
 * new access token that unlocks protected endpoints. After the OAuth token
 * customizer (typ=refresh claim), this also has to be true for refresh
 * tokens issued by Spring Authorization Server's {@code /oauth2/token} —
 * verified indirectly via the {@code typ=refresh} claim being present on
 * SAS-issued tokens. Driving the full PKCE flow under MockMvc is harder
 * than the value justifies; the discovery shape + AuthControllerIntegrationTest
 * already cover that side.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class UnifiedRefreshIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = UnifiedRefreshIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
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
    void refreshTokenRedeemsForNewAccessAndUnlocksProtectedEndpoint() throws Exception
    {
        ObjectMapper mapper = JsonMapper.builder().build();

        // 1. Login
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = mapper.readTree(login.getResponse().getContentAsString());
        String firstAccess = tokens.get("accessToken").asString();
        String refreshToken = tokens.get("refreshToken").asString();
        assertNotNull(firstAccess);
        assertNotNull(refreshToken);

        // 2. Old access token unlocks /resources (baseline)
        mockMvc.perform(get("/api/resources").header("Authorization", "Bearer " + firstAccess))
                .andExpect(status().isOk());

        // 3. Redeem refresh token for a new access token
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();
        JsonNode rotated = mapper.readTree(refreshed.getResponse().getContentAsString());
        String secondAccess = rotated.get("accessToken").asString();
        String secondRefresh = rotated.get("refreshToken").asString();
        assertNotEquals(firstAccess, secondAccess, "access token must rotate on every refresh");
        // PRD 031 rotate-when-stale design: fresh refresh token (>7d remaining) is kept;
        // it would only rotate when within the renewal window.
        assertEquals(refreshToken, secondRefresh, "refresh token kept when not stale");

        // 4. New access token also unlocks /resources
        mockMvc.perform(get("/api/resources").header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isOk());
    }

    @Test
    void refreshAcceptsLoginIssuedTokenAcrossRestarts() throws Exception
    {
        // Tokens are signed by the persistent JWK (RaplaKeyStorage-backed). A
        // refresh token issued before a hypothetical restart would still
        // validate, because /auth/refresh is stateless — signature + typ=refresh
        // claim only, no SAS-state lookup. Surrogate: just refresh twice using
        // the previous refresh token, ensuring the chain works through rotation.
        ObjectMapper mapper = JsonMapper.builder().build();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refresh1 = mapper.readTree(login.getResponse().getContentAsString())
                .get("refreshToken").asString();

        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refresh2 = mapper.readTree(firstRefresh.getResponse().getContentAsString())
                .get("refreshToken").asString();

        MvcResult secondRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String access3 = mapper.readTree(secondRefresh.getResponse().getContentAsString())
                .get("accessToken").asString();
        assertTrue(access3.split("\\.").length == 3, "result is a JWT");
    }

    @Test
    void accessTokenRejectedByRefreshEndpoint() throws Exception
    {
        // typ=access claim must not be accepted by /auth/refresh — that would
        // let a leaked access token mint indefinite new tokens.
        ObjectMapper mapper = JsonMapper.builder().build();
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = mapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asString();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().is4xxClientError());
    }
}
