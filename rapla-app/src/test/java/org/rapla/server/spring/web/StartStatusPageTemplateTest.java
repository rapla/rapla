package org.rapla.server.spring.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

/** Start page (/) and status page (/server) are Mustache templates the patch directory restyles (index.css) or replaces. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class StartStatusPageTemplateTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;
    static Path patchDir;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = StartStatusPageTemplateTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
        patchDir = Files.createDirectories(tempDir.resolve("patch").resolve("rapla"));
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.patch-dir", () -> patchDir.getParent().toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RaplaResources i18n;

    @AfterEach
    void removePatchFiles() throws Exception
    {
        for (String name : new String[] { "index.css", "index.mustache", "status.mustache" })
        {
            Files.deleteIfExists(patchDir.resolve(name));
        }
    }

    private String page(String path) throws Exception
    {
        MockHttpServletResponse response = mockMvc.perform(get(path)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), path);
        return response.getContentAsString();
    }

    @Test
    void startPageKeepsItsEntriesAndInfoText() throws Exception
    {
        String html = page("/");
        assertTrue(html.contains("app/\">Open web app</a>"), html);
        assertTrue(html.contains("raplaclient.jnlp\">"), html);
        assertTrue(html.contains("server\">"), html);
        assertTrue(html.contains(i18n.getString("webinfo.text")), html);
        assertTrue(html.contains("default.css"), html);
        assertTrue(html.contains("images/favicon.ico"), html);
        assertTrue(html.contains("<div class=\"logo\"></div>"), html);
        assertFalse(html.contains("patch-index-css"), html);
    }

    @Test
    void statusPageReportsRunning() throws Exception
    {
        String html = page("/server");
        assertTrue(html.contains("Server running"), html);
        assertTrue(html.contains("default.css"), html);
        assertTrue(html.contains("<div class=\"logo\"></div>"), html);
        assertFalse(html.contains("patch-index-css"), html);
    }

    @Test
    void indexCssIsInlinedOnBothPages() throws Exception
    {
        Files.writeString(patchDir.resolve("index.css"), ".logo { height: 44px; }");
        for (String path : new String[] { "/", "/server" })
        {
            String html = page(path);
            assertTrue(html.contains("<style id=\"patch-index-css\">.logo { height: 44px; }</style>"), path + ": " + html);
        }
    }

    @Test
    void pageFilesAtTheTopLevelAreNotRead() throws Exception
    {
        Path topLevel = patchDir.getParent();
        try
        {
            Files.writeString(topLevel.resolve("index.css"), ".top-level {}");
            Files.writeString(topLevel.resolve("index.mustache"), "<p>top-level</p>");
            String html = page("/");
            assertFalse(html.contains("top-level"), html);
            assertTrue(html.contains("class=\"menuEntry\""), html);
        }
        finally
        {
            Files.deleteIfExists(topLevel.resolve("index.css"));
            Files.deleteIfExists(topLevel.resolve("index.mustache"));
        }
    }

    @Test
    void patchTemplatesReplaceTheBuiltInOnes() throws Exception
    {
        Files.writeString(patchDir.resolve("index.mustache"), "<p>start {{title}}</p>{{#menu}}<i>{{{html}}}</i>{{/menu}}");
        Files.writeString(patchDir.resolve("status.mustache"), "<p>status {{{info}}}</p>");
        String start = page("/");
        assertTrue(start.startsWith("<p>start "), start);
        assertTrue(start.contains("<i><span class=\"button\">"), start);
        assertTrue(page("/server").startsWith("<p>status "), "status page");
    }

    @Test
    void brokenPatchTemplateFallsBackToTheBuiltIn() throws Exception
    {
        Files.writeString(patchDir.resolve("index.mustache"), "{{#menu}}unclosed");
        assertTrue(page("/").contains("class=\"menuEntry\""));
    }
}
