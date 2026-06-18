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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * Locks in the PRD 041 unified-refresh design: a refresh token issued at
 * {@code /oauth2/token grant_type=password} can be redeemed at
 * {@code /oauth2/token grant_type=refresh_token}, yielding a new access
 * token that unlocks protected endpoints. Same JWT format ({@code typ=refresh},
 * persistent RSA-signed) for both grants — single mechanism in
 * {@code RefreshSessionService}.
 *
 * <p>Behaviour locked in:
 * <ul>
 *   <li>Multi-tab share: refresh returns the SAME token (never-rotate);
 *       a second login also returns the same stored token.</li>
 *   <li>Restart-safe: validation is stateless (signature + {@code typ=refresh}
 *       + match against full token in user prefs).</li>
 *   <li>Single-token-per-user: revocation via {@code /oauth2/revoke} clears
 *       the prefs entry → all subsequent refreshes fail.</li>
 *   <li>{@code typ=access} cannot be redeemed at the refresh grant.</li>
 * </ul>
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

        // 1. Login via /oauth2/token grant_type=password
        OAuthTestSupport.TokenPair pair = OAuthTestSupport.loginAsWithRefresh(mockMvc, "homer", "duffs");
        String firstAccess = pair.accessToken();
        String refreshToken = pair.refreshToken();

        // 2. First access token unlocks /resources (baseline)
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + firstAccess))
                .andExpect(status().isOk());

        // 3. Redeem refresh token at /oauth2/token grant_type=refresh_token
        MvcResult refreshed = mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content("grant_type=refresh_token"
                                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                                + "&client_id=rapla-client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andReturn();
        JsonNode rotated = mapper.readTree(refreshed.getResponse().getContentAsString());
        String secondAccess = rotated.get("access_token").asString();
        String secondRefresh = rotated.get("refresh_token").asString();
        assertNotEquals(firstAccess, secondAccess, "access token must be fresh on every refresh");
        // PRD 041 never-rotate: same refresh token returned (multi-tab share works).
        assertEquals(refreshToken, secondRefresh, "refresh token kept (never-rotate)");

        // 4. New access token also unlocks /resources
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isOk());
    }

    @Test
    void refreshAcceptsLoginIssuedTokenAcrossRestarts() throws Exception
    {
        // Tokens are signed by the persistent JWK (RaplaKeyStorage-backed) and
        // validated against the full token stored in user prefs. Both survive a
        // hypothetical server restart. Surrogate: refresh twice using the
        // (same) refresh token — proves the validation is stateless against
        // any in-memory authorization store.
        ObjectMapper mapper = JsonMapper.builder().build();
        OAuthTestSupport.TokenPair pair = OAuthTestSupport.loginAsWithRefresh(mockMvc, "homer", "duffs");
        String refresh = pair.refreshToken();

        for (int i = 0; i < 2; i++)
        {
            MvcResult result = mockMvc.perform(post("/oauth2/token")
                            .contentType("application/x-www-form-urlencoded")
                            .content("grant_type=refresh_token"
                                    + "&refresh_token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8)
                                    + "&client_id=rapla-client"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
            String access = body.get("access_token").asString();
            assertTrue(access.split("\\.").length == 3, "result is a JWT");
        }
    }

    @Test
    void accessTokenRejectedByRefreshEndpoint() throws Exception
    {
        // typ=access claim must not be accepted at the refresh grant — that would
        // let a leaked access token mint indefinite new tokens.
        OAuthTestSupport.TokenPair pair = OAuthTestSupport.loginAsWithRefresh(mockMvc, "homer", "duffs");
        String accessToken = pair.accessToken();

        mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content("grant_type=refresh_token"
                                + "&refresh_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                                + "&client_id=rapla-client"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void revokeEndpointInvalidatesRefreshToken() throws Exception
    {
        // Single-token-per-user: /oauth2/revoke clears the user-prefs SESSION
        // entry → the previously-issued refresh token (and any other token in
        // circulation for the user) becomes invalid.
        OAuthTestSupport.TokenPair pair = OAuthTestSupport.loginAsWithRefresh(mockMvc, "homer", "duffs");
        String refresh = pair.refreshToken();

        // Confirm the refresh works first
        mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content("grant_type=refresh_token"
                                + "&refresh_token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8)
                                + "&client_id=rapla-client"))
                .andExpect(status().isOk());

        // Revoke
        mockMvc.perform(post("/oauth2/revoke")
                        .contentType("application/x-www-form-urlencoded")
                        .content("token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8)
                                + "&token_type_hint=refresh_token"
                                + "&client_id=rapla-client"))
                .andExpect(status().isOk());

        // Refresh now fails
        mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content("grant_type=refresh_token"
                                + "&refresh_token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8)
                                + "&client_id=rapla-client"))
                .andExpect(status().is4xxClientError());
    }
}
