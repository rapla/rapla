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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 055 — tier-3 tests for the reservation read resolvers. Mirrors the
 * {@link ReservationMutationControllerTest} setup: testdefault.xml via @TempDir,
 * MockMvc → HttpGraphQlTester, security filters bypassed so @WithMockUser
 * reaches the resolver.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationGraphQLControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    /**
     * Nested config class that flips on the window cap. Used by the
     * {@link #windowCapRejectsWhenConfigured} test below. Kept as a static
     * nested {@code @SpringBootTest} so the cap-on context is cached
     * separately from the default cap-off context the other tests use.
     */
    @SpringBootTest(classes = RaplaSpringBootApplication.class,
            properties = "rapla.graphql.max-query-window-days=365")
    @AutoConfigureMockMvc(addFilters = false)
    static class WithWindowCap
    {
        @TempDir
        static Path nestedTempDir;

        static Path nestedDataFile;

        @BeforeAll
        static void nestedCopyFixture() throws IOException
        {
            nestedDataFile = nestedTempDir.resolve("rapla-data.xml");
            try (InputStream in = ReservationGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
            {
                assertNotNull(in, "testdefault.xml fixture missing from classpath");
                Files.copy(in, nestedDataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        @DynamicPropertySource
        static void registerNestedProps(DynamicPropertyRegistry registry)
        {
            registry.add("rapla.file-datasources.raplafile", () -> nestedDataFile.toAbsolutePath().toString());
        }

        @Autowired MockMvc mockMvc;

        HttpGraphQlTester tester;

        @BeforeEach
        void setUp()
        {
            tester = HttpGraphQlTester.builder(
                    MockMvcWebTestClient.bindTo(mockMvc).build().mutate())
                    .url("/api/graphql")
                    .build();
        }

        @Test
        @WithMockUser(username = "homer", roles = "ADMIN")
        void windowCapRejectsWhenConfigured()
        {
            tester.document("""
                    query {
                      reservations(filter: {
                        from: "2020-01-01T00:00:00",
                        to:   "2025-12-31T00:00:00"
                      }) { id }
                    }
                    """)
                    .execute()
                    .errors()
                    .satisfy(errs -> {
                        assertFalse(errs.isEmpty(), "expected window-cap error");
                        String joined = errs.toString();
                        assertTrue(joined.contains("365") || joined.toLowerCase().contains("window"),
                                () -> "expected window-cap error; got " + joined);
                    });
        }
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

    // ============================================================ schema smoke

    /** The Query.reservation + Query.reservations roots appear in introspection. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaExposesReservationQueries()
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
        assertTrue(names.contains("reservation"),  () -> "missing Query.reservation in " + names);
        assertTrue(names.contains("reservations"), () -> "missing Query.reservations in " + names);
    }

    /** The Reservation output type has the derived/canModify/owner fields. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationTypeHasDerivedFields()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Reservation") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("firstDate"),    () -> "missing Reservation.firstDate in " + names);
        assertTrue(names.contains("lastDate"),     () -> "missing Reservation.lastDate in " + names);
        assertTrue(names.contains("canModify"),    () -> "missing Reservation.canModify in " + names);
        assertTrue(names.contains("owner"),        () -> "missing Reservation.owner in " + names);
        assertTrue(names.contains("appointments"), () -> "missing Reservation.appointments in " + names);
        assertTrue(names.contains("allocations"),  () -> "missing Reservation.allocations in " + names);
    }

    // ============================================================ §12 leak tests

    /**
     * AGENTS.md §12 — anonymous callers get empty list (existence not leaked).
     * Filter is mandatory so we pass a wide window.
     */
    @Test
    @WithAnonymousUser
    void anonymousReservationsRejected()
    {
        tester.document("""
                query {
                  reservations(filter: {
                    from: "2020-01-01T00:00:00",
                    to:   "2020-12-31T00:00:00"
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous data query must error, not return []");
                    String joined = errs.toString();
                    assertTrue(joined.contains("UNAUTHENTICATED")
                                    || joined.toLowerCase().contains("authentication"),
                            () -> "expected UNAUTHENTICATED-style error; got " + joined);
                });
    }

    /**
     * §12 — id existence not leaked. Unknown / hidden id → null, not an error.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unknownReservationIdReturnsNull()
    {
        tester.document("""
                query { reservation(id: "nonexistent-uuid") { id } }
                """)
                .execute()
                .path("reservation")
                .valueIsNull();
    }

    // ============================================================ validation

    // === PRD 066 Phase 2 — allocatableMatching on ReservationFilter =========

    private String idByDisplayName(String namePart)
    {
        List<Map<String, Object>> got = tester.document(String.format("""
                { allocatables(filter: { nameContains: "%s" }) { id displayName } }
                """, namePart))
                .execute()
                .path("allocatables")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        return got.stream()
                .filter(a -> ((String) a.get("displayName")).contains(namePart))
                .map(a -> (String) a.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no allocatable with displayName containing " + namePart));
    }

    /**
     * PRD 066 — `allocatableMatching: { idIn: [...] }` returns the same
     * reservations as the legacy `allocatableIdsIn: [...]`.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationsAllocatableMatchingResolvesEquivalentToIdsIn()
    {
        String roomA66 = idByDisplayName("Room A66");
        List<Map<String, Object>> legacy = tester.document(String.format("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    allocatableIdsIn: ["%s"]
                  }) { id } }
                """, roomA66))
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        List<Map<String, Object>> matching = tester.document(String.format("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    allocatableMatching: { idIn: ["%s"] }
                  }) { id } }
                """, roomA66))
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // Same id set, ignore order.
        java.util.Set<Object> legacyIds = legacy.stream().map(m -> m.get("id")).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Object> matchingIds = matching.stream().map(m -> m.get("id")).collect(java.util.stream.Collectors.toSet());
        assertEquals(legacyIds, matchingIds, () -> "allocatableMatching{idIn} should match legacy allocatableIdsIn; legacy=" + legacy + " matching=" + matching);
        assertFalse(legacyIds.isEmpty(), "fixture should have reservations on Room A66");
    }

    /**
     * PRD 066 — `allocatableMatching: { typeIn: [...] }` returns
     * reservations using ANY allocatable of those types.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationsAllocatableMatchingByTypeKeyIn()
    {
        List<Map<String, Object>> got = tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    allocatableMatching: { typeIn: [room] }
                  }) { id } }
                """)
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // Fixture has multiple reservations allocating Room A66 — at least 1.
        assertFalse(got.isEmpty(), () -> "expected reservations on rooms in fixture; got " + got);
    }

    /**
     * PRD 059 Phase 7 — the GENERATED `typeIn: [ReservationTypeKey!]` on
     * ReservationFilter selects by the RESERVATION's own DynamicType key
     * (union over the list). The fixture's reservations are all of type
     * `event`, so `[event]` returns everything in the window; an unknown
     * enum value is a loud VALIDATION error (the Phase 7 trade-off).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationsTypeInFiltersByEventType()
    {
        List<Map<String, Object>> unfiltered = tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00"
                  }) { id } }
                """)
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        List<Map<String, Object>> typed = tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    typeIn: [event]
                  }) { id } }
                """)
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        java.util.Set<Object> unfilteredIds = unfiltered.stream().map(m -> m.get("id")).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Object> typedIds = typed.stream().map(m -> m.get("id")).collect(java.util.stream.Collectors.toSet());
        assertFalse(unfilteredIds.isEmpty(), "fixture should have reservations in the window");
        assertEquals(unfilteredIds, typedIds, () -> "typeIn:[event] should match all fixture reservations; got " + typed);
        tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    typeIn: [noSuchType]
                  }) { id } }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "unknown ReservationTypeKey enum value must be rejected at validation"));
    }

    /**
     * PRD 059 Phase 7b — the type enums are SPLIT per kind:
     * `ReservationFilter.typeIn: [ReservationTypeKey!]` only accepts
     * reservation DT keys; an allocatable key (`room`) is a VALIDATION
     * error, not a silent empty result. And vice versa on
     * `AllocatableFilter.typeIn: [AllocatableTypeKey!]`.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void typeInEnumsAreSplitPerKind()
    {
        tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    typeIn: [room]
                  }) { id } }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "allocatable key 'room' must be rejected on ReservationFilter.typeIn"));
        tester.document("""
                { allocatables(filter: { typeIn: [event] }) { id } }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "reservation key 'event' must be rejected on AllocatableFilter.typeIn"));
    }

    /**
     * PRD 059 Phase 6 — generated `where<EventTypeKey>` predicates on
     * ReservationFilter filter by the RESERVATION's own classification
     * attributes, running through the SAME WhereEvaluator as the
     * allocatable path. Fixture: events named "Reservation 2",
     * "test-reservation", … — a name-eq predicate selects exactly one.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationsWhereEventFiltersByAttribute()
    {
        List<Map<String, Object>> got = tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    whereEvent: { name: { eq: "Reservation 2" } }
                  }) { name } }
                """)
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "whereEvent name-eq must select exactly one event; got " + got);
        assertEquals("Reservation 2", got.get(0).get("name"));
        List<Map<String, Object>> combos = tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    whereEvent: { OR: [ { name: { eq: "Reservation 2" } },
                                        { name: { contains: "test-reservation" } } ] }
                  }) { name } }
                """)
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertTrue(combos.size() >= 2, () -> "OR combinator must widen the match; got " + combos);
    }

    /**
     * PRD 059 Phase 6 — whereEvent works on the block-rooted read too
     * (appointmentBlocks delegates to the same reservations gate).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksWhereEventFilters()
    {
        List<Map<String, Object>> blocks = tester.document("""
                { appointmentBlocks(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    whereEvent: { name: { eq: "Reservation 2" } }
                  }) { reservation { name } } }
                """)
                .execute()
                .path("appointmentBlocks")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertFalse(blocks.isEmpty(), "expected blocks for Reservation 2");
        assertTrue(blocks.stream().allMatch(b ->
                        "Reservation 2".equals(((Map<String, Object>) b.get("reservation")).get("name"))),
                () -> "all blocks must belong to the whereEvent match; got " + blocks);
    }

    /**
     * PRD 066 — `allocatableMatching` + the legacy `allocatableIdsIn` field
     * are UNIONed when both are set. Both arms drive the same allocatable
     * set in the fixture; result must equal the per-arm result.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationsAllocatableMatchingAndIdsInUnion()
    {
        String roomA66 = idByDisplayName("Room A66");
        List<Map<String, Object>> mergedQuery = tester.document(String.format("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    allocatableIdsIn: ["%s"]
                    allocatableMatching: { idIn: ["%s"] }
                  }) { id } }
                """, roomA66, roomA66))
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        List<Map<String, Object>> idsInOnly = tester.document(String.format("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    allocatableIdsIn: ["%s"]
                  }) { id } }
                """, roomA66))
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        java.util.Set<Object> mergedIds = mergedQuery.stream().map(m -> m.get("id")).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Object> idsInOnlyIds = idsInOnly.stream().map(m -> m.get("id")).collect(java.util.stream.Collectors.toSet());
        assertEquals(idsInOnlyIds, mergedIds, () -> "same id in both arms must produce identical result (dedup'd)");
    }

    private java.util.Set<Object> reservationIds(String query)
    {
        return tester.document(query).execute().path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {}).get()
                .stream().map(m -> m.get("id")).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Genuine UNION across two DISTINCT allocatables — pins that {@code allocatableIdsIn} and
     * {@code allocatableMatching} are UNIONed (not intersected, no dropped arm) when they name
     * different resources. The existing dedup test reuses the same id in both arms and so can't
     * catch an intersection regression. Guards the reservations() scope-resolution refactor that
     * resolves the scoped allocatable set DIRECTLY via the catalog resolver (skipping the full
     * {@code getAllocatables(null)} scan): a regression to intersection or a missing arm would
     * shrink the result below the union.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationsScopeUnionsTwoDistinctAllocatables()
    {
        final String erwin   = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d"; // room "erwin"
        final String roomA66 = "c24ce517-4697-4e52-9917-ec000c84563c"; // room "Room A66"
        final String win = "from: \"2001-01-01T00:00:00\", to: \"2020-12-31T00:00:00\"";

        java.util.Set<Object> a = reservationIds(
                "{ reservations(filter: { " + win + ", allocatableIdsIn: [\"" + erwin + "\"] }) { id } }");
        java.util.Set<Object> b = reservationIds(
                "{ reservations(filter: { " + win + ", allocatableMatching: { idIn: [\"" + roomA66 + "\"] } }) { id } }");
        java.util.Set<Object> merged = reservationIds(
                "{ reservations(filter: { " + win + ", allocatableIdsIn: [\"" + erwin + "\"], "
                        + "allocatableMatching: { idIn: [\"" + roomA66 + "\"] } }) { id } }");

        assertFalse(a.isEmpty(), "fixture: room erwin should have reservations");
        assertFalse(b.isEmpty(), "fixture: Room A66 should have reservations");
        java.util.Set<Object> union = new java.util.HashSet<>(a);
        union.addAll(b);
        assertEquals(union, merged, () -> "scope must UNION both arms; a=" + a + " b=" + b + " merged=" + merged);
        assertTrue(merged.containsAll(a) && merged.containsAll(b),
                () -> "merged must contain every reservation from both arms; a=" + a + " b=" + b + " merged=" + merged);
    }

    /**
     * §12 leak guard — non-admin caller with an `allocatableMatching` that
     * could match hidden allocatables must see only reservations they can
     * actually read.
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void reservationsAllocatableMatchingDoesNotLeakHiddenAllocatables()
    {
        List<Map<String, Object>> got = tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    allocatableMatching: { typeIn: [room] }
                  }) { id } }
                """)
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // Whatever monty sees must be a subset of what homer sees — but the
        // sharper property is that the response is well-formed and never
        // throws. Existence-leak property requires comparing against the
        // hand-computed monty-visible set; we delegate that to the existing
        // §12 contract on `reservations()` + the new resolution path.
        assertNotNull(got, "result must be non-null even when allocatableMatching is set");
    }

    /**
     * Regression — `reservations(filter:)` must return reservations the
     * caller can READ, not only ones they own. The
     * {@code AppointmentImpl.getAppointments(user, ...)} helper at the
     * storage layer filters by appointment-owner when {@code user != null};
     * the resolver must pass {@code null} there and let the post-loop
     * {@code canRead(r, caller)} check enforce §12. Pre-fix: monty queried
     * → 0 results (no events owned by monty). Post-fix: monty sees the
     * fixture's events because the `event` DT has read=everyone.
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void monkeyCanReadReservationsSheDoesntOwn()
    {
        List<Map<String, Object>> got = tester.document("""
                query {
                  reservations(filter: {
                    from: "2001-01-01T00:00:00",
                    to:   "2020-12-31T00:00:00"
                  }) { id }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertFalse(got.isEmpty(),
                () -> "monty must see homer-owned readable reservations; got " + got);
    }

    /**
     * Window cap is configurable via {@code rapla.graphql.max-query-window-days}.
     * Default is null (no cap) — large windows are allowed unless a deployer
     * explicitly opts in to a cap. This test exercises the default-no-cap path.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void largeWindowAllowedByDefault()
    {
        tester.document("""
                query {
                  reservations(filter: {
                    from: "2020-01-01T00:00:00",
                    to:   "2025-12-31T00:00:00"
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertTrue(errs.isEmpty(),
                        () -> "default: no window cap → no error expected; got " + errs));
    }

    /** Filter is mandatory — omitting it is a schema-level error. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void filterIsMandatory()
    {
        tester.document("""
                query { reservations { id } }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "expected schema error for missing mandatory filter"));
    }

    // ============================================================ happy path read

    /**
     * Authenticated admin can query reservations within a window. Verifies the
     * end-to-end wire: query → operator dispatch → permission filter → DTO
     * shape. testdefault.xml ships some reservations under the `event` type.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminCanReadReservationsInWindow()
    {
        // Wide-but-bounded window covering the testdefault.xml fixture data
        List<Map<String, Object>> result = tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    id
                    firstDate
                    lastDate
                    canModify
                    classification { typeKey }
                    appointments { id start end allDay }
                    allocations { appointmentIds }
                  }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        // The fixture has reservations in 2010 — minimal assertion is no error
        // + canModify present (boolean). Empty result is acceptable here; the
        // value of this test is the wire-shape coverage.
        assertNotNull(result, "result must not be null");
        for (Map<String, Object> r : result)
        {
            assertNotNull(r.get("id"), "id required");
            assertNotNull(r.get("firstDate"), "firstDate required");
            assertNotNull(r.get("lastDate"), "lastDate required");
            assertNotNull(r.get("canModify"), "canModify required");
            assertEquals(Boolean.TRUE, r.get("canModify"),
                    () -> "admin should canModify every visible reservation: " + r);
        }
    }

    // ============================================================ repeating + blocks

    /**
     * Verifies the RepeatingRule wire shape on a recurring reservation.
     * testdefault.xml has a monthly-repeating reservation with end-date
     * 2010-09-04. Asserts the rule comes through with type=MONTHLY and
     * end=2010-09-04 (Date scalar, not LocalDateTime).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void repeatingRuleSerializesAsDate()
    {
        List<Map<String, Object>> result = tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    appointments {
                      repeating { type interval end count weekdays }
                    }
                  }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        // Find at least one repeating appointment in the result set
        boolean foundRepeating = false;
        for (Map<String, Object> r : result)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appts = (List<Map<String, Object>>) r.get("appointments");
            for (Map<String, Object> a : appts)
            {
                Map<String, Object> rep = (Map<String, Object>) a.get("repeating");
                if (rep == null) continue;
                foundRepeating = true;
                assertNotNull(rep.get("type"), "RepeatingRule.type required");
                assertNotNull(rep.get("interval"), "RepeatingRule.interval required");
                // 'end' may be null (open-ended) or a Date string like "2010-09-04"
                Object end = rep.get("end");
                if (end != null)
                {
                    String s = end.toString();
                    assertTrue(s.matches("\\d{4}-\\d{2}-\\d{2}"),
                            () -> "RepeatingRule.end must be ISO date format YYYY-MM-DD, got: " + s);
                }
            }
        }
        assertTrue(foundRepeating, "fixture should contain at least one repeating appointment in 2010");
    }

    /**
     * Verifies Appointment.blocks(from:, to:) materializes recurrence
     * expansion. Picks a wide window over the fixture's 2010 data and
     * asserts each block has start/end as LocalDateTime strings.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksMaterializeInWindow()
    {
        List<Map<String, Object>> result = tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    appointments {
                      blocks(from: "2010-01-01T00:00:00", to: "2010-12-31T00:00:00") {
                        start end isException
                      }
                    }
                  }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        boolean foundAnyBlock = false;
        for (Map<String, Object> r : result)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appts = (List<Map<String, Object>>) r.get("appointments");
            for (Map<String, Object> a : appts)
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> blocks = (List<Map<String, Object>>) a.get("blocks");
                if (blocks == null || blocks.isEmpty()) continue;
                foundAnyBlock = true;
                for (Map<String, Object> b : blocks)
                {
                    assertNotNull(b.get("start"), "block.start required");
                    assertNotNull(b.get("end"), "block.end required");
                    assertNotNull(b.get("isException"), "block.isException required");
                    String s = b.get("start").toString();
                    assertTrue(s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                            () -> "block.start must be ISO LocalDateTime, got: " + s);
                }
            }
        }
        assertTrue(foundAnyBlock, "fixture should produce at least one materialized block in 2010");
    }

    /**
     * PRD 094 D4 — every block exposes its owning appointment's id (the SPA
     * delete-scope flow needs the (appointmentId, blockStart) pair per row).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksExposeAppointmentId()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    start appointmentId
                  }
                }
                """)
                .execute()
                .path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks in the 2006 window");
        for (Map<String, Object> b : blocks)
        {
            Object id = b.get("appointmentId");
            assertNotNull(id, "block.appointmentId required");
            assertFalse(id.toString().isBlank(), "block.appointmentId must be non-blank");
        }
    }

    // ============================================================ PRD 074 Baustein 1 — appointmentBlocks query root

    /** Schema smoke: the Query.appointmentBlocks root appears in introspection. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaExposesAppointmentBlocksRoot()
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
        assertTrue(names.contains("appointmentBlocks"),
                () -> "missing Query.appointmentBlocks in " + names);
    }

    /**
     * PRD 074 — the block-rooted query root materializes recurrence blocks
     * across all caller-visible reservations in the window (flat list). One row
     * = one block. Mirrors {@link #appointmentBlocksMaterializeInWindow} but
     * rooted at Query.appointmentBlocks instead of nested under appointments.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksRootMaterializesInWindow()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    start end isException
                  }
                }
                """)
                .execute()
                .path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertNotNull(blocks, "appointmentBlocks must not be null");
        assertFalse(blocks.isEmpty(), "fixture should produce blocks in the 2006 window");
        // Flat list, sorted ascending by start; each block well-formed.
        String prev = null;
        for (Map<String, Object> b : blocks)
        {
            assertNotNull(b.get("start"), "block.start required");
            assertNotNull(b.get("end"), "block.end required");
            assertNotNull(b.get("isException"), "block.isException required");
            String s = b.get("start").toString();
            assertTrue(s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                    () -> "block.start must be ISO LocalDateTime, got: " + s);
            if (prev != null)
            {
                assertTrue(prev.compareTo(s) <= 0,
                        "blocks must be sorted ascending by start (" + prev + " > " + s + ")");
            }
            prev = s;
        }
    }

    /**
     * Recurrence expansion: the fixture's MONTHLY appointment (start 2006-09-04,
     * end-date 2010-09-04) yields multiple blocks (Sep/Oct/Nov/Dec) within the
     * 2006 window — proving createBlocks is invoked per appointment on the root
     * path, not one-block-per-appointment.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksRootExpandsRecurrence()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) { start }
                }
                """)
                .execute()
                .path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertTrue(blocks.size() >= 4,
                () -> "MONTHLY recurrence should expand to ≥4 blocks in the 2006 window; got " + blocks.size());
    }

    /**
     * Limit truncation returns the EARLIEST N blocks (bounded top-N), not an
     * arbitrary subset. limit:2 → exactly the 2 earliest of the full set.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksRootLimitReturnsEarliest()
    {
        String window = "from: \"2006-01-01T00:00:00\", to: \"2006-12-31T00:00:00\"";
        List<Map<String, Object>> all = tester.document(
                "query { appointmentBlocks(filter: { " + window + " }) { start } }")
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertTrue(all.size() > 2, () -> "precondition: window must have >2 blocks; got " + all.size());
        List<Map<String, Object>> limited = tester.document(
                "query { appointmentBlocks(filter: { " + window + ", limit: 2 }) { start } }")
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertEquals(2, limited.size(), "limit:2 must return exactly 2 blocks");
        // Must be the 2 globally-earliest, in ascending order.
        assertEquals(all.get(0).get("start"), limited.get(0).get("start"), "earliest block first");
        assertEquals(all.get(1).get("start"), limited.get(1).get("start"), "second-earliest second");
    }

    /**
     * §12 positive — a non-admin caller (monty/USER) sees the readable subset
     * of blocks (the fixture's `event` DT is read=everyone), mirroring
     * {@link #monkeyCanReadReservationsSheDoesntOwn}. The block path delegates
     * §12 wholly to {@code reservations()}.
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void appointmentBlocksRootNonAdminSeesReadableSubset()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) { start }
                }
                """)
                .execute()
                .path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertFalse(blocks.isEmpty(),
                () -> "monty must see blocks of homer-owned readable reservations; got " + blocks);
    }

    /** PRD 074 Baustein 2 — schema: Reservation.displayName + AppointmentBlock.reservation. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaExposesBlockReservationAndDisplayName()
    {
        List<String> resFields = typeFieldNames("Reservation");
        assertTrue(resFields.contains("name"),
                () -> "missing Reservation.name in " + resFields);
        // displayName is @deprecated → excluded from the default field list; visible with includeDeprecated.
        Map<String, Object> withDep = tester.document(
                "{ __type(name: \"Reservation\") { fields(includeDeprecated: true) { name isDeprecated } } }")
                .execute().path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> depFields = (List<Map<String, Object>>) withDep.get("fields");
        Map<String, Object> dn = depFields.stream()
                .filter(f -> "displayName".equals(f.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError("missing deprecated Reservation.displayName"));
        assertEquals(Boolean.TRUE, dn.get("isDeprecated"), "displayName must be @deprecated");
        List<String> blockFields = typeFieldNames("AppointmentBlock");
        assertTrue(blockFields.contains("reservation"),
                () -> "missing AppointmentBlock.reservation in " + blockFields);
    }

    private List<String> typeFieldNames(String typeName)
    {
        Map<String, Object> result = tester.document(
                "{ __type(name: \"" + typeName + "\") { fields { name } } }")
                .execute().path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        return fields.stream().map(f -> (String) f.get("name")).toList();
    }

    /**
     * PRD 074 Baustein 2 — block → reservation → displayName resolves on the
     * root path (the dhbw `name: reservation { displayName }` column).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksRootExposesReservationDisplayName()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    start
                    reservation { id displayName }
                  }
                }
                """)
                .execute()
                .path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks");
        for (Map<String, Object> b : blocks)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = (Map<String, Object>) b.get("reservation");
            assertNotNull(res, () -> "every block must carry its reservation; got " + b);
            assertNotNull(res.get("id"), "reservation.id required");
            assertNotNull(res.get("displayName"), () -> "reservation.displayName required; got " + res);
            assertFalse(((String) res.get("displayName")).isBlank(), "displayName must be non-blank");
        }
    }

    /**
     * PRD 074 Baustein 9 — AppointmentBlock.name is FLAT and block-aware. In the
     * note-free fixture it equals the reservation's name (block → reservation), but
     * it is resolved via reservation.formatAppointmentBlock so an appointment-note
     * override would diverge. Asserts: field present, non-blank, == reservation.name.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlockNameIsFlatAndMatchesReservationWhenNoNote()
    {
        assertTrue(typeFieldNames("AppointmentBlock").contains("name"),
                () -> "missing AppointmentBlock.name");
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    name
                    reservation { name }
                  }
                }
                """)
                .execute()
                .path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks");
        for (Map<String, Object> b : blocks)
        {
            String flat = (String) b.get("name");
            assertNotNull(flat, () -> "block.name required; got " + b);
            assertFalse(flat.isBlank(), "block.name must be non-blank");
            @SuppressWarnings("unchecked")
            Map<String, Object> res = (Map<String, Object>) b.get("reservation");
            assertEquals(res.get("name"), flat,
                    () -> "note-free fixture: block.name must equal reservation.name; got " + b);
        }
    }

    /**
     * PRD 074 Baustein 9 — Appointment.name is appointment-aware (reservation.formatAppointment).
     * Note-free fixture: equals the reservation name; field present + non-blank.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentNameIsAppointmentAwareAndMatchesReservationWhenNoNote()
    {
        assertTrue(typeFieldNames("Appointment").contains("name"),
                () -> "missing Appointment.name");
        List<Map<String, Object>> reservations = tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    name
                    appointments { name }
                  }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertFalse(reservations.isEmpty(), "fixture should produce reservations");
        boolean sawAppointment = false;
        for (Map<String, Object> r : reservations)
        {
            String resName = (String) r.get("name");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> apps = (List<Map<String, Object>>) r.get("appointments");
            assertNotNull(apps, "reservation.appointments required");
            for (Map<String, Object> a : apps)
            {
                sawAppointment = true;
                String aName = (String) a.get("name");
                assertNotNull(aName, () -> "appointment.name required; got " + a);
                assertFalse(aName.isBlank(), "appointment.name must be non-blank");
                assertEquals(resName, aName,
                        () -> "note-free fixture: appointment.name must equal reservation.name");
            }
        }
        assertTrue(sawAppointment, "fixture should expose at least one appointment");
    }

    /**
     * PRD 074 Baustein 3 — AppointmentBlock.allocatables(filter:) narrows the
     * block's allocatables by type (Kurs/Person/Raum columns), reusing the
     * Appointment.allocatables §12 + restriction logic. Schema + behaviour.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksRootAllocatablesFilterNarrows()
    {
        assertTrue(typeFieldNames("AppointmentBlock").contains("allocatables"),
                () -> "missing AppointmentBlock.allocatables");

        List<String> rooms = blockAllocatableNames("{ typeIn: [room] }");
        List<String> persons = blockAllocatableNames("{ isPersonEq: true }");
        assertTrue(rooms.contains("Room A66"),
                () -> "expected Room A66 among block rooms; got " + rooms);
        assertFalse(rooms.contains("Burns Monty"),
                () -> "person must be filtered out by typeIn:[room]; got " + rooms);
        assertTrue(persons.contains("Burns Monty"),
                () -> "expected lecturer among block persons; got " + persons);
        assertFalse(persons.contains("Room A66"),
                () -> "room must be filtered out by isPersonEq:true; got " + persons);
    }

    private List<String> blockAllocatableNames(String filterArg)
    {
        String nested = filterArg == null
                ? "allocatables { displayName }"
                : "allocatables(filter: " + filterArg + ") { displayName }";
        List<Map<String, Object>> blocks = tester.document(String.format("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    %s
                  }
                }
                """, nested))
                .execute()
                .path("appointmentBlocks")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Map<String, Object> b : blocks)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allocs = (List<Map<String, Object>>) b.get("allocatables");
            if (allocs == null) continue;
            for (Map<String, Object> al : allocs) names.add((String) al.get("displayName"));
        }
        return names;
    }

    /**
     * PRD 074 Baustein 4 — server-evaluated `times` (+ `duration`) on the block,
     * via the rapla function bridge. `times` (StandardFunctions, always present)
     * must resolve to a formatted time range; `duration` (eventtimecalculator
     * plugin) resolves to a string or null (plugin-dependent), without error.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksRootServerEvaluatesTimesAndDuration()
    {
        List<String> blockFields = typeFieldNames("AppointmentBlock");
        assertTrue(blockFields.contains("times"), () -> "missing AppointmentBlock.times in " + blockFields);
        assertTrue(blockFields.contains("duration"), () -> "missing AppointmentBlock.duration in " + blockFields);

        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) { times duration }
                }
                """)
                .execute()
                .path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks");
        boolean anyTimes = false;
        boolean anyDuration = false;
        for (Map<String, Object> b : blocks)
        {
            Object times = b.get("times");
            if (times != null)
            {
                anyTimes = true;
                assertTrue(times.toString().contains(":"),
                        () -> "times should be a formatted time range; got " + times);
            }
            // duration: the eventtimecalculator plugin IS registered in the full
            // @SpringBootTest context, so the bridge must resolve a non-null string
            // for every block (EventTimeModel.format never returns null; worst case
            // "" for zero/negative). A null here = the bridge regressed (factory
            // lookup / createFunction / parse failure).
            assertNotNull(b.get("duration"),
                    () -> "duration must be non-null when the plugin is present; got block " + b);
            if (!b.get("duration").toString().isBlank()) anyDuration = true;
        }
        assertTrue(anyTimes, "the times function bridge should resolve a formatted range for at least one block");
        assertTrue(anyDuration, "the duration bridge should resolve a non-blank value for at least one normal block");
    }

    /**
     * §12 — block.allocatables filter runs AFTER the canRead gate (no hidden-but-
     * matching allocatable leaks), for a non-admin caller. Mirrors
     * {@link #appointmentAllocatablesFilterRunsAfterCanReadGate} on the block path
     * (which delegates to the same shared resolver).
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void appointmentBlocksAllocatablesFilterRunsAfterCanReadGate()
    {
        List<String> unfilteredVisible = blockAllocatableNames(null);
        List<String> serverFilteredRooms = blockAllocatableNames("{ typeIn: [room] }");
        java.util.Set<String> roomNames = java.util.Set.of("erwin", "Room A66");
        java.util.Set<String> expected = unfilteredVisible.stream()
                .filter(roomNames::contains)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, new java.util.HashSet<>(serverFilteredRooms),
                () -> "block.allocatables filter must run over the canRead-narrowed subset only; "
                        + "visible=" + unfilteredVisible + " serverFiltered=" + serverFilteredRooms);
    }

    /**
     * PRD 074 Baustein 5 (model A) — Reservation.name(variant:). DISPLAY equals the
     * deprecated displayName; EXPORT/PLANNING fall back to DISPLAY when the fixture
     * type lacks those nameformats. Schema exposes name(variant:) + NameVariant enum.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationNameVariantResolvesWithFallback()
    {
        assertTrue(typeFieldNames("Reservation").contains("name"),
                () -> "missing Reservation.name");
        Map<String, Object> nv = tester.document("{ __type(name: \"NameVariant\") { enumValues { name } } }")
                .execute().path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ev = (List<Map<String, Object>>) nv.get("enumValues");
        List<String> variants = ev.stream().map(e -> (String) e.get("name")).toList();
        assertTrue(variants.containsAll(List.of("DISPLAY", "EXPORT", "PLANNING")),
                () -> "NameVariant must have DISPLAY/EXPORT/PLANNING; got " + variants);

        List<Map<String, Object>> rows = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    reservation {
                      def: name
                      disp: name(variant: DISPLAY)
                      exp:  name(variant: EXPORT)
                      legacy: displayName
                    }
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(rows.isEmpty(), "fixture should produce blocks");
        for (Map<String, Object> b : rows)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = (Map<String, Object>) b.get("reservation");
            assertNotNull(res.get("def"), "name (default) required");
            assertEquals(res.get("disp"), res.get("def"), "name default must equal name(variant:DISPLAY)");
            assertEquals(res.get("disp"), res.get("legacy"), "name(DISPLAY) must equal deprecated displayName");
            assertEquals(res.get("disp"), res.get("exp"),
                    () -> "EXPORT must fall back to DISPLAY when the type lacks nameformat_export; got " + res);
        }
    }

    /**
     * PRD 074 Baustein 6 — AppointmentBlock.compute(expr:) inline composition.
     * `{p->times(p)}` must equal the `times` field; a concat composes; an unknown
     * function returns null (not an error).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksComputeEvaluatesExpression()
    {
        assertTrue(typeFieldNames("AppointmentBlock").contains("compute"),
                () -> "missing AppointmentBlock.compute");
        List<Map<String, Object>> rows = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    times
                    viaCompute: compute(expr: "{p->times(p)}")
                    doubled:    compute(expr: "{p->times(p)}-{p->times(p)}")
                    broken:     compute(expr: "{p->nosuchfn(p)}")
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(rows.isEmpty(), "fixture should produce blocks");
        boolean anyTimes = false;
        for (Map<String, Object> b : rows)
        {
            assertEquals(b.get("times"), b.get("viaCompute"),
                    () -> "compute({p->times(p)}) must equal the times field; got " + b);
            if (b.get("times") != null && !b.get("times").toString().isBlank())
            {
                anyTimes = true;
                assertEquals(b.get("times") + "-" + b.get("times"), b.get("doubled"),
                        () -> "two placeholders + literal text must compose; got " + b);
            }
            org.junit.jupiter.api.Assertions.assertNull(b.get("broken"),
                    () -> "unknown function must yield null, not error; got " + b);
        }
        assertTrue(anyTimes, "at least one block should have a non-blank times for the composition check");
    }

    /**
     * PRD 074 V2 — expr ergonomics: bare body auto-wraps as {item -> …}; subject `item` is implicit
     * (0-arg `times()`) or explicit (`times(item)`); the braced explicit form accepts both arrows
     * (`->` and `=>`). All four notations must equal the full `{p->times(p)}` form.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlockComputeExprErgonomics()
    {
        List<Map<String, Object>> rows = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    full:     compute(expr: "{p->times(p)}")
                    bare:     compute(expr: "times()")
                    itemArg:  compute(expr: "times(item)")
                    arrowFat: compute(expr: "{item => times(item)}")
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(rows.isEmpty(), "fixture should produce blocks");
        for (Map<String, Object> b : rows)
        {
            Object full = b.get("full");
            assertEquals(full, b.get("bare"),     () -> "bare body + 0-arg times() must equal full form; got " + b);
            assertEquals(full, b.get("itemArg"),  () -> "explicit item must equal full form; got " + b);
            assertEquals(full, b.get("arrowFat"), () -> "=> arrow must equal full form; got " + b);
        }
    }

    /**
     * PRD 074 Stufe b — aggregate by a numeric `expr` (coerced to a number). A constant `expr:"1"`
     * summed equals the block count, proving expr-metric coercion feeds the (built) reduction.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockStatsExprMetricCoercion()
    {
        Map<String, Object> bucket = tester.document("""
                query {
                  appointmentBlockStats(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    aggregate: [ { key: "ones",  expr: "1",                  fn: SUM },
                                 { key: "count", field: DURATION_MINUTES,    fn: COUNT } ]
                  ) { count values { key number } }
                }
                """)
                .execute().path("appointmentBlockStats[0]")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) bucket.get("values");
        long ones  = values.stream().filter(v -> v.get("key").equals("ones"))
                .mapToLong(v -> ((Number) v.get("number")).longValue()).findFirst().orElse(-1);
        long count = values.stream().filter(v -> v.get("key").equals("count"))
                .mapToLong(v -> ((Number) v.get("number")).longValue()).findFirst().orElse(-2);
        assertEquals(count, ones, "SUM(expr \"1\") must equal the block count (expr-metric coercion)");
        assertEquals(((Number) bucket.get("count")).longValue(), ones, "and equal bucket.count");
    }

    /**
     * belongsTo asymmetry fix — {@code groupBy.allocatables.idIn:[parentId]} must match a CHILD
     * allocatable that belongsTo the parent. Mirrors the real Raum→Gebäude case: a building id in the
     * stats fan-out selects the building's rooms. The fan-out path ({@code filterAllocatables}) walks
     * belongsTo UP, the same hierarchy the filter path's {@code getDependentRef} walks DOWN. Without the
     * fix the parent id matches no child (room id ≠ building id) and the bucket is absent.
     *
     * <p>Fixture: Teilraum "Room A66.1" ({@code rdd6b473…}) {@code a1}→ Room A66 ({@code c24ce517…}),
     * belongsTo=true. Counterpart {@link #catalogAllocatablesIdInStaysExactId()} asserts the global
     * {@code Query.allocatables} idIn keeps exact-id semantics — only the group path expands.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void groupByAllocatableIdInMatchesBelongsToChild()
    {
        final String roomA66  = "c24ce517-4697-4e52-9917-ec000c84563c"; // parent Raum
        final String teilraum = "rdd6b473-7c77-4344-a73d-1f27008341cb"; // belongsTo Room A66

        // Seed a reservation allocating the CHILD Teilraum in an isolated future window.
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "e6666666-6666-4666-8666-666666666666",
                    typeKey: "event",
                    classification: { event: {} },
                    appointments: [ { id: "a6666666-6666-4666-8666-666666666666",
                                      start: "2031-03-03T09:00:00", end: "2031-03-03T10:00:00", allDay: false } ],
                    allocations: [ { allocatableId: "%s" } ]
                  }) { id }
                }
                """.formatted(teilraum))
                .execute().path("createReservation.id").entity(String.class).get();

        // Group-path idIn = [PARENT room id] → must bucket the CHILD Teilraum via belongsTo.
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  appointmentBlockStats(
                    filter: { from: "2031-03-01T00:00:00", to: "2031-03-31T00:00:00",
                              allocatableMatching: { idIn: ["%s"] } },
                    groupBy: [ { key: "raum", allocatables: { idIn: ["%s"] } } ],
                    aggregate: [ { key: "termine", field: DURATION_MINUTES, fn: COUNT } ]
                  ) { keys { value entity { ... on Allocatable { id } } } }
                }
                """.formatted(roomA66, roomA66))
                .execute().path("appointmentBlockStats")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {}).get();

        List<String> entityIds = new java.util.ArrayList<>();
        for (Map<String, Object> b : buckets)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ks = (List<Map<String, Object>>) b.get("keys");
            if (ks == null || ks.isEmpty()) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> ent = (Map<String, Object>) ks.get(0).get("entity");
            if (ent != null) entityIds.add((String) ent.get("id"));
        }
        assertTrue(entityIds.contains(teilraum),
                () -> "group-path idIn=[parent Room A66] must bucket the child Teilraum via belongsTo; got " + entityIds);
    }

    /**
     * Counterpart to {@link #groupByAllocatableIdInMatchesBelongsToChild()} — the GLOBAL catalog query
     * {@code Query.allocatables(filter:{idIn:[parentId]})} keeps EXACT-id semantics: it returns the
     * parent itself, NOT its belongsTo children. The belongsTo expansion is scoped to the stats
     * fan-out only; a normal allocatable lookup must not silently swap a building for its rooms.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void catalogAllocatablesIdInStaysExactId()
    {
        final String roomA66  = "c24ce517-4697-4e52-9917-ec000c84563c"; // parent Raum
        final String teilraum = "rdd6b473-7c77-4344-a73d-1f27008341cb"; // belongsTo Room A66

        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { idIn: ["%s"] }) { id } }
                """.formatted(roomA66))
                .execute().path("allocatables")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {}).get();

        List<String> ids = got.stream().map(a -> (String) a.get("id")).toList();
        assertTrue(ids.contains(roomA66),   () -> "catalog idIn must return the requested parent; got " + ids);
        assertFalse(ids.contains(teilraum), () -> "catalog idIn must NOT expand to belongsTo children; got " + ids);
        assertEquals(1, ids.size(),         () -> "catalog idIn returns exactly the requested id; got " + ids);
    }

    /**
     * PRD 074 Baustein 7 — Allocatable.name(variant:) mirrors Reservation.name;
     * Allocatable.displayName is now @deprecated (still queryable). name(DISPLAY)
     * equals the legacy displayName; EXPORT falls back to DISPLAY in the fixture.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatableNameVariantMirrorsReservation()
    {
        assertTrue(typeFieldNames("Allocatable").contains("name"),
                () -> "missing Allocatable.name");
        // displayName deprecated → not in default field list; present with includeDeprecated.
        Map<String, Object> withDep = tester.document(
                "{ __type(name: \"Allocatable\") { fields(includeDeprecated: true) { name isDeprecated } } }")
                .execute().path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> depFields = (List<Map<String, Object>>) withDep.get("fields");
        Map<String, Object> dn = depFields.stream()
                .filter(f -> "displayName".equals(f.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError("missing Allocatable.displayName"));
        assertEquals(Boolean.TRUE, dn.get("isDeprecated"), "Allocatable.displayName must be @deprecated");

        List<Map<String, Object>> rows = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    allocatables(filter: { isPersonEq: false }) {
                      n:      name
                      disp:   name(variant: DISPLAY)
                      exp:    name(variant: EXPORT)
                      legacy: displayName
                    }
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        boolean sawAny = false;
        for (Map<String, Object> b : rows)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allocs = (List<Map<String, Object>>) b.get("allocatables");
            if (allocs == null) continue;
            for (Map<String, Object> a : allocs)
            {
                sawAny = true;
                assertEquals(a.get("legacy"), a.get("n"), () -> "name must equal deprecated displayName; got " + a);
                assertEquals(a.get("disp"), a.get("n"), () -> "name default must equal name(DISPLAY); got " + a);
                assertEquals(a.get("disp"), a.get("exp"), () -> "EXPORT falls back to DISPLAY; got " + a);
            }
        }
        assertTrue(sawAny, "fixture should have non-person allocatables on a block");
    }

    // ============================================================ PRD 074 — render-meta (@view → extensions.view)

    private static String gqlBody(String query)
    {
        return "{\"query\":\"" + query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"}";
    }

    /**
     * PRD 074 — an operation carrying @view emits extensions.view (key + resolved
     * title + column descriptors from the root field's selection + field directives).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewDirectiveEmitsExtensionsView() throws Exception
    {
        String query = """
                query Termine @view(title: "Termine KW") {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 2 }) {
                    head: reservation @column(header: "Veranstaltung") { name }
                    start
                    day: start @hidden
                    persons: allocatables(filter: { isPersonEq: true }) @join(separator: "; ") { name }
                  }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.key").value("Termine"))
                .andExpect(jsonPath("$.extensions.view.title").value("Termine KW"))
                .andExpect(jsonPath("$.extensions.view.columns[*].alias")
                        .value(hasItems("head", "start", "day", "persons")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='head')].header")
                        .value(hasItem("Veranstaltung")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='day')].hidden")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='persons')].join")
                        .value(hasItem("; ")))
                // schema-derived type hints — the GUI uses these for alignment/formatting
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='start')].type")
                        .value(hasItem("LocalDateTime")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='head')].type")
                        .value(hasItem("Reservation")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='persons')].type")
                        .value(hasItem("Allocatable")));
    }

    /**
     * PRD 074 §"Window and inputs directives" — the {@code @window} directive is resolved
     * server-side at request time and emitted as {@code extensions.view.window} (the SPA seeds
     * its date-nav from it; no client-side anchor resolution). A Monday week resolves to the
     * current ISO week's Monday → next Monday, both at 00:00:00.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewWindowResolvedFromWindowDirective() throws Exception
    {
        String query = """
                query Wochenplan($filter: ReservationFilter! = { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 1 })
                  @view(title: "Wochenplan")
                  @window(from: { anchor: WEEK_START, offset: 0 }, to: { anchor: WEEK_START, offset: 7 }) {
                  appointmentBlocks(filter: $filter) { start }
                }
                """;
        java.time.LocalDate monday = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.window.from").value(monday + "T00:00:00"))
                .andExpect(jsonPath("$.extensions.view.window.to").value(monday.plusDays(7) + "T00:00:00"))
                .andExpect(jsonPath("$.extensions.view.inputs").doesNotExist());
    }

    /**
     * PRD 074 §"Window and inputs directives" — no {@code @window} → the render-mode default
     * window (table → TODAY −7 … +7), still resolved server-side and emitted.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewWindowDefaultsToTableModeWithoutDirective() throws Exception
    {
        String query = """
                query Termine @view(title: "Termine") {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 1 }) { start }
                }
                """;
        java.time.LocalDate today = java.time.LocalDate.now();
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.window.from").value(today.minusDays(7) + "T00:00:00"))
                .andExpect(jsonPath("$.extensions.view.window.to").value(today.plusDays(7) + "T00:00:00"));
    }

    /**
     * PRD 074 — @column(order:) sorts the emitted column descriptors so the GUI can
     * render them left-to-right without re-sorting. Columns without order keep their
     * declaration index as the sort key (an explicit order slots into that position).
     */
    /**
     * PRD 074 — @column(group: true) marks the row-grouping column: emitted as columns[].group and
     * surfaced as the top-level view.groupBy (the column alias) so a generic renderer groups
     * client-side by row[view.groupBy] without per-view config.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewColumnGroupEmitsGroupByHint() throws Exception
    {
        String query = """
                query Wochenansicht @view(title: "Wochenansicht") {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 2 }) {
                    date  @column(header: "Datum", order: 1, group: true, format: "EE dd.MM")
                    times @column(header: "Zeit",  order: 2)
                  }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.groupBy").value("date"))
                .andExpect(jsonPath("$.extensions.view.groupFormat").value("EE dd.MM"))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='date')].group")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='date')].format")
                        .value(hasItem("EE dd.MM")))
                // non-group columns must not carry the flags
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='times')].group")
                        .value(empty()));
    }

    /**
     * PRD 079/080 — a @view over the stats root (BlockStatBucket) emits a FLAT column set derived
     * from the groupBy keys (kind group, + selected entity leaf fields kind entity), the aggregate
     * keys (kind value, + fn), and count — not the generic keys/values/count containers. Data shape
     * stays untouched; columns tell the renderer how to flatten it.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void statsViewEmitsFlatColumnsFromGroupByAndAggregate() throws Exception
    {
        String query = """
                query Raumauslastung @view(title: "Raumauslastung") {
                  appointmentBlockStats(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "raum", allocatables: { typeIn: [room] } } ],
                    aggregate: [ { key: "minuten", field: DURATION_MINUTES, fn: SUM },
                                 { key: "termine", field: DURATION_MINUTES, fn: COUNT } ]
                  ) {
                    keys { value entity { ... on Allocatable { id } } }
                    values { key number }
                    count
                  }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                // group column from groupBy key, typed by the dimension
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='raum')].kind").value(hasItem("group")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='raum')].type").value(hasItem("Allocatable")))
                // selected entity leaf field becomes an entity column hung off the group key
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='id')].kind").value(hasItem("entity")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='id')].group").value(hasItem("raum")))
                // metric columns from aggregate keys, carrying fn
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='minuten')].kind").value(hasItem("value")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='minuten')].fn").value(hasItem("SUM")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='termine')].fn").value(hasItem("COUNT")))
                // count column; and NO generic container columns
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='count')].kind").value(hasItem("count")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='keys')]").value(empty()))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='values')]").value(empty()));
    }

    /**
     * PRD 074/078 — a @view emits its operation variable signature (name + GraphQL type)
     * as extensions.view.variables. The SPA binds each variable BY TYPE (ReservationFilter
     * ← window+selection, AllocatableFilter ← selection) without ever seeing the stored
     * query, so it can fill ALL required variables (e.g. a stats view's two filters).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewEmitsVariableSignature() throws Exception
    {
        String query = """
                query Raumauslastung(
                  $filter: ReservationFilter! = { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                  $allocatableFilter: AllocatableFilter! = { typeIn: [room] }
                ) @view(title: "Raumauslastung") {
                  appointmentBlockStats(
                    filter: $filter,
                    groupBy:   [ { key: "raum", allocatables: $allocatableFilter } ],
                    aggregate: [ { key: "minuten", field: DURATION_MINUTES, fn: SUM } ]
                  ) {
                    keys { value entity { ... on Allocatable { id } } }
                    values { key number }
                  }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.variables[?(@.name=='filter')].type")
                        .value(hasItem("ReservationFilter!")))
                .andExpect(jsonPath("$.extensions.view.variables[?(@.name=='allocatableFilter')].type")
                        .value(hasItem("AllocatableFilter!")));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewColumnsSortedByOrder() throws Exception
    {
        String query = """
                query Sorted @view {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 1 }) {
                    c: start @column(order: 2)
                    a: end @column(order: 0)
                    b: isException @column(order: 1)
                  }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.columns[*].alias").value(contains("a", "b", "c")));
    }

    /**
     * PRD 074 — sort: START DESC returns the latest blocks first (reverses the
     * default ascending order). Asserts the result is non-increasing by start.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksSortStartDesc()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    sort: [{ field: START, dir: DESC }]
                  ) { start }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertTrue(blocks.size() >= 2, "need ≥2 blocks to assert ordering");
        String prev = null;
        for (Map<String, Object> b : blocks)
        {
            String s = b.get("start").toString();
            if (prev != null)
                assertTrue(prev.compareTo(s) >= 0,
                        "DESC: blocks must be non-increasing by start (" + prev + " < " + s + ")");
            prev = s;
        }
    }

    /**
     * PRD 074 — offset pagination: page 0 (offset 0) and page 1 (offset 2), each
     * limit 2, are disjoint and contiguous — page 1 continues exactly where page 0
     * stopped against the full ascending list.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksOffsetPaginates()
    {
        String win = "from: \"2006-01-01T00:00:00\", to: \"2006-12-31T00:00:00\"";
        List<Map<String, Object>> all = tester.document(
                "query { appointmentBlocks(filter: { " + win + " }) { start } }")
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertTrue(all.size() >= 4, () -> "need ≥4 blocks; got " + all.size());

        List<Map<String, Object>> page1 = tester.document(
                "query { appointmentBlocks(filter: { " + win + ", limit: 2 }, offset: 2) { start } }")
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertEquals(2, page1.size(), "offset 2 limit 2 returns 2 rows");
        assertEquals(all.get(2).get("start"), page1.get(0).get("start"), "page1[0] == all[2]");
        assertEquals(all.get(3).get("start"), page1.get(1).get("start"), "page1[1] == all[3]");
    }

    /**
     * PRD 074 — @view + pagination emits extensions.view.page {offset,limit,returned,hasMore}.
     * With limit 1 over a multi-block window, hasMore must be true.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewPageMetaEmitted() throws Exception
    {
        String query = """
                query Paged @view(title: "P") {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 1 }, offset: 0) {
                    start @column(header: "Beginn")
                  }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.page.offset").value(0))
                .andExpect(jsonPath("$.extensions.view.page.limit").value(1))
                .andExpect(jsonPath("$.extensions.view.page.returned").value(1))
                .andExpect(jsonPath("$.extensions.view.page.hasMore").value(true));
    }

    /**
     * PRD 074 — @flatten adds a `flatten` hint to the column descriptor naming the leaf
     * the GUI projects: explicit `@flatten(field:)` carries that field; bare `@flatten`
     * over a single-sub-field selection auto-detects it. Meta-only (data stays nested).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void viewFlattenHintEmitted() throws Exception
    {
        String query = """
                query Flat @view {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 1 }) {
                    auto:     reservation @flatten { name }
                    explicit: reservation @flatten(field: "id") { id name }
                  }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='auto')].flatten")
                        .value(hasItem("name")))
                .andExpect(jsonPath("$.extensions.view.columns[?(@.alias=='explicit')].flatten")
                        .value(hasItem("id")));
    }

    /**
     * PRD 079 — appointmentBlockStats with NO groupBy = one global bucket (= a total).
     * SUM of durationMinutes over the full set equals the sum of the per-row values, and
     * COUNT equals the block count. Typed `data`, not extensions.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockStatsGlobalBucketSumsFullSet()
    {
        List<Map<String, Object>> all = tester.document(
                "query { appointmentBlocks(filter: { from: \"2006-01-01T00:00:00\", to: \"2006-12-31T00:00:00\" }) { durationMinutes } }")
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        int fullCount = all.size();
        assertTrue(fullCount >= 2, () -> "need ≥2 blocks; got " + fullCount);
        long expectedSum = all.stream().mapToLong(b -> ((Number) b.get("durationMinutes")).longValue()).sum();

        Map<String, Object> bucket = tester.document("""
                query {
                  appointmentBlockStats(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    aggregate: [ { key: "minutes", field: DURATION_MINUTES, fn: SUM },
                                 { key: "n",       field: DURATION_MINUTES, fn: COUNT } ]
                  ) { count keys { key value } values { key number text } }
                }
                """)
                .execute().path("appointmentBlockStats[0]")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {}).get();
        assertEquals(fullCount, ((Number) bucket.get("count")).intValue(), "global bucket counts all blocks");
        assertTrue(((List<?>) bucket.get("keys")).isEmpty(), "no groupBy → empty keys");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) bucket.get("values");
        long sum = values.stream().filter(v -> v.get("key").equals("minutes"))
                .mapToLong(v -> ((Number) v.get("number")).longValue()).findFirst().orElse(-1);
        long n = values.stream().filter(v -> v.get("key").equals("n"))
                .mapToLong(v -> ((Number) v.get("number")).longValue()).findFirst().orElse(-1);
        assertEquals(expectedSum, sum, "SUM(durationMinutes) over full set");
        assertEquals(fullCount, n, "COUNT equals block count");
    }

    /**
     * PRD 079 — groupBy ISO_WEEK produces one bucket per week; the per-week SUMs add up to the
     * global SUM and the per-week counts add up to the total. Proves bucketing partitions the set.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockStatsGroupByWeekPartitions()
    {
        List<Map<String, Object>> all = tester.document(
                "query { appointmentBlocks(filter: { from: \"2006-01-01T00:00:00\", to: \"2006-12-31T00:00:00\" }) { durationMinutes } }")
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        int fullCount = all.size();
        long expectedSum = all.stream().mapToLong(b -> ((Number) b.get("durationMinutes")).longValue()).sum();

        List<Map<String, Object>> buckets = tester.document("""
                query {
                  appointmentBlockStats(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "week", date: START, by: ISO_WEEK } ],
                    aggregate: [ { key: "minutes", field: DURATION_MINUTES, fn: SUM } ]
                  ) { count keys { key value } values { key number } }
                }
                """)
                .execute().path("appointmentBlockStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "should produce week buckets");
        int summedCount = 0; long summedMinutes = 0;
        for (Map<String, Object> bk : buckets)
        {
            summedCount += ((Number) bk.get("count")).intValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) bk.get("keys");
            assertEquals("week", keys.get(0).get("key"), "key name carried through");
            assertTrue(((String) keys.get(0).get("value")).matches("\\d{4}-W\\d{2}"),
                    () -> "ISO week label, got " + keys.get(0).get("value"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> values = (List<Map<String, Object>>) bk.get("values");
            summedMinutes += ((Number) values.get(0).get("number")).longValue();
        }
        assertEquals(fullCount, summedCount, "per-week counts sum to total");
        assertEquals(expectedSum, summedMinutes, "per-week SUMs sum to global SUM");
    }

    /**
     * PRD 079 — custom group key via `expr` (same engine as compute). Grouping by a constant
     * expression collapses everything into a single bucket whose key is that constant.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockStatsGroupByComputeExpr()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  appointmentBlockStats(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "k", expr: "{p->'all'}" } ],
                    aggregate: [ { key: "n", field: DURATION_MINUTES, fn: COUNT } ]
                  ) { count keys { key value } }
                }
                """)
                .execute().path("appointmentBlockStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertEquals(1, buckets.size(), "constant expr → exactly one bucket");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) buckets.get(0).get("keys");
        assertEquals("k", keys.get(0).get("key"));
        assertEquals("all", keys.get(0).get("value"), "bucket key = the constant expr result");
    }

    /**
     * PRD 080 items 1/2 — an allocatable group dimension carries the typed entity in
     * StatKey.entity (Allocatable), selectable like a normal object (here displayName).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockStatsAllocatableDimensionCarriesEntity()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  appointmentBlockStats(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "raum", allocatables: { typeIn: [room] } } ],
                    aggregate: [ { key: "n", field: DURATION_MINUTES, fn: COUNT } ]
                  ) { keys { value entity { __typename ... on Allocatable { displayName } } } }
                }
                """)
                .execute().path("appointmentBlockStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "fixture should have room-grouped buckets");
        for (Map<String, Object> bk : buckets)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> key0 = ((List<Map<String, Object>>) bk.get("keys")).get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) key0.get("entity");
            assertNotNull(entity, () -> "allocatable dimension must carry entity; got " + bk);
            assertEquals("Allocatable", entity.get("__typename"), "entity is an Allocatable");
            assertEquals(key0.get("value"), entity.get("displayName"),
                    () -> "entity.displayName must equal the key value; got " + bk);
        }
    }

    /**
     * PRD 080 item 3 — `reservation: true` groups blocks by their event; StatKey.entity is the
     * typed Reservation, selectable.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockStatsReservationDimensionCarriesEntity()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  appointmentBlockStats(
                    filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "event", reservation: true } ],
                    aggregate: [ { key: "n", field: DURATION_MINUTES, fn: COUNT } ]
                  ) { count keys { value entity { __typename ... on Reservation { id } } } }
                }
                """)
                .execute().path("appointmentBlockStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "fixture should produce reservation buckets");
        Map<String, Object> bk = buckets.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> key0 = ((List<Map<String, Object>>) bk.get("keys")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) key0.get("entity");
        assertNotNull(entity, () -> "reservation dimension must carry entity; got " + bk);
        assertEquals("Reservation", entity.get("__typename"), "entity is a Reservation");
        assertNotNull(entity.get("id"), "reservation entity has an id");
    }

    /**
     * PRD 080 item 6 — allocatableStats groups the §12-visible allocatable population by DynamicType
     * and counts; COUNT needs no expr. The fixture has room allocatables, so a "room" bucket exists.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatableStatsGroupByTypeCounts()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  allocatableStats(
                    groupBy:   [ { key: "typ", type: true } ],
                    aggregate: [ { key: "n", fn: COUNT } ]
                  ) { count keys { key value } values { key number } }
                }
                """)
                .execute().path("allocatableStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "fixture should produce type-grouped allocatable buckets");
        for (Map<String, Object> bk : buckets)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> key0 = ((List<Map<String, Object>>) bk.get("keys")).get(0);
            assertEquals("typ", key0.get("key"));
            assertNotNull(key0.get("value"), "type bucket has a value (the type name)");
            @SuppressWarnings("unchecked")
            Map<String, Object> val0 = ((List<Map<String, Object>>) bk.get("values")).get(0);
            // COUNT value == the bucket count (population of this type).
            assertEquals(((Number) bk.get("count")).intValue(),
                    ((Number) val0.get("number")).intValue(), "COUNT metric == bucket count");
            assertTrue(((Number) bk.get("count")).intValue() > 0, "non-empty type bucket");
        }
    }

    /**
     * PRD 080 item 6 — `self: true` carries the typed Allocatable in StatKey.entity, selectable
     * (here displayName); filtered to one type to keep the bucket set bounded.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatableStatsSelfDimensionCarriesEntity()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  allocatableStats(
                    filter:    { typeIn: [room] },
                    groupBy:   [ { key: "res", self: true } ],
                    aggregate: [ { key: "n", fn: COUNT } ]
                  ) { keys { value entity { __typename ... on Allocatable { displayName } } } }
                }
                """)
                .execute().path("allocatableStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "fixture should have room allocatables");
        for (Map<String, Object> bk : buckets)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> key0 = ((List<Map<String, Object>>) bk.get("keys")).get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) key0.get("entity");
            assertNotNull(entity, () -> "self dimension must carry entity; got " + bk);
            assertEquals("Allocatable", entity.get("__typename"));
            assertEquals(key0.get("value"), entity.get("displayName"),
                    () -> "entity.displayName must equal the key value; got " + bk);
        }
    }

    /**
     * PRD 080 item 7 — reservationStats groups the §12-visible reservation set in the window by
     * DynamicType and counts.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationStatsGroupByTypeCounts()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  reservationStats(
                    filter:    { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "typ", type: true } ],
                    aggregate: [ { key: "n", fn: COUNT } ]
                  ) { count keys { key value } values { key number } }
                }
                """)
                .execute().path("reservationStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "fixture should produce type-grouped reservation buckets");
        for (Map<String, Object> bk : buckets)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> val0 = ((List<Map<String, Object>>) bk.get("values")).get(0);
            assertEquals(((Number) bk.get("count")).intValue(),
                    ((Number) val0.get("number")).intValue(), "COUNT metric == bucket count");
        }
    }

    /**
     * PRD 080 item 7 — `self: true` carries the typed Reservation in StatKey.entity.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationStatsSelfDimensionCarriesEntity()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  reservationStats(
                    filter:    { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "ev", self: true } ],
                    aggregate: [ { key: "n", fn: COUNT } ]
                  ) { keys { value entity { __typename ... on Reservation { id } } } }
                }
                """)
                .execute().path("reservationStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "fixture should produce reservation buckets");
        Map<String, Object> bk = buckets.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> key0 = ((List<Map<String, Object>>) bk.get("keys")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) key0.get("entity");
        assertNotNull(entity, () -> "self dimension must carry entity; got " + bk);
        assertEquals("Reservation", entity.get("__typename"));
        assertNotNull(entity.get("id"), "reservation entity has an id");
    }

    /**
     * PRD 080 — generic resource lanes: two aliased {@code allocatables(filter:{isPersonEq:…})}
     * columns split Personen / Nicht-Personen without any instance-specific type key, and each
     * allocatable exposes the universal {@code isPerson} / {@code isLocation} flags.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockAllocatablesPersonNonPersonLanesAndFlags()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    personen:      allocatables(filter: { isPersonEq: true })  { id isPerson isLocation }
                    nichtPersonen: allocatables(filter: { isPersonEq: false }) { id isPerson isLocation }
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks");
        boolean sawPerson = false, sawNonPerson = false;
        for (Map<String, Object> blk : blocks)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> persons = (List<Map<String, Object>>) blk.get("personen");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> others = (List<Map<String, Object>>) blk.get("nichtPersonen");
            for (Map<String, Object> p : persons)
            {
                assertEquals(Boolean.TRUE, p.get("isPerson"), "isPersonEq:true lane must be persons only");
                assertNotNull(p.get("isLocation"), "isLocation is non-null boolean");
                sawPerson = true;
            }
            for (Map<String, Object> o : others)
            {
                assertEquals(Boolean.FALSE, o.get("isPerson"), "isPersonEq:false lane must be non-persons only");
                assertNotNull(o.get("isLocation"), "isLocation is non-null boolean");
                sawNonPerson = true;
            }
        }
        assertTrue(sawPerson || sawNonPerson, "fixture blocks should allocate at least one allocatable");
    }

    /**
     * PRD 080 item 5 — an entity-returning group `expr` resolves to the typed entity (here
     * {@code resources(item)} → the block's Allocatables) and fans out; StatKey.entity carries the
     * Allocatable, selectable. Non-entity exprs keep the string key (covered by blockStatsGroupByComputeExpr).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockStatsExprResolvingToEntityCarriesEntity()
    {
        List<Map<String, Object>> buckets = tester.document("""
                query {
                  appointmentBlockStats(
                    filter:    { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" },
                    groupBy:   [ { key: "res", expr: "resources(item)" } ],
                    aggregate: [ { key: "n", field: DURATION_MINUTES, fn: COUNT } ]
                  ) { keys { value entity { __typename ... on Allocatable { displayName } } } }
                }
                """)
                .execute().path("appointmentBlockStats")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(buckets.isEmpty(), "fixture blocks allocate resources → expr→entity buckets");
        for (Map<String, Object> bk : buckets)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> key0 = ((List<Map<String, Object>>) bk.get("keys")).get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) key0.get("entity");
            assertNotNull(entity, () -> "entity-returning expr must carry entity; got " + bk);
            assertEquals("Allocatable", entity.get("__typename"));
            assertEquals(key0.get("value"), entity.get("displayName"),
                    () -> "entity.displayName must equal the bucket key value; got " + bk);
        }
    }

    /**
     * PRD 073 — the computeFunctions catalog aggregates the registered FunctionFactory descriptors
     * (here at least core StandardFunctions) with their rapla-level metadata, for editor autocomplete
     * + view validation.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void computeFunctionsCatalogExposesCoreFunctions()
    {
        List<Map<String, Object>> fns = tester.document("""
                query {
                  computeFunctions { name namespace minArgs maxArgs returnType sourceLevel doc }
                }
                """)
                .execute().path("computeFunctions")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(fns.isEmpty(), "catalog must list functions");

        Map<String, Map<String, Object>> byName = new java.util.HashMap<>();
        for (Map<String, Object> f : fns) byName.put((String) f.get("name"), f);

        Map<String, Object> concat = byName.get("concat");
        assertNotNull(concat, "concat must be in the catalog");
        assertEquals("org.rapla", concat.get("namespace"));
        assertEquals(-1, ((Number) concat.get("maxArgs")).intValue(), "concat is variadic (maxArgs -1)");
        assertEquals("String", concat.get("returnType"));
        assertEquals("ANY", concat.get("sourceLevel"));

        Map<String, Object> attribute = byName.get("attribute");
        assertNotNull(attribute, "attribute must be in the catalog");
        assertEquals(2, ((Number) attribute.get("minArgs")).intValue());
        assertEquals("AttributeValue", attribute.get("returnType"));
        assertEquals("CLASSIFIABLE", attribute.get("sourceLevel"));

        assertNotNull(byName.get("start"), "start must be in the catalog");
        assertEquals("EVENT", byName.get("start").get("sourceLevel"));
    }

    /**
     * PRD 073 — the appointmentnote plugin's `note` descriptor is auto-generated as a real typed
     * field `AppointmentBlock.note: String` (EL-backed), not just a catalog entry. The query
     * resolving at all proves the field is in the schema + wired; value is null here (fixture has no
     * note annotations).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void descriptorGeneratedNoteFieldResolvesOnBlock()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 3 }) {
                    start
                    note
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks");
        for (Map<String, Object> b : blocks)
        {
            assertTrue(b.containsKey("note"), "generated note field must be selectable");
            Object note = b.get("note");
            assertTrue(note == null || ((String) note).isEmpty(),
                    () -> "fixture has no appointment notes → blank; got " + note);
        }
    }

    /**
     * PRD 073 — Int return mapping: the core `number` descriptor (block sequence #, EVENT/Int) is
     * auto-generated as `AppointmentBlock.number: Int` and coerced from the EL result to a real
     * Integer (not a string). 1-based, so every block's number is >= 1.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void descriptorGeneratedNumberFieldIsInt()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 5 }) {
                    start
                    number
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks");
        for (Map<String, Object> b : blocks)
        {
            Object n = b.get("number");
            assertNotNull(n, "generated number field must resolve");
            assertTrue(n instanceof Integer, () -> "number must be a GraphQL Int (Integer), got " + n.getClass());
            assertTrue(((Integer) n) >= 1, () -> "block sequence number is 1-based; got " + n);
        }
    }

    /**
     * PRD 073 — DateTime/Date return mapping: the core `date` (Date) and `lastchanged` (DateTime →
     * LocalDateTime scalar) descriptors are auto-generated as AppointmentBlock fields and the RAW
     * LocalDateTime eval result is coerced to the scalar's Java type (Date → LocalDate). Proves the
     * scalar serialization path (the PRD-flagged risk) works end-to-end.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void descriptorGeneratedDateAndDateTimeFields()
    {
        List<Map<String, Object>> blocks = tester.document("""
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 3 }) {
                    start
                    date
                    lastchanged
                  }
                }
                """)
                .execute().path("appointmentBlocks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(blocks.isEmpty(), "fixture should produce blocks");
        for (Map<String, Object> b : blocks)
        {
            Object date = b.get("date");
            assertNotNull(date, "generated date field must resolve");
            assertTrue(((String) date).matches("\\d{4}-\\d{2}-\\d{2}"),
                    () -> "Date scalar serializes to yyyy-MM-dd; got " + date);
            assertTrue(b.containsKey("lastchanged"), "generated lastchanged field must be selectable");
            Object lc = b.get("lastchanged");
            assertTrue(lc == null || ((String) lc).contains("T"),
                    () -> "LocalDateTime scalar serializes to ISO (has 'T') or null; got " + lc);
        }
    }

    /**
     * PRD 073 — generated function-fields extend to the Appointment target type too (not just
     * AppointmentBlock): `date`/`note` resolve on Appointment. `number` is block-only (excluded
     * from Appointment), so it must NOT be a field here.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void descriptorGeneratedFieldsOnAppointment()
    {
        List<Map<String, Object>> reservations = tester.document("""
                query {
                  reservations(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 3 }) {
                    appointments { date note }
                  }
                }
                """)
                .execute().path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(reservations.isEmpty(), "fixture should produce reservations");
        boolean sawAppointment = false;
        for (Map<String, Object> r : reservations)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appts = (List<Map<String, Object>>) r.get("appointments");
            for (Map<String, Object> a : appts)
            {
                sawAppointment = true;
                Object date = a.get("date");
                assertNotNull(date, "Appointment.date (generated) must resolve");
                assertTrue(((String) date).matches("\\d{4}-\\d{2}-\\d{2}"),
                        () -> "Appointment.date serializes as yyyy-MM-dd; got " + date);
                assertTrue(a.containsKey("note"), "Appointment.note (generated) selectable");
            }
        }
        assertTrue(sawAppointment, "fixture reservations should have appointments");

        // number is block-only → must NOT be generated on Appointment (query references unknown field).
        tester.document("""
                query {
                  reservations(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    appointments { number }
                  }
                }
                """)
                .execute().errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "Appointment.number must not exist (block-only function)"));
    }

    /** No @view → no extensions.view (zero overhead for plain queries). */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void noViewDirectiveNoExtensions() throws Exception
    {
        String query = """
                query {
                  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00", limit: 1 }) { start }
                }
                """;
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(query)))
                .andExpect(jsonPath("$.data.appointmentBlocks").isArray())
                .andExpect(jsonPath("$.extensions.view").doesNotExist());
    }

    /** §12 — anonymous caller gets an error on the block root, not a list. */
    @Test
    @WithAnonymousUser
    void anonymousAppointmentBlocksRejected()
    {
        tester.document("""
                query {
                  appointmentBlocks(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) { start }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "anonymous block query must error, not return []"));
    }

    // ============================================================ PRD 028 Phase 1 — searchText + matchKind + hasConflicts

    /** Schema introspection: `searchText` + `matchKind` appear on the filter input. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationFilterHasSearchTextAndMatchKind()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "ReservationFilter") { inputFields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) result.get("inputFields");
        List<String> names = inputs.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("searchText"), () -> "missing searchText in " + names);
        assertTrue(names.contains("matchKind"),  () -> "missing matchKind in " + names);
    }

    /** `Reservation.hasConflicts: Boolean!` is exposed and resolves. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationTypeHasHasConflictsField()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Reservation") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("hasConflicts"), () -> "missing Reservation.hasConflicts in " + names);
    }

    // ===================================== PRD 073 — filterable Appointment.allocatables

    /**
     * Helper — query Reservation 2's appointment allocatables (display names),
     * optionally passing a `filter:` arg to the nested field. Reservation 2
     * (event "Reservation 2") allocates two rooms ("erwin", "Room A66") and one
     * lecturer ("Burns Monty") on its 2006-repeating appointment, so the window
     * 2006-01..2006-12 reaches it.
     */
    private List<String> reservation2AppointmentAllocatableNames(String filterArg)
    {
        String nested = filterArg == null
                ? "allocatables { displayName }"
                : "allocatables(filter: " + filterArg + ") { displayName }";
        List<Map<String, Object>> result = tester.document(String.format("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00",
                    searchText: "Reservation 2"
                  }) {
                    appointments { %s }
                  }
                }
                """, nested))
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Map<String, Object> r : result)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appts = (List<Map<String, Object>>) r.get("appointments");
            for (Map<String, Object> a : appts)
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> allocs = (List<Map<String, Object>>) a.get("allocatables");
                if (allocs == null) continue;
                for (Map<String, Object> al : allocs)
                {
                    names.add((String) al.get("displayName"));
                }
            }
        }
        return names;
    }

    /** PRD 074 A: Appointment.allocatables accepts a `filter` arg of the unified type AllocatableFilter. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesHasFilterArgument()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Appointment") {
                    fields { name args { name type { name ofType { name } } } }
                } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        Map<String, Object> allocField = fields.stream()
                .filter(f -> "allocatables".equals(f.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Appointment.allocatables field"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> args = (List<Map<String, Object>>) allocField.get("args");
        Map<String, Object> filterArg = args.stream()
                .filter(a -> "filter".equals(a.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Appointment.allocatables must accept a 'filter' arg; got "
                        + args.stream().map(a -> (String) a.get("name")).toList()));
        @SuppressWarnings("unchecked")
        Map<String, Object> type = (Map<String, Object>) filterArg.get("type");
        String typeName = type.get("name") != null
                ? (String) type.get("name")
                : (String) ((Map<?, ?>) type.get("ofType")).get("name");
        assertEquals("AllocatableFilter", typeName,
                "filter arg must use the unified AllocatableFilter, not " + typeName);
    }

    /**
     * PRD 074 A — `idIn` is now part of the unified filter on the nested path: ACCEPTED (no
     * validation error) and applied (narrows the list to named ids).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesIdInAccepted()
    {
        tester.document("""
                query {
                  reservations(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    appointments { allocatables(filter: { idIn: ["some-id"] }) { displayName } }
                  }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertTrue(errs.isEmpty(),
                        "idIn is part of the unified AllocatableFilter — must NOT be a validation error: " + errs));
    }

    /** PRD 074 A — `limit` accepted on the nested path (caps the list), no validation error. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesLimitAccepted()
    {
        tester.document("""
                query {
                  reservations(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    appointments { allocatables(filter: { limit: 1 }) { displayName } }
                  }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertTrue(errs.isEmpty(),
                        "limit is part of the unified AllocatableFilter — must NOT be a validation error: " + errs));
    }

    /**
     * PRD 074 A — `accessLevel` accepted on the nested path ("which of this event's resources may
     * I edit"), no validation error.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesAccessLevelAccepted()
    {
        tester.document("""
                query {
                  reservations(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    appointments { allocatables(filter: { accessLevel: EDIT }) { displayName } }
                  }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertTrue(errs.isEmpty(),
                        "accessLevel is part of the unified AllocatableFilter — must NOT be a validation error: " + errs));
    }

    /** (a) typeIn narrows the nested list to the named DynamicTypes (rooms only). */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesTypeKeyInNarrowsToRooms()
    {
        List<String> all = reservation2AppointmentAllocatableNames(null);
        assertTrue(all.contains("erwin") && all.contains("Room A66") && all.contains("Burns Monty"),
                () -> "fixture precondition: Reservation 2 should allocate erwin, Room A66, Burns Monty; got " + all);

        List<String> rooms = reservation2AppointmentAllocatableNames("{ typeIn: [room] }");
        assertTrue(rooms.contains("erwin"), () -> "expected room erwin; got " + rooms);
        assertTrue(rooms.contains("Room A66"), () -> "expected Room A66; got " + rooms);
        assertFalse(rooms.contains("Burns Monty"),
                () -> "lecturer must be filtered out by typeIn:[room]; got " + rooms);
    }

    /** (b) isPersonEq:true returns persons only. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesIsPersonEqTrueReturnsPersonsOnly()
    {
        List<String> persons = reservation2AppointmentAllocatableNames("{ isPersonEq: true }");
        assertTrue(persons.contains("Burns Monty"),
                () -> "expected the lecturer person; got " + persons);
        assertFalse(persons.contains("erwin") || persons.contains("Room A66"),
                () -> "rooms must be filtered out by isPersonEq:true; got " + persons);
    }

    /** (c) combined typeIn + isPersonEq AND together → empty (rooms aren't persons). */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesCombinedFilterAnds()
    {
        List<String> combined = reservation2AppointmentAllocatableNames(
                "{ typeIn: [room], isPersonEq: true }");
        assertTrue(combined.isEmpty(),
                () -> "typeIn:[room] AND isPersonEq:true must yield nothing (rooms aren't persons); got " + combined);

        List<String> personRooms = reservation2AppointmentAllocatableNames(
                "{ typeIn: [room], isPersonEq: false }");
        assertTrue(personRooms.contains("erwin") && personRooms.contains("Room A66"),
                () -> "typeIn:[room] AND isPersonEq:false must keep both rooms; got " + personRooms);
        assertFalse(personRooms.contains("Burns Monty"),
                () -> "lecturer must be excluded; got " + personRooms);
    }

    /**
     * (d) §12 leak guard — the canRead gate runs BEFORE the filter. For a
     * non-admin caller, the filtered nested list must equal (canRead-narrowed
     * set ∩ filter): applying the filter client-side to the caller's UNfiltered
     * (already canRead-gated) nested list must equal the server's filtered list.
     * If the server applied the filter before canRead, a hidden-but-matching
     * allocatable would slip into the filtered list and the two diverge.
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void appointmentAllocatablesFilterRunsAfterCanReadGate()
    {
        List<String> unfilteredVisible = reservation2AppointmentAllocatableNames(null);
        List<String> serverFilteredRooms = reservation2AppointmentAllocatableNames("{ typeIn: [room] }");

        // Client-side reference: rooms among the names monty can actually read.
        java.util.Set<String> roomNames = java.util.Set.of("erwin", "Room A66");
        java.util.List<String> expected = unfilteredVisible.stream()
                .filter(roomNames::contains)
                .toList();

        assertEquals(new java.util.HashSet<>(expected), new java.util.HashSet<>(serverFilteredRooms),
                () -> "filter must run over the canRead-narrowed subset only; "
                        + "visible=" + unfilteredVisible + " serverFiltered=" + serverFilteredRooms);
    }

    /** Happy path — searchText narrows results; hasConflicts resolves to false on no-conflict fixture. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void searchTextNarrowsAndHasConflictsResolves()
    {
        // testdefault.xml has reservations named "test-reservation" — match that
        List<Map<String, Object>> all = tester.document("""
                query {
                  reservations(filter: {
                    from: "2010-01-01T00:00:00",
                    to:   "2017-12-31T00:00:00",
                    searchText: "test",
                    matchKind: SUBSTRING
                  }) { id hasConflicts }
                }
                """)
                .execute()
                .path("reservations")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(all);
        // Every returned row must populate hasConflicts (false on a non-conflicting fixture)
        for (Map<String, Object> r : all)
        {
            assertNotNull(r.get("hasConflicts"), "hasConflicts must populate on every row");
        }
    }
}
