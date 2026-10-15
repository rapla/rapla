package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The start page's web-app link is the menu bean "0_spa"; rapla.services.0_spa: false (YAML, no brackets needed) hides it, /app/ keeps serving. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class StartPageSpaLinkSwitchTest
{
    static final String LINK = "app/\">Open web app</a>";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = StartPageSpaLinkSwitchTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
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
    void startPageLinksTheWebAppByDefault() throws Exception
    {
        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains(LINK), html);
    }

    @Nested
    @TestPropertySource(properties = "spring.config.import=classpath:spa-link-off.yml")
    class SwitchedOff
    {
        @Autowired
        MockMvc mockMvc;

        @Test
        void startPageHidesTheLinkAndTheAppStillServes() throws Exception
        {
            String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
            assertFalse(html.contains(LINK), html);
            assertTrue(html.contains("class=\"menuEntry\""), "the other entries stay: " + html);
            assertEquals(200, mockMvc.perform(get("/app/")).andReturn().getResponse().getStatus());
        }
    }
}
