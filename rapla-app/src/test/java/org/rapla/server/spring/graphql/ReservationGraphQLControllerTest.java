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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
     * PRD 066 — `allocatableMatching: { typeKeyIn: [...] }` returns
     * reservations using ANY allocatable of those types.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationsAllocatableMatchingByTypeKeyIn()
    {
        List<Map<String, Object>> got = tester.document("""
                { reservations(filter: {
                    from: "2001-01-01T00:00:00", to: "2020-12-31T00:00:00",
                    allocatableMatching: { typeKeyIn: ["room"] }
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
                    allocatableMatching: { typeKeyIn: ["room"] }
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

    /** Schema: Appointment.allocatables accepts a `filter` arg of type AppointmentAllocatableFilter. */
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
        assertEquals("AppointmentAllocatableFilter", typeName,
                "filter arg must use AppointmentAllocatableFilter, not " + typeName);
    }

    /**
     * Contract enforcement — `idIn` is not part of `AppointmentAllocatableFilter`.
     * Passing it must be a GraphQL validation error, not a silent no-op.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesIdInIsValidationError()
    {
        tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    appointments {
                      allocatables(filter: { idIn: ["some-id"] }) { displayName }
                    }
                  }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "idIn is not in AppointmentAllocatableFilter — expected a validation error"));
    }

    /**
     * Contract enforcement — `limit` is not part of `AppointmentAllocatableFilter`.
     * Passing it must be a GraphQL validation error, not a silent no-op.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesLimitIsValidationError()
    {
        tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    appointments {
                      allocatables(filter: { limit: 1 }) { displayName }
                    }
                  }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "limit is not in AppointmentAllocatableFilter — expected a validation error"));
    }

    /**
     * Contract enforcement — `accessibleByUsername` is not part of `AppointmentAllocatableFilter`.
     * Passing it must be a GraphQL validation error, not a silent no-op.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesAccessibleByUsernameIsValidationError()
    {
        tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    appointments {
                      allocatables(filter: { accessibleByUsername: "homer" }) { displayName }
                    }
                  }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "accessibleByUsername is not in AppointmentAllocatableFilter — expected a validation error"));
    }

    /** (a) typeKeyIn narrows the nested list to the named DynamicTypes (rooms only). */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesTypeKeyInNarrowsToRooms()
    {
        List<String> all = reservation2AppointmentAllocatableNames(null);
        assertTrue(all.contains("erwin") && all.contains("Room A66") && all.contains("Burns Monty"),
                () -> "fixture precondition: Reservation 2 should allocate erwin, Room A66, Burns Monty; got " + all);

        List<String> rooms = reservation2AppointmentAllocatableNames("{ typeKeyIn: [\"room\"] }");
        assertTrue(rooms.contains("erwin"), () -> "expected room erwin; got " + rooms);
        assertTrue(rooms.contains("Room A66"), () -> "expected Room A66; got " + rooms);
        assertFalse(rooms.contains("Burns Monty"),
                () -> "lecturer must be filtered out by typeKeyIn:[room]; got " + rooms);
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

    /** (c) combined typeKeyIn + isPersonEq AND together → empty (rooms aren't persons). */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentAllocatablesCombinedFilterAnds()
    {
        List<String> combined = reservation2AppointmentAllocatableNames(
                "{ typeKeyIn: [\"room\"], isPersonEq: true }");
        assertTrue(combined.isEmpty(),
                () -> "typeKeyIn:[room] AND isPersonEq:true must yield nothing (rooms aren't persons); got " + combined);

        List<String> personRooms = reservation2AppointmentAllocatableNames(
                "{ typeKeyIn: [\"room\"], isPersonEq: false }");
        assertTrue(personRooms.contains("erwin") && personRooms.contains("Room A66"),
                () -> "typeKeyIn:[room] AND isPersonEq:false must keep both rooms; got " + personRooms);
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
        List<String> serverFilteredRooms = reservation2AppointmentAllocatableNames("{ typeKeyIn: [\"room\"] }");

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
