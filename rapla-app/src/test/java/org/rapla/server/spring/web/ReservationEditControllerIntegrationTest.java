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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc coverage of {@link ReservationEditController}
 * (PRD 024 Phase 1). The validator itself has 14 tier-1 tests in
 * {@code RepeatingRuleValidatorTest} — this test only verifies the
 * wire round-trip + JWT gate.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@Tag("e2e")
class ReservationEditControllerIntegrationTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationEditControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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
                .andExpect(status().isOk()).andReturn();
        JsonNode tree = json.readTree(mvc.getResponse().getContentAsString());
        return tree.get("accessToken").asString();
    }

    // ---------- JWT gate ----------

    @Test
    void requiresAuthentication() throws Exception
    {
        mockMvc.perform(post("/edit/validate-recurrence")
                        .contentType("application/json")
                        .content("""
                            {"type":"daily","interval":1,"weekdays":[],
                             "endingMode":"FOREVER","endDate":null,"repeatCount":-1,
                             "appointmentStart":"2026-06-01T09:00:00"}"""))
                .andExpect(status().isUnauthorized());
    }

    // ---------- happy path ----------

    @Test
    void validDailyForeverIsValid() throws Exception
    {
        mockMvc.perform(post("/edit/validate-recurrence")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"type":"daily","interval":1,"weekdays":[],
                             "endingMode":"FOREVER","endDate":null,"repeatCount":-1,
                             "appointmentStart":"2026-06-01T09:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.issues.length()").value(0));
    }

    // ---------- error cases ----------

    @Test
    void intervalZeroReportsClampIssue() throws Exception
    {
        mockMvc.perform(post("/edit/validate-recurrence")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"type":"daily","interval":0,"weekdays":[],
                             "endingMode":"FOREVER","endDate":null,"repeatCount":-1,
                             "appointmentStart":"2026-06-01T09:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[?(@.code == 'INTERVAL_LESS_THAN_ONE')]").exists());
    }

    @Test
    void weeklyWithNoWeekdaysReportsIssue() throws Exception
    {
        mockMvc.perform(post("/edit/validate-recurrence")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"type":"weekly","interval":1,"weekdays":[],
                             "endingMode":"FOREVER","endDate":null,"repeatCount":-1,
                             "appointmentStart":"2026-06-01T09:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[?(@.code == 'WEEKLY_WITH_NO_WEEKDAYS')]").exists());
    }

    @Test
    void untilEndBeforeStartReportsIssue() throws Exception
    {
        mockMvc.perform(post("/edit/validate-recurrence")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"type":"daily","interval":1,"weekdays":[],
                             "endingMode":"UNTIL","endDate":"2026-05-01T09:00:00",
                             "repeatCount":-1,"appointmentStart":"2026-06-01T09:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[?(@.code == 'UNTIL_END_BEFORE_START')]").exists());
    }

    @Test
    void multipleIssuesAllSurface() throws Exception
    {
        mockMvc.perform(post("/edit/validate-recurrence")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"type":"weekly","interval":0,"weekdays":[],
                             "endingMode":"N_TIMES","endDate":null,"repeatCount":0,
                             "appointmentStart":"2026-06-01T09:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues.length()").value(3));
    }

    // ---------- /check-conflicts ----------

    // Public allocatable id from testdefault.xml.
    private static final String ROOM_A66   = "c24ce517-4697-4e52-9917-ec000c84563c";
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    void checkConflictsRequiresAuthentication() throws Exception
    {
        mockMvc.perform(post("/edit/check-conflicts")
                        .contentType("application/json")
                        .content("""
                            {"allocatableIds":[],"appointments":[],"today":"2026-06-01"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkConflictsEmptyRequestReturnsEmptyOutcomes() throws Exception
    {
        mockMvc.perform(post("/edit/check-conflicts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"allocatableIds":[],"appointments":[],"today":"2026-06-01"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes.length()").value(0));
    }

    @Test
    void checkConflictsKnownAllocatableProducesOutcome() throws Exception
    {
        String body = """
            {"allocatableIds":["%s"],
             "appointments":[{"start":"2026-06-01T09:00:00","end":"2026-06-01T10:00:00","recurrence":null}],
             "today":"2026-06-01"}""".formatted(ROOM_A66);
        mockMvc.perform(post("/edit/check-conflicts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes.length()").value(1))
                .andExpect(jsonPath("$.outcomes[0].allocatableId").value(ROOM_A66))
                .andExpect(jsonPath("$.outcomes[0].conflictingAppointments.length()").value(1));
    }

    /**
     * AGENTS.md §12 leak-probe regression: unknown allocatable id must
     * be silently dropped from the outcomes — no error, no echo, no
     * way for a client to distinguish "id doesn't exist" from "you can't
     * see it" by comparing responses.
     */
    @Test
    void checkConflictsUnknownAllocatableSilentlyDropped() throws Exception
    {
        String body = """
            {"allocatableIds":["%s","%s"],
             "appointments":[{"start":"2026-06-01T09:00:00","end":"2026-06-01T10:00:00","recurrence":null}],
             "today":"2026-06-01"}""".formatted(ROOM_A66, UNKNOWN_ID);
        mockMvc.perform(post("/edit/check-conflicts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes.length()").value(1))
                .andExpect(jsonPath("$.outcomes[0].allocatableId").value(ROOM_A66))
                .andExpect(jsonPath("$.outcomes[?(@.allocatableId == '" + UNKNOWN_ID + "')]").doesNotExist());
    }

    @Test
    void checkConflictsOnlyUnknownIdsReturnsEmptyOutcomesNotError() throws Exception
    {
        String body = """
            {"allocatableIds":["%s"],
             "appointments":[{"start":"2026-06-01T09:00:00","end":"2026-06-01T10:00:00","recurrence":null}],
             "today":"2026-06-01"}""".formatted(UNKNOWN_ID);
        // Same status code + same shape as "known but no conflicts" —
        // probe-indistinguishable.
        mockMvc.perform(post("/edit/check-conflicts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes.length()").value(0));
    }
}
