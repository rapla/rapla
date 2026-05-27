package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * End-to-end MockMvc coverage of {@link TableViewController}.
 * Real fixture, OAuth2 password-grant login.
 *
 * <p>Fixture: {@code testdefault.xml}. Users: {@code homer/duffs} (admin),
 * {@code monty/burns} (non-admin).
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

    /** Build a minimal {from,to} POST body — covers the default selection-empty case. */
    private static String body(String from, String to)
    {
        return "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}";
    }

    /** POST request with the given JSON body. */
    private MockHttpServletRequestBuilder postTable(String path, String json)
    {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    // ---------- requires auth ----------

    @Test
    void reservationsRequiresAuthentication() throws Exception
    {
        mockMvc.perform(postTable("/api/table/reservations", body("2026-06-01", "2026-06-08")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void appointmentsRequiresAuthentication() throws Exception
    {
        mockMvc.perform(postTable("/api/table/appointments", body("2026-06-01", "2026-06-08")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- happy path ----------

    @Test
    void reservationsReturnsTablePageShape() throws Exception
    {
        mockMvc.perform(postTable("/api/table/reservations", body("2026-06-01", "2026-06-08"))
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
        mockMvc.perform(postTable("/api/table/appointments", body("2026-06-01", "2026-06-08"))
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
        mockMvc.perform(postTable("/api/table/reservations", body("2001-10-15", "2001-10-22"))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void allocatablesNarrowingFiltersTheTable() throws Exception
    {
        // Anchor on a single allocatable that the testdefault fixture has
        // reservations on (room "erwin", 5521686b-…). Result is non-empty
        // for that resource. Compare against the no-filter case (admin,
        // same window) to assert the narrowing actually narrowed.
        String unfiltered = "{\"from\":\"2001-10-01\",\"to\":\"2001-11-01\"}";
        var unfilteredResult = mockMvc.perform(postTable("/api/table/reservations", unfiltered)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk()).andReturn();
        int unfilteredCount = json.readTree(unfilteredResult.getResponse().getContentAsString())
                .get("rows").size();

        String narrow = "{\"from\":\"2001-10-01\",\"to\":\"2001-11-01\","
                + "\"allocatables\":[\"5521686b-0ab4-4ff4-a56e-0bdf148e8d1d\"]}";
        var narrowResult = mockMvc.perform(postTable("/api/table/reservations", narrow)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk()).andReturn();
        int narrowCount = json.readTree(narrowResult.getResponse().getContentAsString())
                .get("rows").size();

        // The narrowed scope must produce at most as many rows, and the
        // resource has at least one reservation in the seeded window.
        org.junit.jupiter.api.Assertions.assertTrue(
                narrowCount > 0, "room erwin should have ≥1 reservation in window");
        org.junit.jupiter.api.Assertions.assertTrue(
                narrowCount <= unfilteredCount,
                "narrowed result (" + narrowCount + ") must be ≤ unfiltered (" + unfilteredCount + ")");
    }

    @Test
    void nonAdminUserSeesReservationsOnReadableResources() throws Exception
    {
        // testdefault.xml seeds reservations around 2001-10-16, all owned by
        // homer (admin). monty is a non-admin who owns NO reservations but has
        // READ access to the rooms those reservations allocate.
        //
        // The reservations-table view must scope by "what the user can read",
        // not "what the user owns" — otherwise non-owning users see an empty
        // table (the bug christopher.kohlhaas reported against DHBW).
        String montyToken = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        mockMvc.perform(postTable("/api/table/reservations", body("2001-10-01", "2001-11-01"))
                        .header("Authorization", "Bearer " + montyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    // ---------- AGENTS.md §12 leak-probe regression tests ----------

    @Test
    void unknownColumnIdIsSilentlyDropped() throws Exception
    {
        String json = "{\"from\":\"2026-06-01\",\"to\":\"2026-06-08\","
                + "\"columns\":[\"no-such-column-existing\"]}";
        mockMvc.perform(postTable("/api/table/reservations", json)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                // No column with that id appears in the response — the
                // server doesn't echo back unknown ids.
                .andExpect(jsonPath("$.columns[?(@.id == 'no-such-column-existing')]").doesNotExist());
    }

    @Test
    void unknownSortColumnIsIgnoredNotErrored() throws Exception
    {
        String json = "{\"from\":\"2026-06-01\",\"to\":\"2026-06-08\","
                + "\"sort\":[\"no-such-column:asc\"]}";
        mockMvc.perform(postTable("/api/table/reservations", json)
                        .header("Authorization", "Bearer " + adminToken()))
                // Same as the column-id case — unknown sort spec is dropped,
                // not echoed as an error. Caller gets a valid (unsorted) page.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    void unknownAllocatableIdIsSilentlyDropped() throws Exception
    {
        // AGENTS.md §12: passing an id the caller can't see must give the same
        // response as the id-doesn't-exist case. Neither status, error body, nor
        // headers reveal which case the caller is in. After silent-drop, the
        // selection collapses to empty and the server falls through to its
        // "all readable" default — that's leak-free because both the
        // "doesn't exist" and "exists but unreadable" probes land in the
        // same default state.
        String json = "{\"from\":\"2001-10-01\",\"to\":\"2001-11-01\","
                + "\"allocatables\":[\"NO_SUCH_ALLOCATABLE_ID\"]}";
        mockMvc.perform(postTable("/api/table/reservations", json)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    void unknownReservationFilterTypeIsSilentlyDropped() throws Exception
    {
        // Same §12 invariant for the filter shape: unknown type → entry dropped,
        // not a 400/500.
        String json = "{\"from\":\"2026-06-01\",\"to\":\"2026-06-08\","
                + "\"reservationFilter\":[{\"typeId\":\"NO_SUCH_TYPE\",\"rules\":[]}]}";
        mockMvc.perform(postTable("/api/table/reservations", json)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());
    }

    // ---------- /config + /columns/catalog ----------

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
