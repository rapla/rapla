package org.rapla.server.spring.web;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 131 OQ1 — {@code rapla.auth.remember-me-days} is the one lifetime N: it is the refresh JWT's
 * {@code exp - iat} and, when the user chose to stay signed in, the {@code Max-Age} of the refresh cookie
 * on every login path.
 */
abstract class AbstractRefreshLifetimeTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    abstract int days();

    @Test
    void formLoginRefreshCookieAndJwtLiveForNDays() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "admin").param("password", "").param("remember-me", "on")
                        .with(csrf()).session(new MockHttpSession()))
                .andReturn();

        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertNotNull(refresh, "form login must set the refresh cookie");
        assertEquals(Duration.ofDays(days()).toSeconds(), refresh.getMaxAge());
        assertEquals(Duration.ofDays(days()), lifetime(refresh.getValue()));
    }

    @Test
    void passwordGrantRefreshCookieAndJwtLiveForNDays() throws Exception
    {
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .cookie(new Cookie("rapla-remember-choice", "1"))
                        .content("grant_type=password&username=admin&password=&client_id=rapla-client"))
                .andExpect(status().isOk())
                .andReturn();

        String refreshJwt = JsonMapper.builder().build()
                .readTree(token.getResponse().getContentAsString()).get("refresh_token").asString();
        Cookie refresh = token.getResponse().getCookie("refresh_token");
        assertNotNull(refresh, "the token response must be mirrored into the refresh cookie");
        assertEquals(Duration.ofDays(days()).toSeconds(), refresh.getMaxAge());
        assertEquals(Duration.ofDays(days()), lifetime(refreshJwt));
    }

    private static Duration lifetime(String jwt) throws Exception
    {
        var claims = SignedJWT.parse(jwt).getJWTClaimsSet();
        return Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant());
    }
}
