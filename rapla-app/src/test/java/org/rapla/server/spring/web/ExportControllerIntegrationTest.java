package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc coverage of {@link ExportController} (PRD 030 Phase 5).
 * Verifies the CSV body shape, headers, and the AGENTS.md §12 leak-probe
 * regression (unknown tableName returns empty CSV, not error).
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@Tag("e2e")
class ExportControllerIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ExportControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();

    private String adminToken() throws Exception
    {
        MvcResult mvc = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = json.readTree(mvc.getResponse().getContentAsString());
        return tree.get("accessToken").asString();
    }

    // ---------- auth ----------

    @Test
    void exportRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/export/csv")
                        .param("tableName", "events")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- happy path ----------

    @Test
    void exportReturnsCsvContentTypeAndDispositionHeader() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/export/csv")
                        .param("tableName", "events")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(".csv")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        // At minimum, a header line is present even with no rows.
        assertTrue(body.length() > 0, "CSV body must include at least the header line");
        // CRLF line endings (RFC 4180).
        assertTrue(body.contains("\r\n"), "CSV body must use CRLF line endings");
    }

    @Test
    void exportReservationsHeaderMatchesColumns() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/export/csv")
                        .param("tableName", "events")
                        .param("from", "2001-10-15")
                        .param("to", "2001-10-22")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String[] lines = body.split("\r\n");
        assertTrue(lines.length >= 1, "CSV must have at least a header row");
        String header = lines[0];
        // The default events view ships with name/start/lastChanged columns;
        // their locale-resolved labels should appear in the header. We
        // intentionally don't pin exact label text (locale-dependent), just
        // that the column count is reasonable.
        long commas = header.chars().filter(c -> c == ',').count();
        assertTrue(commas >= 1, "expected at least 2 columns (name, start)");
    }

    @Test
    void exportAppointmentsViewWorks() throws Exception
    {
        mockMvc.perform(get("/export/csv")
                        .param("tableName", "appointments")
                        .param("from", "2001-10-15")
                        .param("to", "2001-10-22")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void exportExplicitColumnsHonored() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/export/csv")
                        .param("tableName", "events")
                        .param("from", "2001-10-15")
                        .param("to", "2001-10-22")
                        .param("columns", "name")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String[] lines = body.split("\r\n");
        // Single requested column → header should have zero commas.
        assertEquals(0, lines[0].chars().filter(c -> c == ',').count(),
                "single column header must have no commas");
    }

    // ---------- AGENTS.md §12 leak probe ----------

    @Test
    void unknownTableNameReturnsEmptyCsvNotError() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/export/csv")
                        .param("tableName", "no_such_view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        // Empty body — no header line, no rows. Same 200 OK status as the
        // happy path; an attacker can't probe view-name existence via the
        // status code.
        assertEquals("", result.getResponse().getContentAsString());
    }
}
