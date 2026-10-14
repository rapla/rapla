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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** PRD 131 R2 — without SSO providers the group carries the segmented choice, the "Rapla Server" section the password form. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPagePasswordOnlyChoiceTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPagePasswordOnlyChoiceTest.class.getResourceAsStream("/testdefault.xml"))
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
    void passwordOnlyPageShowsGroupAndServerSection() throws Exception
    {
        String html = mockMvc.perform(get("/login").header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString();
        int frame = html.indexOf("<hr class=\"gsep\">");
        int off = html.indexOf("name=\"remember-me\" value=\"off\"");
        int on = html.indexOf("name=\"remember-me\" value=\"on\"");
        int password = html.indexOf("name=\"password\"");
        assertTrue(off > 0 && off < on && on < frame && frame < password, html);
        assertFalse(html.contains("formaction=\"/oauth2/authorization/"), html);
        assertTrue(html.contains("<p class=\"stitle\">Rapla Server</p>") && frame < html.indexOf("class=\"stitle\"") && html.indexOf("class=\"stitle\"") < password, html);
        assertFalse(html.contains("class=\"divider\"") || html.contains("Keycloak"), html);
    }

    @Test
    void withoutSsoThePasswordFormIsOnTheFirstStage() throws Exception
    {
        String html = mockMvc.perform(get("/login").header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("<details"), html);
        assertFalse(html.contains("Sign in directly on the server"), html);
        assertTrue(html.contains("name=\"username\""), html);
    }
}
