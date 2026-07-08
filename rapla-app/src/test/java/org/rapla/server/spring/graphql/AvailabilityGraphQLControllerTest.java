package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 091 Phase 1 — tier-3 tests for the availability queries
 * (`resourceAvailability` cheap display path, `potentialConflicts` detail
 * path). The acceptance case is UC-C2 from the equipment-lending archetype
 * (docs/usecases/equipment-planning.md): "which room/camera is free for a
 * single multi-day window", candidates selected via typeIn filter.
 *
 * <p>testdefault.xml ships no overlapping reservations, so each test creates
 * its own busy reservation through the shipped createReservation mutation
 * (distinct far-future weeks per test — the class-level fixture copy is
 * shared across methods).
 *
 * <p>Fixture facts used: rooms "Room A66" (c24ce517…) and "erwin"
 * (5521686b…) of type key "room"; users homer (admin) / monty (non-admin).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class AvailabilityGraphQLControllerTest
{
    static final String ROOM_A66 = "c24ce517-4697-4e52-9917-ec000c84563c";
    static final String ROOM_ERWIN = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AvailabilityGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing from classpath");
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

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate())
                .url("/api/graphql")
                .build();
    }

    private void createBusyReservation(String allocatableId, String start, String end)
    {
        String id = tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: { name: "busy-fixture-loan" } },
                    appointments: [
                      { id: "%s", start: "%s", end: "%s", allDay: false }
                    ],
                    allocations: [
                      { allocatableId: "%s" }
                    ]
                  }) { id }
                }
                """.formatted(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                        start, end, allocatableId))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();
        assertNotNull(id, "busy-reservation setup must succeed");
    }

    // ============================================================ schema smoke

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaIncludesAvailabilityQueries()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Query") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("resourceAvailability"),
                () -> "missing Query.resourceAvailability in " + names);
        assertTrue(names.contains("potentialConflicts"),
                () -> "missing Query.potentialConflicts in " + names);
    }

    // ============================================================ UC-C2 acceptance

    /**
     * UC-C2: single multi-day draft appointment (Mon 09:00 – Fri 17:00),
     * candidates via typeIn filter. Room A66 is busy on Wednesday →
     * CONFLICT with the draft appointment id; erwin is free → AVAILABLE.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void ucC2FinderSingleMultiDayAppointment()
    {
        createBusyReservation(ROOM_A66, "2031-06-04T10:00:00", "2031-06-04T12:00:00");

        String draftAppointmentId = "a11c0de1-1111-4111-8111-111111111111";
        List<Map<String, Object>> rows = tester.document("""
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "%s", start: "2031-06-02T09:00:00", end: "2031-06-06T17:00:00", allDay: false }
                    ],
                    candidates: { filter: { typeIn: [room] } }
                  }) {
                    allocatable { id name }
                    status
                    conflictingAppointmentIds
                  }
                }
                """.formatted(draftAppointmentId))
                .execute()
                .path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        Map<String, Map<String, Object>> byId = rows.stream().collect(Collectors.toMap(
                r -> (String) ((Map<?, ?>) r.get("allocatable")).get("id"), r -> r));
        assertTrue(byId.containsKey(ROOM_A66), () -> "Room A66 missing from " + byId.keySet());
        assertTrue(byId.containsKey(ROOM_ERWIN), () -> "erwin missing from " + byId.keySet());

        Map<String, Object> a66 = byId.get(ROOM_A66);
        assertEquals("CONFLICT", a66.get("status"), "busy room must be CONFLICT (single appointment → no PARTIAL)");
        assertEquals(List.of(draftAppointmentId), a66.get("conflictingAppointmentIds"),
                "clash must be attributed to the draft appointment id");

        Map<String, Object> erwin = byId.get(ROOM_ERWIN);
        assertEquals("AVAILABLE", erwin.get("status"));
        assertEquals(List.of(), erwin.get("conflictingAppointmentIds"));
    }

    // ============================================================ potentialConflicts drill-down

    /**
     * Drill-down on the busy room: one row, side 1 = the draft (id echoed,
     * reservation1 null — brand-new draft), side 2 = the stored counterparty
     * (readable for homer → resolved + described), startDate = the clash.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void potentialConflictsResolveCounterparty()
    {
        createBusyReservation(ROOM_A66, "2031-06-11T10:00:00", "2031-06-11T12:00:00");

        String draftReservationId = "e22c0de2-2222-4222-8222-222222222222";
        String draftAppointmentId = "a22c0de2-2222-4222-8222-222222222223";
        List<Map<String, Object>> rows = tester.document("""
                query {
                  potentialConflicts(input: {
                    reservationId: "%s",
                    appointments: [
                      { id: "%s", start: "2031-06-09T09:00:00", end: "2031-06-13T17:00:00", allDay: false }
                    ],
                    allocatableIds: ["%s"]
                  }) {
                    id
                    allocatable { id }
                    reservation1Id
                    appointment1Id
                    reservation2Id
                    appointment2Id
                    appointment1 { id start end }
                    reservation1 { id }
                    reservation2 { id name }
                    appointment2 { id start }
                    description
                    startDate
                  }
                }
                """.formatted(draftReservationId, draftAppointmentId, ROOM_A66))
                .execute()
                .path("potentialConflicts")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        assertEquals(1, rows.size(), () -> "expected exactly one potential conflict; got " + rows);
        Map<String, Object> row = rows.get(0);
        assertEquals(ROOM_A66, ((Map<?, ?>) row.get("allocatable")).get("id"));
        assertEquals(draftReservationId, row.get("reservation1Id"), "side 1 = draft reservation id (echoed)");
        assertEquals(draftAppointmentId, row.get("appointment1Id"), "side 1 = draft appointment id (echoed)");
        assertNull(row.get("reservation1"), "brand-new draft → reservation1 null");
        assertNotNull(row.get("appointment1"), "draft appointment materialized from input");
        assertEquals(draftAppointmentId, ((Map<?, ?>) row.get("appointment1")).get("id"));
        assertNotNull(row.get("reservation2Id"), "readable counterparty → id present");
        assertNotNull(row.get("appointment2Id"));
        assertNotNull(row.get("reservation2"), "readable counterparty → resolved");
        assertNotNull(row.get("appointment2"));
        String description = (String) row.get("description");
        assertNotNull(description);
        assertFalse(description.isBlank(), "description must carry display text");
        assertEquals("2031-06-11T10:00:00", row.get("startDate"),
                "startDate = first clash instant (busy start inside the draft window)");
    }

    // ============================================================ PRD 091 Phase 4.5 — recurring drafts

    /**
     * Weekly series (10×, Tue 10–12 from 2031-08-04), busy slot 2 weeks in
     * on the same weekday → the rule must be materialized (not rejected
     * UNSUPPORTED, not flattened to the first occurrence): Room A66 CONFLICT
     * attributed to the draft appointment id, erwin AVAILABLE.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void weeklyDraftDetectsClashOnLaterOccurrence()
    {
        createBusyReservation(ROOM_A66, "2031-08-18T10:30:00", "2031-08-18T11:30:00");

        String draftAppointmentId = "a55c0de5-5555-4555-8555-555555555555";
        List<Map<String, Object>> rows = tester.document("""
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "%s", start: "2031-08-04T10:00:00", end: "2031-08-04T12:00:00", allDay: false,
                        repeating: { type: WEEKLY, interval: 1, count: 10, exceptions: [] } }
                    ],
                    candidates: { ids: ["%s", "%s"] }
                  }) {
                    allocatable { id }
                    status
                    conflictingAppointmentIds
                  }
                }
                """.formatted(draftAppointmentId, ROOM_A66, ROOM_ERWIN))
                .execute()
                .path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        Map<String, Map<String, Object>> byId = rows.stream().collect(Collectors.toMap(
                r -> (String) ((Map<?, ?>) r.get("allocatable")).get("id"), r -> r));
        Map<String, Object> a66 = byId.get(ROOM_A66);
        assertNotNull(a66, () -> "Room A66 missing from " + byId.keySet());
        assertEquals("CONFLICT", a66.get("status"),
                "series clash on a later occurrence must surface (rule materialized, not flattened)");
        assertEquals(List.of(draftAppointmentId), a66.get("conflictingAppointmentIds"));
        assertEquals("AVAILABLE", byId.get(ROOM_ERWIN).get("status"));
    }

    /**
     * Same series shape, but the busy date is excluded via
     * repeating.exceptions → no occurrence there → AVAILABLE.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void exceptionOnBusyDateClearsConflict()
    {
        createBusyReservation(ROOM_A66, "2031-10-21T10:30:00", "2031-10-21T11:30:00");

        List<Map<String, Object>> rows = tester.document("""
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "a66c0de6-6666-4666-8666-666666666666",
                        start: "2031-10-07T10:00:00", end: "2031-10-07T12:00:00", allDay: false,
                        repeating: { type: WEEKLY, interval: 1, count: 10, exceptions: ["2031-10-21"] } }
                    ],
                    candidates: { ids: ["%s"] }
                  }) {
                    allocatable { id }
                    status
                    conflictingAppointmentIds
                  }
                }
                """.formatted(ROOM_A66))
                .execute()
                .path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        assertEquals(1, rows.size());
        assertEquals("AVAILABLE", rows.get(0).get("status"),
                "exception on the busy date must remove the occurrence (exceptions materialized)");
    }

    /**
     * Endless series (no end, no count) — analytic overlap must terminate
     * and find the clash 26 weeks in (2031-11-04 + 182 days = 2032-05-04).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void endlessSeriesTerminatesAndDetectsClash()
    {
        createBusyReservation(ROOM_A66, "2032-05-04T10:30:00", "2032-05-04T11:30:00");

        List<Map<String, Object>> rows = tester.document("""
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "a77c0de7-7777-4777-8777-777777777777",
                        start: "2031-11-04T10:00:00", end: "2031-11-04T12:00:00", allDay: false,
                        repeating: { type: WEEKLY, interval: 1, exceptions: [] } }
                    ],
                    candidates: { ids: ["%s"] }
                  }) { status conflictingAppointmentIds }
                }
                """.formatted(ROOM_A66))
                .execute()
                .path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        assertEquals(1, rows.size());
        assertEquals("CONFLICT", rows.get(0).get("status"),
                "endless series must detect the clash on a far occurrence (analytic overlap)");
    }

    /**
     * potentialConflicts on a recurring draft: startDate = the first clash
     * instant on the clashing occurrence (2 weeks in), not the series start.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void potentialConflictsOnSeriesReportsFirstClashDate()
    {
        createBusyReservation(ROOM_A66, "2031-09-16T10:30:00", "2031-09-16T11:30:00");

        List<Map<String, Object>> rows = tester.document("""
                query {
                  potentialConflicts(input: {
                    reservationId: "e88c0de8-8888-4888-8888-888888888880",
                    appointments: [
                      { id: "a88c0de8-8888-4888-8888-888888888888",
                        start: "2031-09-02T10:00:00", end: "2031-09-02T12:00:00", allDay: false,
                        repeating: { type: WEEKLY, interval: 1, count: 10, exceptions: [] } }
                    ],
                    allocatableIds: ["%s"]
                  }) { startDate }
                }
                """.formatted(ROOM_A66))
                .execute()
                .path("potentialConflicts")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        assertEquals(1, rows.size(), () -> "expected exactly one potential conflict; got " + rows);
        assertEquals("2031-09-16T10:30:00", rows.get(0).get("startDate"),
                "startDate = first clash instant on the clashing occurrence");
    }

    /**
     * Self-ignore while editing a stored recurring event: without
     * ignoreReservationIds the stored series blocks its own room; with the
     * id ignored the room is AVAILABLE.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void ignoreReservationIdsWorksForStoredSeries()
    {
        String storedId = java.util.UUID.randomUUID().toString();
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: { name: "stored-series-fixture" } },
                    appointments: [
                      { id: "%s", start: "2031-12-03T10:00:00", end: "2031-12-03T12:00:00", allDay: false,
                        repeating: { type: WEEKLY, interval: 1, count: 10, exceptions: [] } }
                    ],
                    allocations: [ { allocatableId: "%s" } ]
                  }) { id }
                }
                """.formatted(storedId, java.util.UUID.randomUUID(), ROOM_ERWIN))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        String draft = """
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "a99c0de9-9999-4999-8999-999999999999",
                        start: "2031-12-10T10:30:00", end: "2031-12-10T11:30:00", allDay: false,
                        repeating: { type: WEEKLY, interval: 1, count: 5, exceptions: [] } }
                    ],
                    candidates: { ids: ["%s"] }%s
                  }) { status }
                }
                """;
        List<Map<String, Object>> blocked = tester.document(draft.formatted(ROOM_ERWIN, ""))
                .execute().path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        assertEquals("CONFLICT", blocked.get(0).get("status"), "stored series must block without ignore");

        List<Map<String, Object>> ignored = tester.document(draft.formatted(ROOM_ERWIN,
                        ",\n                    ignoreReservationIds: [\"" + storedId + "\"]"))
                .execute().path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        assertEquals("AVAILABLE", ignored.get(0).get("status"), "self-ignore must lift the series clash");
    }

    /**
     * allDay normalization parity with the mutation path: an allDay draft
     * with off-midnight times must be widened to the whole day (setWholeDays)
     * — the save would treat it that way, availability must agree.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allDayDraftIsNormalizedToWholeDay()
    {
        createBusyReservation(ROOM_A66, "2032-01-07T18:00:00", "2032-01-07T19:00:00");

        List<Map<String, Object>> rows = tester.document("""
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "aaac0dea-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        start: "2032-01-07T09:00:00", end: "2032-01-07T10:00:00", allDay: true }
                    ],
                    candidates: { ids: ["%s"] }
                  }) { status }
                }
                """.formatted(ROOM_A66))
                .execute()
                .path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        assertEquals(1, rows.size());
        assertEquals("CONFLICT", rows.get(0).get("status"),
                "allDay draft must cover the whole day (mutation-path normalization parity)");
    }

    // ============================================================ PRD 091 Phase 4.1 — occurrence preview

    /**
     * expandOccurrences = the recurrence editor's preview list (server-owned
     * expansion — the MONTHLY semantic and exception-skip rules never get a
     * client-side reimplementation). Weekly ×4 with one exception: 4 rows in
     * order, the excepted one flagged (not dropped — the preview strikes it
     * through).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void expandOccurrencesListsSeriesWithExceptionFlag()
    {
        List<Map<String, Object>> rows = tester.document("""
                query {
                  expandOccurrences(appointment: {
                    id: "abbc0deb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                    start: "2032-02-03T10:00:00", end: "2032-02-03T12:00:00", allDay: false,
                    repeating: { type: WEEKLY, interval: 1, count: 4, exceptions: ["2032-02-17"] }
                  }) { start end exception }
                }
                """)
                .execute()
                .path("expandOccurrences")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        assertEquals(4, rows.size(), () -> "4 occurrences expected; got " + rows);
        assertEquals("2032-02-03T10:00:00", rows.get(0).get("start"));
        assertEquals("2032-02-03T12:00:00", rows.get(0).get("end"));
        assertEquals(false, rows.get(0).get("exception"));
        assertEquals("2032-02-17T10:00:00", rows.get(2).get("start"));
        assertEquals(true, rows.get(2).get("exception"), "excepted occurrence flagged, not dropped");
        assertEquals("2032-02-24T10:00:00", rows.get(3).get("start"));
    }

    /** Endless series: the preview is capped by `limit` and terminates. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void expandOccurrencesCapsEndlessSeries()
    {
        List<Map<String, Object>> rows = tester.document("""
                query {
                  expandOccurrences(appointment: {
                    id: "accc0dec-cccc-4ccc-8ccc-cccccccccccc",
                    start: "2032-03-01T08:00:00", end: "2032-03-01T09:00:00", allDay: false,
                    repeating: { type: DAILY, interval: 1, exceptions: [] }
                  }, limit: 5) { start }
                }
                """)
                .execute()
                .path("expandOccurrences")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        assertEquals(5, rows.size());
        assertEquals("2032-03-05T08:00:00", rows.get(4).get("start"));
    }

    @Test
    @WithAnonymousUser
    void anonymousExpandOccurrencesRejected()
    {
        tester.document("""
                query {
                  expandOccurrences(appointment: {
                    id: "addc0ded-dddd-4ddd-8ddd-dddddddddddd",
                    start: "2032-03-01T08:00:00", end: "2032-03-01T09:00:00", allDay: false
                  }) { start }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous expandOccurrences must error");
                    assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                            () -> "expected UNAUTHENTICATED; got " + errs);
                });
    }

    // ============================================================ D3 contract

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void missingAppointmentIdRejectedLoudly()
    {
        tester.document("""
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { start: "2031-07-01T09:00:00", end: "2031-07-01T17:00:00", allDay: false }
                    ],
                    candidates: { filter: { typeIn: [room] } }
                  }) { status }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "missing appointment id must be a loud ValidationError (D3)");
                    String joined = errs.toString();
                    assertTrue(joined.contains("REQUIRED") || joined.toLowerCase().contains("id"),
                            () -> "expected id-required error; got " + joined);
                });
    }

    // ============================================================ §12

    @Test
    @WithAnonymousUser
    void anonymousAvailabilityRejected()
    {
        tester.document("""
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "a33c0de3-3333-4333-8333-333333333333", start: "2031-07-01T09:00:00", end: "2031-07-01T17:00:00", allDay: false }
                    ],
                    candidates: { ids: ["%s"] }
                  }) { status }
                }
                """.formatted(ROOM_A66))
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous availability query must error");
                    assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                            () -> "expected UNAUTHENTICATED; got " + errs);
                });
    }

    /**
     * §12 — nonexistent candidate ids must be silently dropped: the response
     * for [visible, nonexistent] must equal the response for [visible] alone
     * (hidden and nonexistent ids are indistinguishable).
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void nonexistentCandidateIdsDroppedSilently()
    {
        String draft = """
                query {
                  resourceAvailability(input: {
                    appointments: [
                      { id: "a44c0de4-4444-4444-8444-444444444444", start: "2031-07-07T09:00:00", end: "2031-07-07T17:00:00", allDay: false }
                    ],
                    candidates: { ids: [%s] }
                  }) {
                    allocatable { id }
                    status
                    conflictingAppointmentIds
                  }
                }
                """;
        List<Map<String, Object>> mixed = tester.document(draft.formatted(
                        "\"" + ROOM_ERWIN + "\", \"00000000-0000-4000-8000-00000000dead\""))
                .execute()
                .path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        List<Map<String, Object>> visibleOnly = tester.document(draft.formatted(
                        "\"" + ROOM_ERWIN + "\""))
                .execute()
                .path("resourceAvailability")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(visibleOnly, mixed,
                "response with nonexistent id mixed in must be identical to visible-only response (§12)");
    }
}
