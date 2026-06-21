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
 * PRD 081 Phase 1 — tier-3 tests for {@code Query.search} (omnibox multisearch).
 * Mirrors {@link ClassificationGraphQLControllerTest}'s setup: testdefault.xml
 * fixture via @TempDir, MockMvc → HttpGraphQlTester, security filters off so
 * {@code @WithMockUser} reaches the resolver.
 *
 * <p>Fixture-relevant facts (see {@code testdefault.xml}):
 * <ul>
 *   <li>Reservations (dynatt:event), all owned by homer (admin): "Reservation 2",
 *       "test-reservation" (×2), "power planting", "bowling", "Test". Their
 *       appointments fall in 2001–2016 — far outside any default window, which
 *       is exactly why EVENT search must be WINDOWLESS (OQ1).</li>
 *   <li>Rooms: "Room A66" (seats=30), "erwin" (seats=10). Lecturers: Simpson
 *       Homer, Burns Monty.</li>
 *   <li>Users: homer (admin), monty (non-admin).</li>
 * </ul>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchGraphQLControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};

    /** SearchHit selection set reused across tests. */
    private static final String HIT_FIELDS = """
            __typename id label sublabel score
            ... on ResourceHit { allocatable { id } }
            ... on EventHit { reservation { id } firstOccurrenceStart }
            """;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = SearchGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    // === helpers =============================================================

    private List<Map<String, Object>> groups(String query, String kindsArg)
    {
        String kinds = kindsArg == null ? "" : ", kinds: " + kindsArg;
        return tester.document("{ search(query: \"" + query + "\"" + kinds + ") { groups { kind heading hits { "
                        + HIT_FIELDS + " } } } }")
                .execute()
                .path("search.groups")
                .entityList(MAP)
                .get();
    }

    private List<Map<String, Object>> groups(String query, String kindsArg, int limit)
    {
        String kinds = kindsArg == null ? "" : ", kinds: " + kindsArg;
        return tester.document("{ search(query: \"" + query + "\"" + kinds + ", limit: " + limit
                        + ") { groups { kind heading hits { " + HIT_FIELDS + " } } } }")
                .execute()
                .path("search.groups")
                .entityList(MAP)
                .get();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bucket(List<Map<String, Object>> groups, String kind)
    {
        return groups.stream()
                .filter(g -> kind.equals(g.get("kind")))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> hits(List<Map<String, Object>> groups, String kind)
    {
        Map<String, Object> b = bucket(groups, kind);
        return b == null ? List.of() : (List<Map<String, Object>>) b.get("hits");
    }

    // === auth ================================================================

    @Test
    @WithAnonymousUser
    void anonymousSearchRejected()
    {
        tester.document("{ search(query: \"test\") { groups { kind } } }")
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous search must error");
                    assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                            () -> "expected UNAUTHENTICATED; got " + errs);
                });
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blankTermReturnsNoGroups()
    {
        assertTrue(groups("   ", null).isEmpty(), "blank term must yield no groups");
    }

    // === structure + buckets =================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminSearchBucketsByKindWithTypename()
    {
        // "test" matches the lecturer/room? no — matches event names
        // (test-reservation ×2, Test). Use a term that also hits a resource:
        // "room" matches Room A66 (resource) + nothing in events.
        List<Map<String, Object>> g = groups("room", null);
        Map<String, Object> resources = bucket(g, "RESOURCE");
        assertNotNull(resources, () -> "expected a RESOURCE bucket for 'room', got " + g);
        assertEquals("Ressourcen", resources.get("heading"));
        List<Map<String, Object>> rHits = hits(g, "RESOURCE");
        assertFalse(rHits.isEmpty(), "RESOURCE bucket must have hits");
        rHits.forEach(h -> {
            assertEquals("ResourceHit", h.get("__typename"));
            assertNotNull(h.get("label"), () -> "hit missing label: " + h);
            assertNotNull(h.get("score"), () -> "hit missing score: " + h);
            assertNotNull(((Map<?, ?>) h.get("allocatable")).get("id"), () -> "ResourceHit must carry allocatable.id: " + h);
        });
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void eventSearchIsWindowless()
    {
        // "bowling" is an event whose appointments are in 2001/2002 — far
        // outside any plausible default window. A windowless scan still finds it.
        List<Map<String, Object>> g = groups("bowling", null);
        List<Map<String, Object>> events = hits(g, "EVENT");
        assertEquals(1, events.size(), () -> "expected exactly the 'bowling' event, got " + events);
        Map<String, Object> hit = events.get(0);
        assertEquals("EventHit", hit.get("__typename"));
        assertEquals("bowling", hit.get("label"));
        assertNotNull(((Map<?, ?>) hit.get("reservation")).get("id"), () -> "EventHit must carry reservation.id: " + hit);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void kindsFilterRestrictsToRequestedBucket()
    {
        // "test" matches events; restricting kinds:[RESOURCE] must drop the
        // EVENT bucket entirely even though events match.
        List<Map<String, Object>> g = groups("test", "[RESOURCE]");
        assertTrue(bucket(g, "EVENT") == null, () -> "EVENT bucket must be absent when kinds:[RESOURCE]; got " + g);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void limitCapsPerKind()
    {
        // "test" matches 3 events (test-reservation ×2, Test). limit:1 caps the
        // EVENT bucket to 1.
        List<Map<String, Object>> uncapped = groups("test", "[EVENT]");
        assertTrue(hits(uncapped, "EVENT").size() >= 2, () -> "fixture should have ≥2 'test' events, got " + uncapped);
        List<Map<String, Object>> capped = groups("test", "[EVENT]", 1);
        assertEquals(1, hits(capped, "EVENT").size(), () -> "limit:1 must cap EVENT bucket to 1, got " + capped);
    }

    // === fuzzy asymmetry (OQ3) ==============================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void resourceMatchingIsFuzzy()
    {
        // "erwen" is edit-distance 1 from the room "erwin" — FUZZY (on by
        // default for RESOURCE) finds it; plain substring would not.
        List<Map<String, Object>> g = groups("erwen", "[RESOURCE]");
        List<Map<String, Object>> rHits = hits(g, "RESOURCE");
        assertTrue(rHits.stream().anyMatch(h -> "erwin".equals(h.get("label"))),
                () -> "FUZZY resource search should find 'erwin' for 'erwen', got " + rHits);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void eventMatchingIsNotFuzzy()
    {
        // "bowleng" is edit-distance 1 from the event "bowling". EVENT search
        // is SUBSTRING-only (never fuzzy — the scan could be huge), so it must
        // NOT match, even though the resource path would have.
        List<Map<String, Object>> g = groups("bowleng", "[EVENT]");
        assertTrue(hits(g, "EVENT").isEmpty(),
                () -> "EVENT search must not fuzzy-match 'bowling' for 'bowleng', got " + g);
        // Sanity: the exact substring still matches (proves the term/fixture are right).
        assertEquals(1, hits(groups("bowling", "[EVENT]"), "EVENT").size());
    }

    // === §12 + editable-only gating ==========================================

    /**
     * RESOURCE bucket §12: monty's search resource ids MUST equal exactly the
     * ids the §12-vetted {@code allocatables(filter:)} resolver returns for the
     * same term — no hidden allocatable (e.g. "Room A66", which monty cannot
     * read) may leak through search, and search must not drop a permitted one.
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void resourceSearchExposesExactlyPermittedAllocatables()
    {
        java.util.Set<String> allowed = new java.util.HashSet<>();
        tester.document("""
                { allocatables(filter: { searchText: "room", matchKind: FUZZY }) { id } }
                """)
                .execute()
                .path("allocatables")
                .entityList(MAP)
                .get()
                .forEach(a -> allowed.add((String) a.get("id")));

        java.util.Set<String> searchIds = new java.util.HashSet<>();
        hits(groups("room", "[RESOURCE]"), "RESOURCE")
                .forEach(h -> searchIds.add((String) ((Map<?, ?>) h.get("allocatable")).get("id")));

        assertEquals(allowed, searchIds,
                () -> "search RESOURCE bucket must equal §12-readable allocatables for the term — "
                        + "leak if different. allowed=" + allowed + " search=" + searchIds);
    }

    /**
     * EVENT bucket is EDIT-gated, not read-gated: the omnibox surfaces only
     * events the caller can edit. monty CAN read homer's "test" events (the
     * event type is world-readable) but CANNOT edit them — so the EVENT bucket
     * must be empty. This is both the editable-only restriction and the leak
     * guard: dropping the {@code canModify} gate would surface homer's events
     * here. (Verified red-on-leak: replacing canModify with canRead turns this
     * assertion red.)
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void eventSearchReturnsOnlyEditableEvents()
    {
        // Sanity: monty CAN read homer's "test" events via the read path — they
        // exist and are visible, so an empty search bucket is due to edit-gating,
        // not absence.
        List<Map<String, Object>> readable = tester.document("""
                { reservations(filter: { from: "2000-01-01T00:00:00", to: "2030-01-01T00:00:00",
                    searchText: "test" }) { id } }
                """)
                .execute()
                .path("reservations")
                .entityList(MAP)
                .get();
        assertFalse(readable.isEmpty(), "precondition: monty must be able to READ homer's 'test' events");

        // But the edit-gated omnibox surfaces none of them (monty can't edit).
        assertTrue(hits(groups("test", "[EVENT]"), "EVENT").isEmpty(),
                () -> "edit-gated EVENT search must not surface events monty cannot edit; got "
                        + groups("test", "[EVENT]"));
    }

    /**
     * Positive edit-gate: an admin (homer) can edit every event, so the EVENT
     * bucket DOES surface matching events — confirms the gate doesn't over-filter.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminSeesEditableEventsInBucket()
    {
        assertFalse(hits(groups("test", "[EVENT]"), "EVENT").isEmpty(),
                "admin can edit all events — EVENT bucket must be populated");
    }
}
