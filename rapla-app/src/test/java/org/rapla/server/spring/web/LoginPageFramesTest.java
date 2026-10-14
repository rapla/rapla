package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** PRD 131 R2/R19 — the login page login page: one group, titled sections per login way, Swing/browser context texts. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPageFramesTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPageFramesTest.class.getResourceAsStream("/testdefault.xml"))
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

    private String page(MockHttpSession session) throws Exception
    {
        var request = get("/login").header("Accept-Language", "en");
        if (session != null) request = request.session(session);
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession swingSession(String redirectUri) throws Exception
    {
        var result = mockMvc.perform(get("/oauth2/authorize?response_type=code&client_id=rapla-client"
                        + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
                        + "&scope=openid&state=xyz&redirect_uri=" + redirectUri).accept(MediaType.TEXT_HTML)).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void groupThenSsoSectionThenDividerThenDirectStage() throws Exception
    {
        String html = page(null);
        int band = html.indexOf("class=\"group\"");
        int ssoTitle = html.indexOf("<p class=\"stitle\">Single Sign-On</p>");
        int sso = html.indexOf("formaction=\"/oauth2/authorization/");
        int divider = html.indexOf("class=\"divider\"");
        int server = html.indexOf("<p class=\"stitle\">Rapla Server</p>");
        int direct = html.indexOf("<details>");
        int password = html.indexOf("name=\"password\"");
        assertTrue(band > 0 && band < ssoTitle && ssoTitle < sso && sso < divider && divider < server && server < direct && direct < password, html);
        assertFalse(html.contains("<fieldset") || html.contains("<legend>") || html.contains("✔"), html);
        assertFalse(html.contains("<p class=\"stitle\">Keycloak</p>"), "two providers share a generic title: " + html);
    }

    @Test
    void browserContextShowsBrowserTextsAndNoHint() throws Exception
    {
        String html = page(null);
        assertTrue(html.contains("The sign-in only lasts until the browser is next restarted."), html);
        assertTrue(html.contains("The browser stores the sign-in on this device"), html);
        assertFalse(html.contains("Sign-in for the Rapla client"), html);
        assertFalse(html.contains("class=\"sub\""), html);
    }

    @Test
    void swingContextShowsHintAndClientTexts() throws Exception
    {
        String html = page(swingSession("http://127.0.0.1:54321/login/oauth2/code/rapla"));
        assertTrue(html.contains("class=\"sub\""), html);
        assertTrue(html.contains("After signing in you can close this browser window."), html);
        assertTrue(html.contains("You are signed out automatically when Rapla exits."), html);
        assertTrue(html.contains("Rapla remembers the sign-in on this device for your user"), html);
        assertFalse(html.contains("until the browser is next restarted"), html);
        assertTrue(html.contains("How long should you stay signed in?") && html.contains("This session only"), html);
        assertTrue(html.indexOf("class=\"sub\"") < html.indexOf("class=\"group\""), "hint above the group: " + html);
    }

    @Test
    void passwordFieldsSitBehindTheDirectSignInButton() throws Exception
    {
        String html = page(null);
        int details = html.indexOf("<details>");
        int summary = html.indexOf("Sign in directly with Rapla</summary>");
        int username = html.indexOf("name=\"username\"");
        int end = html.indexOf("</details>");
        assertTrue(details > 0 && details < summary && summary < username && username < end, html);
        assertTrue(html.indexOf("formaction=\"/oauth2/authorization/") < details, "SSO buttons stay on the first stage: " + html);
        assertTrue(html.indexOf("class=\"default-submit\"") < username, "Enter in the password field still signs in with the password: " + html);
    }

    @Test
    void openingTheDirectStageHidesItsButtonAndFocusesTheUsername() throws Exception
    {
        String html = page(null);
        assertTrue(html.contains("details[open] > summary.direct { display: none; }"), html);
        assertTrue(html.contains("addEventListener('toggle'") && html.contains("getElementById('u').focus()"), html);
    }

    @Test
    void failedLoginReopensTheDirectStage() throws Exception
    {
        String html = mockMvc.perform(get("/login").param("error", "").header("Accept-Language", "en")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("<details open>"), html);
    }

    @Test
    void nonLoopbackAuthorizeIsBrowserContext() throws Exception
    {
        MockHttpSession session = swingSession("http://localhost:3000/app/auth/callback");
        String html = page(session);
        assertFalse(html.contains("class=\"sub\""), html);
        assertTrue(html.contains("until the browser is next restarted"), html);
    }
}
