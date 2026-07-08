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
