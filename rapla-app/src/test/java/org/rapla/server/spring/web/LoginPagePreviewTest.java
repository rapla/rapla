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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Preview aid: writes the rendered login page (Swing client / browser context) to target/ for looking at without a server. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPagePreviewTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPagePreviewTest.class.getResourceAsStream("/testdefault.xml"))
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
        return page(session, "en");
    }

    private String page(MockHttpSession session, String language) throws Exception
    {
        var request = get("/login").header("Accept-Language", language);
        if (session != null) request = request.session(session);
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    @Test
    void writesClientAndBrowserPreview() throws Exception
    {
        Path target = Path.of(System.getProperty("basedir", "."), "target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("login-preview-browser.html"), page(null));
        var authorize = mockMvc.perform(get("/oauth2/authorize?response_type=code&client_id=rapla-client"
                + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
                + "&scope=openid&state=xyz&redirect_uri=http://127.0.0.1:54321/login/oauth2/code/rapla").accept(MediaType.TEXT_HTML)).andReturn();
        MockHttpSession swing = (MockHttpSession) authorize.getRequest().getSession(false);
        Files.writeString(target.resolve("login-preview-client.html"), page(swing));
        Files.writeString(target.resolve("login-preview-client-de.html"), page(swing, "de"));
    }
}
