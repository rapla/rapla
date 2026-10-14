package org.rapla.server.spring.web;

import org.junit.jupiter.api.AfterEach;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** PRD 131 round 2 — provider texts from a deployment bundle, login.css / login.mustache from the patch directory. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPageCustomizationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;
    static Path patchDir;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPageCustomizationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
        patchDir = Files.createDirectories(tempDir.resolve("patch"));
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.patch-dir", () -> patchDir.toAbsolutePath().toString());
        registry.add("rapla.oauth.external.kctest.enabled", () -> "true");
        registry.add("rapla.oauth.external.kctest.type", () -> "keycloak");
        registry.add("rapla.oauth.external.kctest.base-url", () -> "https://kc.example.com");
        registry.add("rapla.oauth.external.kctest.realm", () -> "example-realm");
        registry.add("rapla.oauth.external.kctest.client-id", () -> "rapla-app");
        registry.add("rapla.oauth.external.kctest.i18n-bundle", () -> "org.rapla.test.TestLoginResources");
    }

    @Autowired
    MockMvc mockMvc;

    @AfterEach
    void removePatchFiles() throws Exception
    {
        Files.deleteIfExists(patchDir.resolve("login.css"));
        Files.deleteIfExists(patchDir.resolve("login.mustache"));
    }

    private String page(String language) throws Exception
    {
        return mockMvc.perform(get("/login").header("Accept-Language", language)).andReturn().getResponse().getContentAsString();
    }

    private static void write(String name, String content, long mtimeMillis) throws Exception
    {
        Path file = patchDir.resolve(name);
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.fromMillis(mtimeMillis));
    }

    @Test
    void providerButtonAndTitleComeFromTheDeploymentBundle() throws Exception
    {
        String en = page("en");
        assertTrue(en.contains("<p class=\"stitle\">Test Keycloak</p>"), en);
        assertTrue(en.contains(">Sign in with test account</button>"), en);
        String de = page("de");
        assertTrue(de.contains("<p class=\"stitle\">Test-Keycloak</p>"), de);
        assertTrue(de.contains(">Anmelden mit Testkonto</button>"), de);
    }

    @Test
    void builtInStyleUsesColourVariablesAndHasAnEmptyLogoSlot() throws Exception
    {
        String html = page("en");
        assertTrue(html.contains("--accent: #27ae60") && html.contains("var(--accent)"), html);
        assertTrue(html.contains("<body>\n  <div class=\"logo\"></div>"), html);
        assertFalse(html.contains("patch-login-css"), html);
    }

    @Test
    void patchCssIsInlinedAfterTheBuiltInStyleAndReloadedWhenChanged() throws Exception
    {
        write("login.css", ".card { color: #123456; }", 1_000_000L);
        String first = page("en");
        assertTrue(first.contains("<style id=\"patch-login-css\">.card { color: #123456; }</style>"), first);
        assertTrue(first.indexOf("--accent: #27ae60") < first.indexOf("patch-login-css"), first);
        write("login.css", ".card { color: #654321; }", 2_000_000L);
        String second = page("en");
        assertTrue(second.contains("#654321") && !second.contains("#123456"), second);
    }

    @Test
    void patchTemplateReplacesTheBuiltInOneAndIsRecompiledWhenChanged() throws Exception
    {
        write("login.mustache", "<html><h1>PATCHED {{title}}</h1></html>", 1_000_000L);
        assertTrue(page("en").contains("<h1>PATCHED Rapla Login</h1>"));
        write("login.mustache", "<html><h1>AGAIN {{title}}</h1></html>", 2_000_000L);
        assertTrue(page("en").contains("<h1>AGAIN Rapla Login</h1>"));
        Files.delete(patchDir.resolve("login.mustache"));
        assertTrue(page("en").contains("class=\"group\""), "back to the built-in template once the file is gone");
    }
}
