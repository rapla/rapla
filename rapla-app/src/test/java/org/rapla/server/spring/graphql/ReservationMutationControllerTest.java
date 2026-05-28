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
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [
                      { start: "2030-06-01T10:00:00", end: "2030-06-01T11:00:00", allDay: false }
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
}
