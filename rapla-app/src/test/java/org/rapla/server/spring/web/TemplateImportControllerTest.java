package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.plugin.eventimport.ParsedTemplateResult;
import org.rapla.plugin.eventimport.TemplateImport;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Swing "events into templates" menu POSTs to
 * {@code /api/templateimport/importFromServer} through the {@link TemplateImport}
 * proxy. Until 2026-08-29 no server-side controller existed for that interface
 * (the impl was only registered as a plain {@code @Bean}), so the call 404'd.
 *
 * <p>The real {@code RaplaTemplateImport} needs a {@code DBOperator} plus the
 * deployment-specific {@code vw_seminarimporte} view — the test context runs on
 * the XML file operator, so a hand-rolled stub bean stands in (AGENTS.md §13:
 * no mock framework, no {@code @MockBean}).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class TemplateImportControllerTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = TemplateImportControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    @TestConfiguration
    static class StubImportConfig
    {
        @Bean
        @Primary
        TemplateImport stubTemplateImport()
        {
            return () -> {
                ParsedTemplateResult result = new ParsedTemplateResult();
                result.setHeader(List.of(TemplateImport.PRIMARY_KEY, TemplateImport.TEMPLATE_KEY));
                Map<String, String> row = new LinkedHashMap<>();
                row.put(TemplateImport.PRIMARY_KEY, "S-4711");
                row.put(TemplateImport.TEMPLATE_KEY, "Ferienwoche");
                result.addTemplate(row);
                return result;
            };
        }
    }

    @Autowired MockMvc mockMvc;

    @Test
    void adminGetsTheParsedTemplates() throws Exception
    {
        String adminToken = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        mockMvc.perform(post("/api/templateimport/importFromServer")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header[0]").value(TemplateImport.PRIMARY_KEY))
                .andExpect(jsonPath("$.templateList[0]." + TemplateImport.TEMPLATE_KEY).value("Ferienwoche"));
    }

    /** The import runs under the deployment's planning account, which is typically NOT a
     *  global admin (rapla 2.0 had no permission check here at all). The gate is therefore
     *  "may create events", not `isAdmin()` — a caller who cannot create any reservation
     *  type has no business pulling the import rows either. */
    @Test
    void aNonAdminWhoMayCreateEventsIsAllowed() throws Exception
    {
        String montyToken = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");

        mockMvc.perform(post("/api/templateimport/importFromServer")
                        .header("Authorization", "Bearer " + montyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header[0]").value(TemplateImport.PRIMARY_KEY));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception
    {
        mockMvc.perform(post("/api/templateimport/importFromServer"))
                .andExpect(status().isUnauthorized());
    }
}
