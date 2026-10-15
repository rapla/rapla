package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Patch namespace folders over HTTP (PRD 133 D1): rapla/ feeds the fixed URLs, /patch/<ns>/ serves allowed types only, no session needed. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class PatchResourceTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;
    static Path patchDir;

    @BeforeAll
    static void fixture() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PatchResourceTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
        patchDir = Files.createDirectories(tempDir.resolve("patch"));
        Path rapla = Files.createDirectories(patchDir.resolve("rapla"));
        Path local = Files.createDirectories(patchDir.resolve("local"));
        Files.writeString(rapla.resolve("spa.css"), ":root { --bar-bg: #namespaced; }");
        Files.writeString(rapla.resolve("favicon.ico"), "ICON-namespaced");
        Files.writeString(rapla.resolve("index.mustache"), "<p>secret-template</p>");
        Files.writeString(local.resolve("logo.svg"), "<svg>logo-namespaced</svg>");
        Files.writeString(local.resolve("Hidden.graphql"), "query Hidden { secret-view }");
        Files.writeString(patchDir.resolve("spa.css"), ":root { --bar-bg: #top-level; }");
        Files.writeString(patchDir.resolve("logo.svg"), "<svg>top-level</svg>");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.patch-dir", () -> patchDir.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    private MockHttpServletResponse fetch(String path) throws Exception
    {
        return mockMvc.perform(get(path)).andReturn().getResponse();
    }

    @Test
    void fixedUrlsComeFromTheRaplaFolder() throws Exception
    {
        assertEquals(":root { --bar-bg: #namespaced; }", fetch("/app/spa.css").getContentAsString());
        for (String path : new String[] { "/favicon.ico", "/app/favicon.ico", "/images/favicon.ico" })
        {
            MockHttpServletResponse response = fetch(path);
            assertEquals(200, response.getStatus(), path);
            assertEquals("ICON-namespaced", response.getContentAsString(), path);
        }
    }

    @Test
    void namespaceAssetsAreServedWithoutASession() throws Exception
    {
        MockHttpServletResponse logo = fetch("/patch/local/logo.svg");
        assertEquals(200, logo.getStatus());
        assertEquals("<svg>logo-namespaced</svg>", logo.getContentAsString());
        assertEquals(":root { --bar-bg: #namespaced; }", fetch("/patch/rapla/spa.css").getContentAsString());
    }

    @Test
    void templatesViewsAndTopLevelFilesAreNeverServed() throws Exception
    {
        for (String path : new String[] { "/patch/rapla/index.mustache", "/patch/local/Hidden.graphql", "/patch/logo.svg", "/patch/spa.css" })
        {
            MockHttpServletResponse response = fetch(path);
            String body = response.getContentAsString();
            assertFalse(body.contains("secret") || body.contains("top-level"), path + ": " + body);
            assertEquals(404, response.getStatus(), path);
        }
        assertFalse(fetch("/app/spa.css").getContentAsString().contains("top-level"));
    }
}
