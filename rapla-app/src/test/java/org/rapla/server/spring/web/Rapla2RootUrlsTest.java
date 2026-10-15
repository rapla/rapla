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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** A Rapla 2 that ran at the root context published /rapla/raplaclient(.jnlp) and /rapla/index; without rapla.legacy-context-path they answer directly and publicly. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class Rapla2RootUrlsTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = Rapla2RootUrlsTest.class.getResourceAsStream("/testdefault.xml"))
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

    private MockHttpServletResponse anonymousGet(String path) throws Exception
    {
        return mockMvc.perform(get(path)).andReturn().getResponse();
    }

    @Test
    void legacyJnlpUrlsServeTheSameJnlpWithTheRootCodebase() throws Exception
    {
        for (String path : new String[] { "/rapla/raplaclient.jnlp", "/rapla/raplaclient" })
        {
            MockHttpServletResponse response = anonymousGet(path);
            assertEquals(200, response.getStatus(), path);
            String jnlp = response.getContentAsString();
            assertTrue(jnlp.contains("codebase=\"http://localhost/\" href=\"raplaclient.jnlp\""), path + ": " + jnlp);
        }
    }

    @Test
    void legacyIndexServesTheStartPage() throws Exception
    {
        MockHttpServletResponse response = anonymousGet("/rapla/index");
        assertEquals(200, response.getStatus());
        assertEquals(anonymousGet("/index").getContentAsString(), response.getContentAsString());
    }

    @Test
    void calendarLinksStayUntouched() throws Exception
    {
        MockHttpServletResponse response = anonymousGet("/rapla/calendar");
        assertFalse(response.getStatus() == 401 || response.getStatus() / 100 == 3, "status " + response.getStatus());
        assertNull(response.getHeader("Location"));
    }
}
