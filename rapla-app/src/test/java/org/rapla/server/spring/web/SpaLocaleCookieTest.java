package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * PRD 124 OQ2 (user ruling 2026-10-01): the language chosen on the login page (raplaLocale
 * cookie) is the SPA's language — the cookie is sent to /api/locale and wins there.
 */
@Tag("e2e")
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class SpaLocaleCookieTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @Test
    void loginChoiceCookieReachesTheApi() throws Exception
    {
        mockMvc.perform(get("/login").param("lang", "fr").accept(MediaType.TEXT_HTML))
                .andExpect(cookie().value("raplaLocale", "fr"))
                .andExpect(cookie().path("raplaLocale", "/"));
    }

    @Test
    void localeFollowsTheLoginChoice() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "admin", "");
        mockMvc.perform(get("/api/locale/spa").header("Authorization", "Bearer " + token)
                        .cookie(new Cookie("raplaLocale", "fr")))
                .andExpect(jsonPath("$.language").value("fr"));
    }

    @Test
    void meDeliversTheResolvedLanguage() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "admin", "");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)
                        .cookie(new Cookie("raplaLocale", "fr")))
                .andExpect(jsonPath("$.language").value("fr"));
        String withoutCookie = mockMvc.perform(get("/api/locale/spa").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.language").value(
                        com.jayway.jsonpath.JsonPath.<String>read(withoutCookie, "$.language")));
    }

    @Test
    void unknownCookieLanguageIsIgnored() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "admin", "");
        mockMvc.perform(get("/api/locale/spa").header("Authorization", "Bearer " + token)
                        .cookie(new Cookie("raplaLocale", "xx")))
                .andExpect(jsonPath("$.language").value(not("xx")));
    }
}
