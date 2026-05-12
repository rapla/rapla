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
 * End-to-end MockMvc coverage of {@link CalendarViewController}
 * (PRD 024 Phase 3). Includes the canonical leak-probe regression test
 * mandated by AGENTS.md §12: a user passing an id they can't read (or an
 * id that doesn't exist) must not get differentiable responses. The
 * column set of the returned page must reflect only the user's
 * already-permitted scope.
 * <p>
 * Fixture: {@code testdefault.xml} from rapla-app's test resources.
 * Users (per the fixture): {@code homer/duffs} (admin), {@code monty/burns}
 * (non-admin).
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@Tag("e2e")
class CalendarViewControllerIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = CalendarViewControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    // Public allocatable ids from testdefault.xml — every user can read them.
    private static final String ROOM_A66      = "c24ce517-4697-4e52-9917-ec000c84563c";
    private static final String ROOM_ERWIN    = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";
    // A bogus id that does not exist in the fixture — probes the
    // "existence is information" rule from AGENTS.md §12.
    private static final String UNKNOWN_ID    = "00000000-0000-0000-0000-000000000000";

    private String adminToken() throws Exception { return loginAs("homer", "duffs"); }
    private String userToken()  throws Exception { return loginAs("monty", "burns"); }

    private String loginAs(String username, String password) throws Exception
    {
        MvcResult mvc = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = json.readTree(mvc.getResponse().getContentAsString());
        return tree.get("accessToken").asString();
    }

    // ---------- happy path ----------

    @Test
    void requiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("strategy", "BEST_FIT")
                        .param("groupBy", "DAY"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dayLayoutReturnsSevenColumnsForOneWeek() throws Exception
    {
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("strategy", "BEST_FIT")
                        .param("groupBy", "DAY")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(7))
                .andExpect(jsonPath("$.groupBy").value("DAY"))
                .andExpect(jsonPath("$.strategy").value("BEST_FIT"));
    }

    @Test
    void groupStartTimesStrategyAlsoServes() throws Exception
    {
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("strategy", "GROUP_START_TIMES")
                        .param("groupBy", "DAY")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("GROUP_START_TIMES"));
    }

    @Test
    void resourceLayoutReturnsRequestedAllocatablesAsColumns() throws Exception
    {
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("strategy", "BEST_FIT")
                        .param("groupBy", "RESOURCE")
                        .param("allocatables", ROOM_A66, ROOM_ERWIN)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(2))
                .andExpect(jsonPath("$.columns[?(@.id == '" + ROOM_A66   + "')]").exists())
                .andExpect(jsonPath("$.columns[?(@.id == '" + ROOM_ERWIN + "')]").exists());
    }

    @Test
    void existingReservationsInRangeAppearAsBlocks() throws Exception
    {
        // testdefault.xml seeds one reservation in 2001-10-16 — query that
        // range and expect at least one block back.
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2001-10-15")
                        .param("to", "2001-10-22")
                        .param("strategy", "BEST_FIT")
                        .param("groupBy", "DAY")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks").isArray())
                .andExpect(jsonPath("$.blocks.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    // ---------- AGENTS.md §12 leak-probe regression tests ----------

    /**
     * AGENTS.md §12 invariant: a non-existent id passed in the query string
     * must NOT appear as a column in the response. Existence is information;
     * an attacker probing ids must not be able to distinguish "id not present"
     * from "id present but you can't see it" by comparing responses.
     */
    @Test
    void unknownAllocatableIdIsSilentlyDroppedFromResourceColumns() throws Exception
    {
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("strategy", "BEST_FIT")
                        .param("groupBy", "RESOURCE")
                        .param("allocatables", ROOM_A66, UNKNOWN_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                // Only the known id makes it back as a column — the unknown id is
                // silently dropped, no error, no echo, no length differentiator
                // beyond "the known one is there".
                .andExpect(jsonPath("$.columns.length()").value(1))
                .andExpect(jsonPath("$.columns[0].id").value(ROOM_A66))
                .andExpect(jsonPath("$.columns[?(@.id == '" + UNKNOWN_ID + "')]").doesNotExist());
    }

    /**
     * Same as the previous test, observed from the non-admin user's
     * perspective. Confirms the gate is user-scoped, not just admin-friendly.
     */
    @Test
    void unknownAllocatableIdAlsoDroppedForNonAdmin() throws Exception
    {
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("strategy", "BEST_FIT")
                        .param("groupBy", "RESOURCE")
                        .param("allocatables", ROOM_A66, UNKNOWN_ID)
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[?(@.id == '" + UNKNOWN_ID + "')]").doesNotExist());
    }

    /**
     * Probe-indistinguishability check: the response shape for a fully-unknown
     * id must be byte-for-byte the same shape as the response for a "user
     * can't read it" id. Today the fixture doesn't have a tightly-restricted
     * allocatable (all resources are publicly readable), so this test pins
     * the easier half — passing only unknown ids returns a page with zero
     * columns. A fixture with a non-readable resource (TODO when one is
     * added) should yield the exact same response and this test will assert
     * that.
     */
    @Test
    void onlyUnknownAllocatablesYieldEmptyColumnsNotAnError() throws Exception
    {
        mockMvc.perform(get("/api/calendar/view")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-08")
                        .param("strategy", "BEST_FIT")
                        .param("groupBy", "RESOURCE")
                        .param("allocatables", UNKNOWN_ID)
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(0))
                // Status must NOT differentiate from a known-but-empty filter.
                // (i.e. no 404 / 400 — the response is still a CalendarPage.)
                .andExpect(jsonPath("$.groupBy").value("RESOURCE"))
                .andExpect(jsonPath("$.strategy").value("BEST_FIT"));
    }
}
