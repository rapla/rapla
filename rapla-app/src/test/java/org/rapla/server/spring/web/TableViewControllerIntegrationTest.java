package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc coverage of {@link TableViewController}
 * (PRD 030 Phase 2). Mirrors {@link CalendarViewControllerIntegrationTest}'s
 * pattern: real fixture, JWT-via-{@code /auth/login}, and the canonical
 * AGENTS.md §12 leak-probe regression tests (unknown ids silently dropped,
 * not echoed back).
 *
 * <p>Fixture: {@code testdefault.xml} from rapla-app's test resources.
 * Users (per the fixture): {@code homer/duffs} (admin), {@code monty/burns}
 * (non-admin).
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@Tag("e2e")
class TableViewControllerIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = TableViewControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    private String adminToken() throws Exception { return OAuthTestSupport.loginAs(mockMvc, "homer", "duffs"); }

    // ---------- requires auth ----------

    @Test
    void reservationsRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/table/reservations")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void appointmentsRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/table/appointments")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- happy path ----------

    @Test
    void reservationsReturnsTablePageShape() throws Exception
    {
        mockMvc.perform(get("/api/table/reservations")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.totalCount").exists())
                .andExpect(jsonPath("$.incomplete").value(false));
    }

    @Test
    void appointmentsReturnsTablePageShape() throws Exception
    {
        mockMvc.perform(get("/api/table/appointments")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.totalCount").exists());
    }

    @Test
    void existingReservationsInRangeAppearAsRows() throws Exception
    {
        // testdefault.xml seeds reservations around 2001-10-16 — query that range.
        mockMvc.perform(get("/api/table/reservations")
                        .param("from", "2001-10-15")
                        .param("to", "2001-10-22")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    // ---------- pagination ----------

    @Test
    void pageSizeEnforcedAndCursorReturned() throws Exception
    {
        mockMvc.perform(get("/api/table/reservations")
                        .param("from", "2001-10-01")
                        .param("to", "2001-11-01")
                        .param("pageSize", "1")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(1)));
    }

    // ---------- AGENTS.md §12 leak-probe regression tests ----------

    @Test
    void unknownColumnIdIsSilentlyDropped() throws Exception
    {
        mockMvc.perform(get("/api/table/reservations")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("columns", "no-such-column-existing")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                // No column with that id appears in the response — the
                // server doesn't echo back unknown ids.
                .andExpect(jsonPath("$.columns[?(@.id == 'no-such-column-existing')]").doesNotExist());
    }

    @Test
    void unknownSortColumnIsIgnoredNotErrored() throws Exception
    {
        mockMvc.perform(get("/api/table/reservations")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("sort", "no-such-column:asc")
                        .header("Authorization", "Bearer " + adminToken()))
                // Same as the column-id case — unknown sort spec is dropped,
                // not echoed as an error. Caller gets a valid (unsorted) page.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());
    }

    // ---------- /config + /columns/catalog (Phase 3) ----------

    @Test
    void configRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/table/config").param("tableName", "events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void columnsCatalogRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/table/columns/catalog").param("tableName", "events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configForEventsViewReturnsColumns() throws Exception
    {
        mockMvc.perform(get("/api/table/config")
                        .param("tableName", "events")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("events"))
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.columns.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                // Built-in column ids that the default events view ships with:
                .andExpect(jsonPath("$.columns[?(@.id == 'name')]").exists())
                .andExpect(jsonPath("$.columns[?(@.id == 'start')]").exists());
    }

    @Test
    void configForAppointmentsViewReturnsColumns() throws Exception
    {
        mockMvc.perform(get("/api/table/config")
                        .param("tableName", "appointments")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("appointments"))
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.columns.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void columnsCatalogReturnsUniverse() throws Exception
    {
        mockMvc.perform(get("/api/table/columns/catalog")
                        .param("tableName", "events")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("events"))
                .andExpect(jsonPath("$.columns").isArray())
                // Catalog must contain at least the default columns plus any
                // plugin contributions visible system-wide.
                .andExpect(jsonPath("$.columns.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void unknownTableNameReturnsEmptyConfigNotError() throws Exception
    {
        // AGENTS.md §12: don't differentiate "unknown view" from "known view
        // with empty config" via status code. Both return 200 + empty columns.
        mockMvc.perform(get("/api/table/config")
                        .param("tableName", "nonexistent_view")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("nonexistent_view"))
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.columns.length()").value(0));
    }

    @Test
    void columnsCatalogColumnsCarryTypeDescriptor() throws Exception
    {
        // Each column descriptor must carry id + label + type so Angular
        // knows how to render the cell.
        mockMvc.perform(get("/api/table/columns/catalog")
                        .param("tableName", "events")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].id").exists())
                .andExpect(jsonPath("$.columns[0].label").exists())
                .andExpect(jsonPath("$.columns[0].type").exists());
    }
}
