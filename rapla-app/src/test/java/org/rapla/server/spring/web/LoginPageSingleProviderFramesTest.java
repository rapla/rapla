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
class LoginPageSingleProviderFramesTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPageSingleProviderFramesTest.class.getResourceAsStream("/testdefault.xml"))
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
    void oneProviderTitlesItsSectionWithItsLabel() throws Exception
    {
        String html = page(null);
        int band = html.indexOf("class=\"group\"");
        int sso = html.indexOf("<p class=\"stitle\">Keycloak</p>");
        int divider = html.indexOf("class=\"divider\"");
        int server = html.indexOf("<p class=\"stitle\">Rapla Server</p>");
        int direct = html.indexOf("<details>");
        assertTrue(band > 0 && band < sso && sso < divider && divider < server && server < direct, html);
        assertFalse(html.contains("Single Sign-On</p>"), html);
    }

    @Test
    void keycloakWithoutBundleOrDisplayNameSaysSingleSignOn() throws Exception
    {
        String html = page(null);
        assertTrue(html.contains(">Sign in with Single Sign-On</button>"), html);
        assertFalse(html.contains("Sign in with Keycloak"), html);
    }
}
