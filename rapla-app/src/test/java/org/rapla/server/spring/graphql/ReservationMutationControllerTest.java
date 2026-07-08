package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 056 — tier-3 tests for the events mutation surface. Mirrors the
 * {@link ClassificationGraphQLControllerTest} setup: testdefault.xml fixture
 * via @TempDir, MockMvc → HttpGraphQlTester, security filter chain bypassed
 * so @WithMockUser actually reaches the resolver.
 *
 * <p>Fixture relevant facts:
 * <ul>
 *   <li>DynamicTypes include `event` (reservation type, attrs name/belongsto/description).</li>
 *   <li>Allocatables: "Room A66" + "erwin" (both type `room`).</li>
 *   <li>Users: homer (admin), monty (non-admin).</li>
 * </ul>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationMutationControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationMutationControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    HotSwappableGraphQlSource graphQlSource;

    @Autowired
    org.rapla.storage.CachableStorageOperator operator;

    @Autowired
    org.rapla.facade.RaplaFacade facade;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate())
                .url("/api/graphql")
                .build();
    }

    // ============================================================ schema-load smoke

    /**
     * Smoke test — the static mutation surface (createReservation, applyChanges,
     * etc.) appears in the schema and parses cleanly. Catches schema-level
     * regressions like missing scalars or input type wiring problems.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaIncludesMutationSurface()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "Mutation") {
                    fields { name }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("createReservation"),    () -> "missing createReservation in " + names);
        assertTrue(names.contains("updateReservation"),    () -> "missing updateReservation in " + names);
        assertTrue(names.contains("deleteReservations"),   () -> "missing deleteReservations in " + names);
        assertTrue(names.contains("changeReservationOwner"), () -> "missing changeReservationOwner in " + names);
        assertTrue(names.contains("moveReservations"),     () -> "missing moveReservations in " + names);
        assertTrue(names.contains("copyReservations"),     () -> "missing copyReservations in " + names);
        assertTrue(names.contains("applyChanges"),         () -> "missing applyChanges in " + names);
    }

    /**
     * The @oneOf ChangeOp surfaces in introspection. PRD 056 + spec'd
     * @oneOf semantics give us the discriminated-union shape for free.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void changeOpIsIntrospectable()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "ChangeOp") {
                    name kind
                    inputFields { name type { name kind ofType { name } } }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("INPUT_OBJECT", result.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputFields = (List<Map<String, Object>>) result.get("inputFields");
        List<String> fieldNames = inputFields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(fieldNames.contains("createReservation"), () -> "missing createReservation variant in " + fieldNames);
        assertTrue(fieldNames.contains("updateReservation"), () -> "missing updateReservation variant in " + fieldNames);
        assertTrue(fieldNames.contains("deleteReservation"), () -> "missing deleteReservation variant in " + fieldNames);
    }

    /**
     * The generated input types (per-DynamicType ClassificationInput + the
     * @oneOf wrappers) appear in the schema after the symmetric β² generator
     * extension. testdefault.xml fixture's `event` DynamicType generates an
     * `eventClassificationInput`.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void generatedClassificationInputsAreIntrospectable()
    {
        // eventClassificationInput exists for the testdefault `event` reservation type
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "eventClassificationInput") {
                    kind
                    inputFields { name }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(result, "expected eventClassificationInput type to be generated");
        assertEquals("INPUT_OBJECT", result.get("kind"));
    }

    /**
     * ReservationClassificationInput @oneOf wrapper has the per-DynamicType
     * variant set. For testdefault.xml the only reservation type is `event`.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationClassificationInputHasOneOfVariants()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "ReservationClassificationInput") {
                    kind
                    isOneOf
                    inputFields { name }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("INPUT_OBJECT", result.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> variants = (List<Map<String, Object>>) result.get("inputFields");
        List<String> variantNames = variants.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(variantNames.contains("event"),
                () -> "expected 'event' variant on ReservationClassificationInput, got " + variantNames);
    }

    // ============================================================ §12 leak tests

    /**
     * AGENTS.md §12 — anonymous callers cannot mutate. Server returns
     * PERMISSION_DENIED via the typed error code (not a raw 401), so the
     * SPA can map it cleanly.
     */
    @Test
    @WithAnonymousUser
    void anonymousCreateReservationRejected()
    {
        // Valid @oneOf shape so engine validation passes; the auth check
        // fires inside the resolver. Empty appointments would also be REQUIRED
        // but PERMISSION_DENIED hits first at the resolver entry point.
        tester.document("""
                mutation {
                  createReservation(input: {
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { start: "2026-06-01T10:00:00", end: "2026-06-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "expected anonymous create to error");
                    String joined = errs.toString();
                    assertTrue(joined.contains("PERMISSION_DENIED") || joined.contains("authenticated")
                                    || joined.toLowerCase().contains("permission"),
                            () -> "expected PERMISSION_DENIED-style error; got " + joined);
                });
    }

    // ============================================================ basic create flow

    /**
     * Happy-path create with the minimum required fields. Validates the
     * end-to-end wire: input parsing, classification building, dispatch
     * to operator, response shape.
     *
     * Note: this is a thin smoke test that the mutation runs without
     * blowing up at the boundary. Comprehensive create-with-all-attributes
     * coverage lives in the broader mutation suite as PRD 056 lands.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createMinimalReservationDispatches()
    {
        // Issue a minimal valid mutation. Empty appointments is rejected by
        // OQ1.d validation; verify that path.
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "e3333333-3333-4333-8333-333333333333",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [],
                    allocations: []
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "expected REQUIRED error for empty appointments");
                    String joined = errs.toString();
                    assertTrue(joined.contains("REQUIRED") || joined.contains("appointments"),
                            () -> "expected REQUIRED-on-appointments error; got " + joined);
                });
    }

    // ============================================================ PRD 056 §9 — mandatory client ids

    /**
     * PRD 056 §9 (decided 2026-07-06): create verbs REQUIRE a client-supplied
     * id — the server no longer generates one on absence. Rationale: revised
     * OQ5 idempotency (retry maps ID_COLLISION on own id to "already applied")
     * only works when the client mints the id; follows the CalDAV/RFC 5545
     * model (client-generated UID, PUT If-None-Match).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createReservationWithoutIdRejected()
    {
        tester.document("""
                mutation {
                  createReservation(input: {
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a1111111-1111-4111-8111-111111111111",
                        start: "2030-09-01T10:00:00", end: "2030-09-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "create without reservation id must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("REQUIRED") && joined.contains("id"),
                            () -> "expected REQUIRED-on-id error; got " + joined);
                });
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createReservationAppointmentWithoutIdRejected()
    {
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "e1111111-1111-4111-8111-111111111111",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { start: "2030-09-02T10:00:00", end: "2030-09-02T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "appointment without id must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("REQUIRED") && joined.contains("id"),
                            () -> "expected REQUIRED-on-appointment-id error; got " + joined);
                });
    }

    /**
     * PRD 056 §9 check #1 + revised OQ5: a create whose id already resolves to
     * a persistent entity → {@code ID_COLLISION}, no content comparison. This
     * is the retry contract: a client re-sending a lost-response create maps a
     * collision on its own id to "already applied".
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createReservationWithExistingIdReturnsIdCollision()
    {
        String document = """
                mutation {
                  createReservation(input: {
                    id: "e8888888-8888-4888-8888-888888888888",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a8888888-8888-4888-8888-888888888888",
                        start: "2030-09-04T10:00:00", end: "2030-09-04T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """;
        String createdId = tester.document(document)
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();
        assertEquals("e8888888-8888-4888-8888-888888888888", createdId);

        // Retry (same document, same id) — must fail loudly with ID_COLLISION,
        // must NOT silently overwrite.
        tester.document(document)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "create with an existing id must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("ID_COLLISION"),
                            () -> "expected ID_COLLISION error; got " + joined);
                });
    }

    /**
     * checkIdIntegrity check #2 fallout: {@code clone()} keeps appointment ids
     * (edit pattern), so a server-side copy must mint FRESH appointment ids —
     * otherwise the copy's appointments still carry the source reservation's
     * appointment ids and the dispatch guard rejects the store.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void copyReservationsMintsFreshAppointmentIds()
    {
        String sourceApptId = "a2222222-2222-4222-8222-222222222222";
        String sourceId = tester.document("""
                mutation {
                  createReservation(input: {
                    id: "e2222222-2222-4222-8222-222222222222",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "%s", start: "2030-09-03T10:00:00", end: "2030-09-03T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """.formatted(sourceApptId))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();
        assertNotNull(sourceId);

        Map<String, Object> copyResult = tester.document("""
                mutation ($ids: [ID!]!) {
                  copyReservations(ids: $ids, dateShift: "PT24H") {
                    overallStatus
                    results { reservation { id appointments { id } } }
                  }
                }
                """)
                .variable("ids", List.of(sourceId))
                .execute()
                .path("copyReservations")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("SUCCESS", copyResult.get("overallStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) copyResult.get("results");
        @SuppressWarnings("unchecked")
        Map<String, Object> copied = (Map<String, Object>) results.get(0).get("reservation");
        assertNotEquals(sourceId, copied.get("id"), "copy must have a fresh reservation id");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> copiedAppts = (List<Map<String, Object>>) copied.get("appointments");
        assertEquals(1, copiedAppts.size());
        assertNotEquals(sourceApptId, copiedAppts.get(0).get("id"),
                "copied appointment must have a fresh id (source id would trip checkIdIntegrity #2)");
    }

    // ============================================================ happy-path create + read-back

    /**
     * PRD 056 happy-path: a successful createReservation actually stores
     * the reservation, and a subsequent read returns the same content.
     * Closes the "no end-to-end create-then-read coverage" gap noted in
     * the PRD 056 follow-up review (2026-05-29).
     *
     * <p>Verifies: typeKey round-trip, owner = caller, appointments stored
     * in input order with assigned ids, allocation lists round-trip.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createReservationStoresAndReadsBack()
    {
        // Room A66 + erwin from the testdefault.xml fixture
        String roomA66 = "c24ce517-4697-4e52-9917-ec000c84563c";
        String erwin   = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";

        // Create with one appointment + one unrestricted allocation
        String createdId = tester.document("""
                mutation {
                  createReservation(input: {
                    id: "e4444444-4444-4444-8444-444444444444",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a4444444-4444-4444-8444-444444444444",
                        start: "2030-06-01T10:00:00", end: "2030-06-01T11:00:00", allDay: false }
                    ],
                    allocations: [
                      { allocatableId: "%s" }
                    ]
                  }) { id }
                }
                """.formatted(roomA66))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();
        assertNotNull(createdId, "createReservation must return the stored id");
        assertFalse(createdId.isBlank(), "stored id must be non-blank");

        // Read it back; verify the round-trip
        Map<String, Object> roundTrip = tester.document("""
                query ($id: ID!) {
                  reservation(id: $id) {
                    id
                    classification { typeKey type { id key } }
                    owner { username }
                    appointments { start end }
                    allocations { allocatable { id } appointmentIds }
                  }
                }
                """)
                .variable("id", createdId)
                .execute()
                .path("reservation")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(roundTrip, "read-back must succeed");
        assertEquals(createdId, roundTrip.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> classification = (Map<String, Object>) roundTrip.get("classification");
        // typeKey is the sole identifier surfaced on Classification (PRD 035 §11
        // locked 2026-05-29 — typeId dropped; `type { id }` available for the
        // rare UUID consumer).
        assertEquals("event", classification.get("typeKey"), "typeKey round-trip");
        @SuppressWarnings("unchecked")
        Map<String, Object> typeObj = (Map<String, Object>) classification.get("type");
        assertNotNull(typeObj.get("id"), "type.id (DynamicType UUID) must populate");
        assertEquals("event", typeObj.get("key"), "type.key must equal typeKey");
        @SuppressWarnings("unchecked")
        Map<String, Object> owner = (Map<String, Object>) roundTrip.get("owner");
        assertEquals("homer", owner.get("username"), "owner must be the caller (homer)");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> appts = (List<Map<String, Object>>) roundTrip.get("appointments");
        assertEquals(1, appts.size(), "one appointment");
        assertEquals("2030-06-01T10:00:00", appts.get(0).get("start"));
        assertEquals("2030-06-01T11:00:00", appts.get(0).get("end"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allocs = (List<Map<String, Object>>) roundTrip.get("allocations");
        assertEquals(1, allocs.size(), "one allocation");
        @SuppressWarnings("unchecked")
        Map<String, Object> alloc0 = (Map<String, Object>) allocs.get(0).get("allocatable");
        assertEquals(roomA66, alloc0.get("id"));
        // Unrestricted → appointmentIds null per Q4 lock
        assertEquals(null, allocs.get(0).get("appointmentIds"),
                "unrestricted allocation must serialize as null appointmentIds");
    }

    // ============================================================ restriction round-trip (PRD 055 workhorse)

    /**
     * PRD 055 §"Tests (tier-3 MockMvc spec)" — the workhorse assertion.
     * A reservation with a partial restriction (one allocatable bound to
     * all appointments, another bound to only the first) must round-trip
     * cleanly through BOTH read shapes:
     *   - Reservation.allocations (editor shape — appointmentIds per
     *     allocatable, preserving the restriction)
     *   - Appointment.allocatables (listview shape — pre-resolved per
     *     appointment, restriction APPLIED)
     *
     * <p>testdefault.xml has no restriction fixtures, so the test creates
     * one via the createReservation mutation. This kills two birds:
     * exercises the mutation's restriction-handling code path AND verifies
     * the read-side resolver's restriction-aware join.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void restrictionRoundTripBothReadShapes()
    {
        String roomA66 = "c24ce517-4697-4e52-9917-ec000c84563c";
        String erwin   = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";

        // Client-supplied appointment ids so we can reference them in
        // the restriction. The server uses the input ids when present.
        String appt1Id = "11111111-1111-1111-1111-111111111111";
        String appt2Id = "22222222-2222-2222-2222-222222222222";

        // Room A66 bound to ALL appointments (no appointmentIds → unrestricted)
        // erwin    bound to ONLY appt1 (appointmentIds: [appt1Id])
        String createdId = tester.document("""
                mutation {
                  createReservation(input: {
                    id: "e5555555-5555-4555-8555-555555555555",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "%s", start: "2030-07-01T10:00:00", end: "2030-07-01T11:00:00", allDay: false },
                      { id: "%s", start: "2030-07-02T10:00:00", end: "2030-07-02T11:00:00", allDay: false }
                    ],
                    allocations: [
                      { allocatableId: "%s" },
                      { allocatableId: "%s", appointmentIds: ["%s"] }
                    ]
                  }) { id }
                }
                """.formatted(appt1Id, appt2Id, roomA66, erwin, appt1Id))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();
        assertNotNull(createdId);

        // Read back: editor view (Reservation.allocations) must preserve restriction
        Map<String, Object> rt = tester.document("""
                query ($id: ID!) {
                  reservation(id: $id) {
                    allocations { allocatable { id } appointmentIds }
                    appointments { id allocatables { id } }
                  }
                }
                """)
                .variable("id", createdId)
                .execute()
                .path("reservation")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) rt.get("allocations");
        assertEquals(2, allocations.size(), "expected 2 allocations");
        Map<String, Object> roomAlloc = allocations.stream()
                .filter(a -> roomA66.equals(((Map<?, ?>) a.get("allocatable")).get("id")))
                .findFirst().orElseThrow();
        Map<String, Object> erwinAlloc = allocations.stream()
                .filter(a -> erwin.equals(((Map<?, ?>) a.get("allocatable")).get("id")))
                .findFirst().orElseThrow();
        assertEquals(null, roomAlloc.get("appointmentIds"),
                "Room A66 must be unrestricted (null appointmentIds)");
        @SuppressWarnings("unchecked")
        List<String> erwinRestriction = (List<String>) erwinAlloc.get("appointmentIds");
        assertEquals(List.of(appt1Id), erwinRestriction,
                "erwin must be restricted to appt1 only");

        // Listview view (Appointment.allocatables) — restriction APPLIED per-appointment
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> appts = (List<Map<String, Object>>) rt.get("appointments");
        assertEquals(2, appts.size(), "expected 2 appointments");
        Map<String, Object> a1 = appts.stream()
                .filter(a -> appt1Id.equals(a.get("id"))).findFirst().orElseThrow();
        Map<String, Object> a2 = appts.stream()
                .filter(a -> appt2Id.equals(a.get("id"))).findFirst().orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> a1Allocs = (List<Map<String, Object>>) a1.get("allocatables");
        List<String> a1Ids = a1Allocs.stream().map(m -> (String) m.get("id")).toList();
        assertTrue(a1Ids.contains(roomA66), "appt1 must include Room A66 (unrestricted)");
        assertTrue(a1Ids.contains(erwin),   "appt1 must include erwin (restricted to it)");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> a2Allocs = (List<Map<String, Object>>) a2.get("allocatables");
        List<String> a2Ids = a2Allocs.stream().map(m -> (String) m.get("id")).toList();
        assertTrue(a2Ids.contains(roomA66), "appt2 must include Room A66 (unrestricted)");
        assertFalse(a2Ids.contains(erwin),
                "appt2 must NOT include erwin (restricted to appt1 only) — got " + a2Ids);
    }

    // ============================================================ hot-swap interaction

    /**
     * After a DynamicType change is dispatched, the schema rebuilder
     * regenerates the typed inputs within ~10s. This test verifies the
     * generated `event` typed input exists (which would only be true after
     * the schema rebuild) and the mutation surface is consistent post-swap.
     *
     * <p>Full hot-swap test (admin dispatches a DynamicType change in-test
     * + waits for schema rebuild + verifies new variant appears) requires
     * either tightening the poll cadence for tests OR using a poll-on-demand
     * trigger; deferred to a follow-up tier-3 suite once the schema editor
     * lands (PRD 057).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void hotSwapKeepsMutationSurfaceConsistent()
    {
        // After initial schema build, the event variant must be in the @oneOf wrapper
        Map<String, Object> first = tester.document("""
                { __type(name: "ReservationClassificationInput") { inputFields { name } } }
                """)
                .execute()
                .path("__type.inputFields")
                .entity(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get()
                .stream()
                .filter(f -> "event".equals(f.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("event variant missing on ReservationClassificationInput"));
        assertEquals("event", first.get("name"));
    }

    // ============================================================ expectedLastChanged echo contract

    /**
     * Regression (2026-07-07, false "zwischenzeitlich geändert" bug): the SPA
     * reads {@code lastModifiedAt} (DateTime, offset + MILLISECOND fraction),
     * strips ONLY the offset and echoes the rest as expectedLastChanged. The
     * server compares with LocalDateTime.equals(), so the fraction must
     * round-trip — a client that also strips the fraction gets a spurious
     * CONCURRENT_MODIFICATION on every save of a persisted entity.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateWithOffsetStrippedLastModifiedAtPassesConcurrencyCheck()
    {
        String reservationId = "e9999999-9999-4999-8999-999999999999";
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a9999999-9999-4999-8999-999999999999",
                        start: "2030-06-01T10:00:00", end: "2030-06-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """.formatted(reservationId))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        String lastModifiedAt = tester.document("""
                query ($id: ID!) { reservation(id: $id) { lastModifiedAt } }
                """)
                .variable("id", reservationId)
                .execute()
                .path("reservation.lastModifiedAt")
                .entity(String.class)
                .get();
        // the exact SPA transform: strip the offset, KEEP the fraction
        String expected = lastModifiedAt.replaceAll("(Z|[+-]\\d{2}:\\d{2})$", "");

        tester.document("""
                mutation ($id: ID!, $expected: LocalDateTime) {
                  updateReservation(id: $id, input: {
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a9999999-9999-4999-8999-999999999999",
                        start: "2030-06-01T10:00:00", end: "2030-06-01T12:00:00", allDay: false }
                    ],
                    allocations: []
                  }, expectedLastChanged: $expected) { id }
                }
                """)
                .variable("id", reservationId)
                .variable("expected", expected)
                .execute()
                .errors()
                .satisfy(errs -> assertTrue(errs.isEmpty(),
                        () -> "offset-stripped echo must pass the concurrency check; got " + errs));
    }

    // ============================================================ type change (PRD 056 OQ1.c revised 2026-07-07)

    /**
     * PRD 096 / PRD 056 OQ1.c revision (2026-07-07): updateReservation ACCEPTS
     * a type change when the classification @oneOf variant matches the new
     * typeKey. The attribute-remap preview happens client-side in the SPA
     * classification editor; the server just validates + stores the new shape.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateReservationChangesType()
    {
        createMeetingTypeAndRebuildSchema();

        String reservationId = "e6666666-6666-4666-8666-666666666666";
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a6666666-6666-4666-8666-666666666666",
                        start: "2030-06-01T10:00:00", end: "2030-06-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """.formatted(reservationId))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        Map<String, Object> updated = tester.document("""
                mutation ($id: ID!) {
                  updateReservation(id: $id, input: {
                    typeKey: "meeting",
                    classification: { meeting: { name: "Planning sync", topic: "Q3" } },
                    appointments: [
                      { id: "a6666666-6666-4666-8666-666666666666",
                        start: "2030-06-01T10:00:00", end: "2030-06-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) {
                    classification {
                      typeKey
                      ... on meetingClassification { topic }
                    }
                  }
                }
                """)
                .variable("id", reservationId)
                .execute()
                .path("updateReservation.classification")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("meeting", updated.get("typeKey"), "typeKey must switch to the new type");
        assertEquals("Q3", updated.get("topic"), "new-type attribute values must persist");

        // read back — the change is stored, not just echoed
        String storedTypeKey = tester.document("""
                query ($id: ID!) { reservation(id: $id) { classification { typeKey } } }
                """)
                .variable("id", reservationId)
                .execute()
                .path("reservation.classification.typeKey")
                .entity(String.class)
                .get();
        assertEquals("meeting", storedTypeKey, "type change must survive the read-back");
    }

    /** Cross-validation stays: new typeKey with the OLD @oneOf variant is rejected. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateReservationTypeChangeWithMismatchedVariantRejected()
    {
        createMeetingTypeAndRebuildSchema();

        String reservationId = "e7777777-7777-4777-8777-777777777777";
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a7777777-7777-4777-8777-777777777777",
                        start: "2030-06-01T10:00:00", end: "2030-06-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """.formatted(reservationId))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        tester.document("""
                mutation ($id: ID!) {
                  updateReservation(id: $id, input: {
                    typeKey: "meeting",
                    classification: { event: {} },
                    appointments: [
                      { start: "2030-06-01T10:00:00", end: "2030-06-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """)
                .variable("id", reservationId)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "mismatched @oneOf variant must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("MISMATCHED_TYPE"),
                            () -> "expected MISMATCHED_TYPE, got " + joined);
                });
    }

    /**
     * The fixture ships only ONE reservation type (event) — a type change
     * needs a second. saveDynamicType + poll-on-demand rebuild (the 10s
     * {@link GraphQlSchemaRebuilder} cadence is too slow for a test).
     * Create-if-absent: the test methods share one app context + data file,
     * and an id-less saveDynamicType on an existing key is KEY_COLLISION.
     */
    // ============================================================ PRD 099 Phase 1 — permission create-seed

    /**
     * PRD 099 Phase 1 — Swing parity: {@code FacadeImpl.newReservation} copies
     * the type's permissions onto every new reservation
     * ({@code PermissionContainer.Util.copyPermissions} — all but CREATE and
     * READ_TYPE). The GraphQL create must do the same, or SPA-created events
     * are invisible to third parties that could read a Swing-created sibling.
     * Fixture: type `event` carries read_type + read + create → exactly the
     * plain READ permission must arrive on the stored entity.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createReservationCopiesTypePermissions()
    {
        String reservationId = "e1010101-0101-4101-8101-010101010101";
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { id: "a1010101-0101-4101-8101-010101010101",
                        start: "2030-07-01T10:00:00", end: "2030-07-01T11:00:00", allDay: false }
                    ],
                    allocations: []
                  }) { id }
                }
                """.formatted(reservationId))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        org.rapla.entities.domain.Reservation stored = operator.tryResolve(
                new org.rapla.entities.storage.ReferenceInfo<>(reservationId,
                        org.rapla.entities.domain.Reservation.class));
        assertNotNull(stored, "created reservation must be resolvable");
        java.util.Collection<org.rapla.entities.domain.Permission> perms = stored.getPermissionList();
        assertTrue(perms.stream().anyMatch(
                        p -> p.getAccessLevel() == org.rapla.entities.domain.Permission.READ),
                () -> "type permission READ must be copied onto the new reservation (Swing parity); got " + perms);
        assertTrue(perms.stream().noneMatch(
                        p -> p.getAccessLevel() == org.rapla.entities.domain.Permission.CREATE
                                || p.getAccessLevel() == org.rapla.entities.domain.Permission.READ_TYPE),
                () -> "CREATE/READ_TYPE are type-level only and must NOT be copied; got " + perms);
    }

    // ============================================================ PRD 099 Phase 2 — explicit null clears

    /**
     * PRD 099 Phase 2 — input null-semantics. `buildClassificationFromInput`
     * rebuilds from `newClassification()` (defaults prefilled) on EVERY
     * create/update; the old `if (value != null)` guard swallowed explicit
     * nulls, so a cleared defaulted attribute resurrected on each save.
     * Contract: key present with null = clear; key OMITTED = default applies.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void explicitNullClearsDefaultedAttributeOmittedKeyGetsDefault() throws Exception
    {
        createMeetingTypeAndRebuildSchema();
        setTopicDefault("auto-topic");

        String reservationId = "e2020202-0202-4202-8202-020202020202";
        String appointment = """
                { id: "a2020202-0202-4202-8202-020202020202",
                  start: "2030-07-02T10:00:00", end: "2030-07-02T11:00:00", allDay: false }
                """;
        // Create WITHOUT topic — the omitted key gets the type default.
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "meeting",
                    classification: { meeting: { name: "Nulling" } },
                    appointments: [%s],
                    allocations: []
                  }) { id }
                }
                """.formatted(reservationId, appointment))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();
        assertEquals("auto-topic", storedTopic(reservationId),
                "omitted key on create → type default applies");

        // Update with EXPLICIT topic: null — must clear and STAY cleared.
        tester.document("""
                mutation ($id: ID!) {
                  updateReservation(id: $id, input: {
                    typeKey: "meeting",
                    classification: { meeting: { name: "Nulling", topic: null } },
                    appointments: [%s],
                    allocations: []
                  }) { id }
                }
                """.formatted(appointment))
                .variable("id", reservationId)
                .execute()
                .path("updateReservation.id")
                .entity(String.class)
                .get();
        assertEquals(null, storedTopic(reservationId),
                "explicit null must clear the attribute — the type default may not resurrect it");

        // Update OMITTING topic — replace semantics: the default applies again.
        tester.document("""
                mutation ($id: ID!) {
                  updateReservation(id: $id, input: {
                    typeKey: "meeting",
                    classification: { meeting: { name: "Nulling" } },
                    appointments: [%s],
                    allocations: []
                  }) { id }
                }
                """.formatted(appointment))
                .variable("id", reservationId)
                .execute()
                .path("updateReservation.id")
                .entity(String.class)
                .get();
        assertEquals("auto-topic", storedTopic(reservationId),
                "omitted key on update → replace semantics, default applies");
    }

    // ============================================================ PRD 099 Phase 3 — reservationPrototype

    /**
     * PRD 099 Phase 3 — the prototype query returns the birth state of a new
     * reservation: `newClassification()` executed server-side, defaults
     * prefilled, nothing persisted. Same code path the create runs → preview
     * and persisted result can never diverge.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationPrototypeReturnsTypeDefaults() throws Exception
    {
        createMeetingTypeAndRebuildSchema();
        setTopicDefault("auto-topic");

        Map<String, Object> cls = tester.document("""
                {
                  reservationPrototype(typeKey: "meeting") {
                    typeKey
                    classification {
                      typeKey
                      ... on meetingClassification { name topic }
                    }
                  }
                }
                """)
                .execute()
                .path("reservationPrototype.classification")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("meeting", cls.get("typeKey"));
        assertEquals("auto-topic", cls.get("topic"), "type default must be prefilled");
        assertEquals(null, cls.get("name"), "attributes without default stay null");
    }

    /**
     * §12 — the prototype must not leak type existence: a type the caller
     * cannot CREATE answers exactly like a type that does not exist.
     */
    @Test
    @WithMockUser(username = "monty")
    void reservationPrototypeNonCreatableAnswersLikeUnknown()
    {
        String forRestricted = prototypeErrors("room");          // ALLOCATABLE type — never creatable as reservation
        String forUnknown = prototypeErrors("doesnotexist1234");
        assertEquals(forUnknown.replace("doesnotexist1234", "X"),
                forRestricted.replace("room", "X"),
                "non-creatable and unknown typeKey must answer identically (existence non-leak)");
    }

    private String prototypeErrors(String typeKey)
    {
        StringBuilder out = new StringBuilder();
        tester.document("""
                query ($k: String!) { reservationPrototype(typeKey: $k) { typeKey } }
                """)
                .variable("k", typeKey)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "expected an error for typeKey " + typeKey);
                    errs.forEach(e -> out.append(e.getErrorType()).append('|').append(e.getMessage()).append('\n'));
                });
        return out.toString();
    }

    /**
     * PRD 099 ride-along (live bug 2026-07-08: "changing loan from geplant to
     * verliehen and saving does not work") — VALUE_LIST enum input. The @oneOf
     * variant field for a VALUE_LIST category attribute is the GENERATED enum;
     * its values are the leaf-child KEYS. `coerceSingleValue` resolved CATEGORY
     * input by ID only, so the enum key resolved to null and the value was
     * silently dropped on every create/update.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void valueListEnumInputResolvesToCategoryOnCreateAndUpdate()
    {
        // discover the generated enum for event.belongsto + two of its values
        Map<String, Object> typeInfo = tester.document("""
                { __type(name: "eventClassification") {
                    fields { name type { name kind } }
                } }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        String enumName = ((List<Map<String, Object>>) typeInfo.get("fields")).stream()
                .filter(f -> "belongsto".equals(f.get("name")))
                .map(f -> (String) ((Map<String, Object>) f.get("type")).get("name"))
                .findFirst().orElseThrow();
        List<String> values = tester.document("""
                query ($n: String!) { __type(name: $n) { enumValues { name } } }
                """)
                .variable("n", enumName)
                .execute()
                .path("__type.enumValues[*].name")
                .entityList(String.class)
                .get();
        assertTrue(values.size() >= 2, () -> "need two enum values, got " + values);
        String first = values.get(0);
        String second = values.get(1);

        String reservationId = "e3030303-0303-4303-8303-030303030303";
        String appointment = """
                { id: "a3030303-0303-4303-8303-030303030303",
                  start: "2030-07-03T10:00:00", end: "2030-07-03T11:00:00", allDay: false }
                """;
        Map<String, Object> created = tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: { belongsto: %s } },
                    appointments: [%s],
                    allocations: []
                  }) { classification { ... on eventClassification { belongsto } } }
                }
                """.formatted(reservationId, first, appointment))
                .execute()
                .path("createReservation.classification")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(first, created.get("belongsto"),
                "enum key must resolve to the category on create");

        Map<String, Object> updated = tester.document("""
                mutation ($id: ID!) {
                  updateReservation(id: $id, input: {
                    typeKey: "event",
                    classification: { event: { belongsto: %s } },
                    appointments: [%s],
                    allocations: []
                  }) { classification { ... on eventClassification { belongsto } } }
                }
                """.formatted(second, appointment))
                .variable("id", reservationId)
                .execute()
                .path("updateReservation.classification")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(second, updated.get("belongsto"),
                "changing the enum value and saving must persist the new category");
    }

    private void setTopicDefault(String value) throws Exception
    {
        org.rapla.entities.dynamictype.DynamicType dt = facade.getDynamicType("meeting");
        if (value.equals(dt.getAttribute("topic").defaultValue()))
        {
            return;
        }
        org.rapla.entities.dynamictype.DynamicType edit = facade.edit(dt);
        edit.getAttribute("topic").setDefaultValue(value);
        facade.store(edit);
    }

    private Object storedTopic(String reservationId)
    {
        org.rapla.entities.domain.Reservation stored = operator.tryResolve(
                new org.rapla.entities.storage.ReferenceInfo<>(reservationId,
                        org.rapla.entities.domain.Reservation.class));
        assertNotNull(stored, "reservation must be resolvable");
        org.rapla.entities.dynamictype.Classification c = stored.getClassification();
        return c.getValueForAttribute(c.getType().getAttribute("topic"));
    }

    private void createMeetingTypeAndRebuildSchema()
    {
        List<String> keys = tester.document("{ types { key } }")
                .execute()
                .path("types[*].key")
                .entityList(String.class)
                .get();
        if (keys.contains("meeting"))
        {
            return;
        }
        tester.document("""
                mutation {
                  saveDynamicType(input: {
                    key: "meeting",
                    name: { default: "Meeting" },
                    classificationType: RESERVATION,
                    attributes: [
                      { key: "name",  name: { default: "Name" },  valueType: STRING, multiplicity: SINGLE, required: true },
                      { key: "topic", name: { default: "Topic" }, valueType: STRING, multiplicity: SINGLE, required: false }
                    ]
                  }) { id }
                }
                """)
                .execute()
                .path("saveDynamicType.id")
                .entity(String.class)
                .get();
        graphQlSource.rebuild();
    }
}
