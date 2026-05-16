package org.rapla.server.spring.web;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared test helper for OAuth2-standard login. Replaces the per-file
 * {@code loginAs(...)} helpers that POSTed to the deprecated
 * {@code /api/auth/login} (rapla-custom JSON shape). After PRD 041's
 * unified-refresh consolidation, the canonical login path is
 * {@code POST /oauth2/token grant_type=password} — form-encoded body,
 * snake_case response. This helper keeps every test on the same path
 * and removes the last consumers of {@code AuthController}.
 *
 * <p>Returns the raw access token (Bearer) — JWT, signed by the persistent
 * RSA key, {@code typ=access}. Caller passes it as
 * {@code Authorization: Bearer <token>} on subsequent requests.
 */
public final class OAuthTestSupport
{
    private OAuthTestSupport() {}

    /**
     * Logs in as the given user via {@code POST /oauth2/token grant_type=password}
     * and returns the access token. Throws if the login fails.
     */
    public static String loginAs(MockMvc mockMvc, String username, String password) throws Exception
    {
        String body = "grant_type=password"
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&client_id=rapla-client";
        MvcResult mvc = mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = JsonMapper.builder().build().readTree(mvc.getResponse().getContentAsString());
        return tree.get("access_token").asString();
    }

    /**
     * Logs in and returns BOTH the access and refresh tokens. Used by
     * refresh-flow tests that need to exercise the refresh grant.
     */
    public static TokenPair loginAsWithRefresh(MockMvc mockMvc, String username, String password) throws Exception
    {
        String body = "grant_type=password"
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&client_id=rapla-client";
        MvcResult mvc = mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = JsonMapper.builder().build().readTree(mvc.getResponse().getContentAsString());
        return new TokenPair(tree.get("access_token").asString(), tree.get("refresh_token").asString());
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
