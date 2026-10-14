package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PRD 131 R2/D2 — the stay-signed-in choice is made once on {@code /login}, without JavaScript, and
 * governs SSO and password login alike: one POST form, the segmented choice above the SSO buttons, and the
 * cookies of the resulting session are persistent only when "on" was chosen.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPageRememberChoiceTest
{
    private static final String CHOICE = "rapla-remember-choice";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPageRememberChoiceTest.class.getResourceAsStream("/testdefault.xml"))
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
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "https://kc.example.com");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "example-realm");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> "rapla-app");
        registry.add("rapla.oauth.external.google.enabled", () -> "true");
        registry.add("rapla.oauth.external.google.client-id", () -> "test-google-client");
        registry.add("rapla.oauth.external.google.client-secret", () -> "test-google-secret");
    }

    @Autowired
    MockMvc mockMvc;

    private String loginForm(String language) throws Exception
    {
        String html = mockMvc.perform(get("/login").header("Accept-Language", language))
                .andReturn().getResponse().getContentAsString();
        int start = html.indexOf("<form");
        int end = html.indexOf("</form>");
        assertTrue(start >= 0 && end > start, "the login page must render its form, was: " + html);
        assertEquals(start, html.lastIndexOf("<form"), "checkbox, SSO entries and password fields share ONE form");
        return html.substring(start, end);
    }

    private static List<String> setCookies(MvcResult result)
    {
        return result.getResponse().getHeaders("Set-Cookie");
    }

    private static boolean sets(MvcResult result, String name)
    {
        return setCookies(result).stream().anyMatch(h -> h.startsWith(name + "="));
    }

    @Test
    void checkboxSitsAboveTheSsoButtonsInTheSamePostForm() throws Exception
    {
        String form = loginForm("en");
        assertTrue(form.startsWith("<form method=\"post\" action=\"/login\">"), form);
        int box = form.indexOf("name=\"remember-me\" value=\"off\"");
        int keycloak = form.indexOf("formaction=\"/oauth2/authorization/keycloak");
        int google = form.indexOf("formaction=\"/oauth2/authorization/google");
        assertTrue(box > 0, "segmented choice missing: " + form);
        assertTrue(keycloak > box && google > box, "the choice must come before the SSO entries: " + form);
        assertTrue(form.contains("name=\"_csrf\""), "the shared form must carry the CSRF field");
        assertTrue(form.contains("name=\"password\""), "the password fields stay in the same form");
    }

    @Test
    void theChoiceWorksWithoutScript() throws Exception
    {
        String form = loginForm("en");
        assertTrue(form.contains("<button type=\"submit\" class=\"sso\" formnovalidate formaction=\"/oauth2/authorization/keycloak?prompt=login\">"), form);
        assertFalse(form.contains("<a "), "SSO entries are submit buttons, not links that would drop the checkbox: " + form);
        assertFalse(form.contains("onclick"), form);
        assertFalse(form.contains("method=\"get\""), "a password must never travel in a URL: " + form);
    }

    @Test
    void enterInThePasswordFieldSignsInWithThePassword() throws Exception
    {
        String form = loginForm("en");
        int tagStart = form.indexOf("<button");
        String firstButton = form.substring(tagStart, form.indexOf('>', tagStart));
        assertTrue(firstButton.contains("type=\"submit\""), firstButton);
        assertFalse(firstButton.contains("formaction="),
                "the form's default (Enter) button must be the password login, not an SSO entry: " + form);
    }

    @Test
    void segmentedChoiceSitsInTheGroupAboveTheTitledSections() throws Exception
    {
        String form = loginForm("en");
        int question = form.indexOf("Store the sign-in on this device?");
        int off = form.indexOf("name=\"remember-me\" value=\"off\"");
        int on = form.indexOf("name=\"remember-me\" value=\"on\"");
        int frame = form.indexOf("<hr class=\"gsep\">");
        int sso = form.indexOf("formaction=\"/oauth2/authorization/");
        int password = form.indexOf("name=\"password\"");
        assertTrue(question > 0 && question < off && off < on && on < frame && frame < sso && sso < password, form);
        assertTrue(form.contains("role=\"group\" aria-labelledby=\"ask\""), form);
        assertTrue(form.indexOf("class=\"stitle\">Rapla Server") > sso && form.indexOf("class=\"stitle\">Rapla Server") < form.indexOf("<details") && form.indexOf("<details") < password, form);
        assertFalse(form.contains("<fieldset") || form.contains("<legend>") || form.contains("✔"), form);
        assertTrue(form.contains("type=\"radio\""), form);
        assertTrue(form.contains(">No<"), form);
        assertTrue(form.contains("Yes, up to 21 days"), form);
        assertFalse(form.contains("Keycloak, Google"), "the provider list is gone from the text: " + form);
        assertTrue(loginForm("de").contains("Ja, bis zu 21 Tage"));
        assertTrue(loginForm("de").contains("Die Anmeldung gilt nur bis zum n&auml;chsten Neustart des Browsers."));
        assertTrue(loginForm("de").contains("bleibt sie erhalten. Mit &bdquo;Abmelden (Logout)&ldquo; endet sie sofort."));
    }

    @Test
    void sessionOnlyIsPreselectedWithoutCookieAndFollowsTheCookie() throws Exception
    {
        String none = loginForm("en");
        assertTrue(none.contains("value=\"off\" checked"), none);
        assertFalse(none.contains("value=\"on\" checked"), none);
        String html = mockMvc.perform(get("/login").cookie(new Cookie(CHOICE, "1")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"on\" checked"), html);
        assertFalse(html.contains("value=\"off\" checked"), html);
    }

    @Test
    void ssoStepWithOffDeletesAnEarlierChoice() throws Exception
    {
        MvcResult step = mockMvc.perform(post("/oauth2/authorization/google").param("remember-me", "off")
                        .cookie(new Cookie(CHOICE, "1")).with(csrf())).andReturn();
        Cookie choice = step.getResponse().getCookie(CHOICE);
        assertNotNull(choice, "off must not count as remembered");
        assertEquals(0, choice.getMaxAge());
    }

    @Test
    void offPasswordLoginSetsSessionCookiesAndClearsTheChoice() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "monty").param("password", "burns").param("remember-me", "off")
                        .cookie(new Cookie(CHOICE, "1")).with(csrf()).session(new MockHttpSession()))
                .andReturn();
        assertEquals(-1, login.getResponse().getCookie("access_token").getMaxAge());
        assertEquals(-1, login.getResponse().getCookie("refresh_token").getMaxAge());
        assertEquals(0, login.getResponse().getCookie(CHOICE).getMaxAge());
    }

    @Test
    void ssoStepWithTheCheckboxRemembersAndLeadsToTheProvider() throws Exception
    {
        MvcResult step = mockMvc.perform(post("/oauth2/authorization/keycloak?prompt=login")
                        .param("remember-me", "on").param("username", "monty").param("password", "burns")
                        .with(csrf()))
                .andReturn();
        assertEquals(302, step.getResponse().getStatus());
        String location = step.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("https://kc.example.com/realms/example-realm/protocol/openid-connect/auth"), location);
        assertTrue(location.contains("prompt=login"), "the always-prompt of Keycloak must survive the POST: " + location);
        assertFalse(location.contains("burns"), "a typed password must never reach a redirect URL: " + location);
        Cookie choice = step.getResponse().getCookie(CHOICE);
        assertNotNull(choice, "the SSO step must record the choice, was: " + setCookies(step));
        assertEquals("1", choice.getValue());
    }

    @Test
    void ssoStepWithoutTheCheckboxDeletesAnEarlierChoice() throws Exception
    {
        MvcResult step = mockMvc.perform(post("/oauth2/authorization/google")
                        .cookie(new Cookie(CHOICE, "1")).with(csrf()))
                .andReturn();
        assertEquals(302, step.getResponse().getStatus());
        Cookie choice = step.getResponse().getCookie(CHOICE);
        assertNotNull(choice, "an unticked SSO step must delete the earlier choice");
        assertEquals(0, choice.getMaxAge());
    }

    @Test
    void ssoStepWithoutCheckboxOrEarlierChoiceLeavesNothingBehind() throws Exception
    {
        MvcResult step = mockMvc.perform(post("/oauth2/authorization/google").with(csrf())).andReturn();
        assertEquals(302, step.getResponse().getStatus());
        assertFalse(sets(step, CHOICE), "no tick, no cookie: " + setCookies(step));
    }

    @Test
    void ssoLinkWithoutThePostStaysAsItWas() throws Exception
    {
        MvcResult link = mockMvc.perform(get("/oauth2/authorization/google").cookie(new Cookie(CHOICE, "1"))).andReturn();
        assertEquals(302, link.getResponse().getStatus());
        assertFalse(sets(link, CHOICE), "only the login form decides, a bare link keeps the earlier choice");
    }

    @Test
    void tickedPasswordLoginSetsPersistentCookies() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "monty").param("password", "burns").param("remember-me", "on")
                        .with(csrf()).session(new MockHttpSession()))
                .andReturn();
        Cookie access = login.getResponse().getCookie("access_token");
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertNotNull(access);
        assertNotNull(refresh);
        assertEquals(3600, access.getMaxAge());
        assertEquals(21 * 24 * 3600, refresh.getMaxAge());
        assertFalse(String.valueOf(login.getResponse().getHeader("Location")).contains("burns"));
    }

    @Test
    void untickedPasswordLoginSetsSessionCookies() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "monty").param("password", "burns")
                        .with(csrf()).session(new MockHttpSession()))
                .andReturn();
        Cookie access = login.getResponse().getCookie("access_token");
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertNotNull(access);
        assertNotNull(refresh);
        assertEquals(-1, access.getMaxAge(), "no Max-Age: the browser drops it when it closes");
        assertEquals(-1, refresh.getMaxAge(), "no Max-Age: the browser drops it when it closes");
        assertFalse(String.valueOf(login.getResponse().getHeader("Location")).contains("burns"));
    }

    @Test
    void untickedLoginAfterATickedOneDecidesFromTheParameterNotTheOldCookie() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "monty").param("password", "burns")
                        .cookie(new Cookie(CHOICE, "1"))
                        .with(csrf()).session(new MockHttpSession()))
                .andReturn();
        assertEquals(-1, login.getResponse().getCookie("refresh_token").getMaxAge());
        assertEquals(-1, login.getResponse().getCookie("access_token").getMaxAge());
    }
}
