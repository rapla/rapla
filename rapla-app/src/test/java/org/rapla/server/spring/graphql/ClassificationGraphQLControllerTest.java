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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 035 Cut C — tier-3 tests for allocatables, dynamic types, the
 * Classification interface, and the generated per-DynamicType classification
 * types. Mirrors the {@link HelloGraphQLControllerTest} setup: testdefault.xml
 * fixture via @TempDir, MockMvc → HttpGraphQlTester, security filter chain
 * bypassed so @WithMockUser actually reaches the resolver.
 *
 * <p>Fixture relevant facts (see {@code rapla-app/src/test/resources/testdefault.xml}):
 * <ul>
 *   <li>DynamicTypes: {@code dynatt:resource1} / {@code dynatt:resource2} /
 *       {@code dynatt:resource3} / {@code dynatt:room} (with attrs name, seats,
 *       belongsto) / {@code dynatt:lecturer} (person) / {@code dynatt:event}
 *       (reservation).</li>
 *   <li>Rooms: "Room A66" (seats=30, allocate_conflicts permission), "erwin"
 *       (seats=10, allocate permission). Both are dynatt:room.</li>
 *   <li>Users: homer (admin), monty (non-admin).</li>
 * </ul>
 *
 * <p>The generated GraphQL type for {@code dynatt:room} is named
 * {@code roomClassification} (sanitized PascalCase per
 * {@link ClassificationSdlGenerator#checkGraphQlCompliantName(String)}).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ClassificationGraphQLControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ClassificationGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    // === types / type ========================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void typesQueryReturnsAllDynamicTypes()
    {
        // rapla's XML reader strips the dynatt: namespace prefix at parse time
        // (see DynamicTypeReader line 76 — substring after ':'), so actual keys
        // are bare: room / lecturer / event / resource1 / resource2 / resource3.
        List<String> keys = tester.document("{ types { key } }")
                .execute()
                .path("types[*].key")
                .entityList(String.class)
                .get();
        assertTrue(keys.contains("room"),     () -> "expected 'room' in " + keys);
        assertTrue(keys.contains("lecturer"), () -> "expected 'lecturer' in " + keys);
        assertTrue(keys.contains("event"),    () -> "expected 'event' in " + keys);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void typeByKeyReturnsBasicMetadataNoAttributes()
    {
        // β refactor 2026-05-28: DynamicType no longer carries an `attributes`
        // field. Attribute metadata lives on the generated typed classification
        // (roomClassification) via introspection + custom directives.
        Map<String, Object> room = tester.document("""
                { type(key: "room") { key name classificationType } }
                """)
                .execute()
                .path("type")
                .entity(Map.class)
                .get();
        assertEquals("room", room.get("key"));
        assertEquals("RESOURCE", room.get("classificationType"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void typeByUnknownKeyReturnsNull()
    {
        tester.document("{ type(key: \"does-not-exist\") { key } }")
                .execute()
                .path("type")
                .valueIsNull();
    }

    /**
     * β refactor 2026-05-28 — AttributeDescriptor replaced by SDL directives
     * + introspection. Verify the field types on the generated classification
     * still encode the constraint info:
     *  - CATEGORY ORGANIZATION attr → field type `Category`
     *  - CATEGORY VALUE_LIST attr → field type is the generated enum
     *  - ALLOCATABLE attr → field type `Allocatable`
     * testdefault.xml: dynatt:room.belongsto is CATEGORY-typed with root=department.
     *
     * Directive presence ({@code @rootCategory}, {@code @expectedType},
     * {@code @displayName}, {@code @multiplicity}, {@code @required}) is
     * verified at the SDL-generator unit test layer; introspection of
     * `appliedDirectives` requires a graphql-java feature flag that
     * spring-graphql 2.0.3 doesn't enable.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void categoryFieldCarriesGeneratedEnumType()
    {
        // testdefault.xml's `department` root is VALUE_LIST (depth-1; classified
        // by CategoryKindClassifier). The CATEGORY-typed `belongsto` attribute
        // therefore generates as the `Department` enum, not `Category`.
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "roomClassification") {
                    fields { name type { name kind } }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        Map<String, Object> belongsto = fields.stream()
                .filter(f -> "belongsto".equals(f.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("belongsto field missing on roomClassification"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldType = (Map<String, Object>) belongsto.get("type");
        assertEquals("department", fieldType.get("name"),
                () -> "belongsto field should be typed Department enum (VALUE_LIST root), got " + fieldType);
        assertEquals("ENUM", fieldType.get("kind"));
    }

    /**
     * β refactor — ALLOCATABLE attribute field is typed Allocatable. When
     * the attribute is multi-valued (testdefault.xml: resource2.a1 has
     * multi-select / belongs-to / package), the field type is a `[Allocatable!]`
     * list wrapper; need to introspect through `ofType` to confirm.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatableFieldCarriesAllocatableType()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "resource2Classification") {
                    fields {
                      name
                      type {
                        name kind
                        ofType { name kind ofType { name kind } }
                      }
                    }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        Map<String, Object> a1 = fields.stream()
                .filter(f -> "a1".equals(f.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("a1 field missing on resource2Classification"));
        // Walk type unwraps to find the leaf type name (handles `Allocatable`,
        // `[Allocatable!]`, `[Allocatable!]!` shapes).
        String leafName = leafTypeName(a1.get("type"));
        assertEquals("Allocatable", leafName,
                () -> "a1 field's leaf type should be Allocatable, got " + a1.get("type"));
    }

    @SuppressWarnings("unchecked")
    private static String leafTypeName(Object typeObj)
    {
        Map<String, Object> t = (Map<String, Object>) typeObj;
        while (t != null && t.get("name") == null)
        {
            t = (Map<String, Object>) t.get("ofType");
        }
        return t == null ? null : (String) t.get("name");
    }

    // === PRD 066 Phase 1 — idIn on AllocatableFilter =========================

    /**
     * Helper — resolve allocatable ids from the live schema by displayName,
     * so the tests survive id-format changes (UUID prefix, etc.). The
     * fixture has Springfield personas only (§17): rooms Room A66 / erwin,
     * lecturers Simpson Homer / Burns Monty.
     */
    private String idByDisplayName(String namePart)
    {
        List<Map<String, Object>> got = tester.document(String.format("""
                { allocatables(filter: { nameContains: "%s" }) { id displayName } }
                """, namePart))
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        return got.stream()
                .filter(a -> ((String) a.get("displayName")).contains(namePart))
                .map(a -> (String) a.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no allocatable found with displayName containing " + namePart));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterIdInReturnsExplicitSet()
    {
        String roomA66 = idByDisplayName("Room A66");
        List<Map<String, Object>> got = tester.document(String.format("""
                { allocatables(filter: { idIn: ["%s"] }) { id displayName } }
                """, roomA66))
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "expected just Room A66, got " + got);
        assertEquals(roomA66, got.get(0).get("id"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterIdInUnionsTypeKeyIn()
    {
        // typeKeyIn:["room"] picks all rooms; idIn adds a lecturer who isn't
        // a room. Result is the union.
        String simpsonHomer = idByDisplayName("Simpson Homer");
        List<Map<String, Object>> got = tester.document(String.format("""
                { allocatables(filter: {
                    typeKeyIn: ["room"]
                    idIn: ["%s"]
                  }) { id displayName } }
                """, simpsonHomer))
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // fixture has 2 rooms (Room A66, erwin) + the picked lecturer = 3
        assertEquals(3, got.size(), () -> "expected 2 rooms + 1 lecturer, got " + got);
        assertTrue(got.stream().anyMatch(a -> simpsonHomer.equals(a.get("id"))),
                () -> "Simpson Homer must appear via idIn: " + got);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterIdInIgnoresFilterRulesForExplicitPicks()
    {
        // Room A66 seats=30; erwin seats=10. whereRoom narrows to seats>=20
        // (only Room A66 matches). idIn adds erwin. Result is Room A66 + erwin.
        String erwin = idByDisplayName("erwin");
        List<Map<String, Object>> got = tester.document(String.format("""
                { allocatables(filter: {
                    typeKeyIn: ["room"]
                    whereRoom: { seats: { gte: 20 } }
                    idIn: ["%s"]
                  }) { id displayName classification { ... on roomClassification { seats } } } }
                """, erwin))
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, got.size(), () -> "expected Room A66 (filter-match) + erwin (id-pick), got " + got);
        assertTrue(got.stream().anyMatch(a -> erwin.equals(a.get("id"))),
                () -> "erwin must appear via idIn even though seats=10 fails the filter: " + got);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterIdInSilentlyDropsUnknownId()
    {
        String roomA66 = idByDisplayName("Room A66");
        List<Map<String, Object>> got = tester.document(String.format("""
                { allocatables(filter: {
                    idIn: ["%s", "definitely-not-an-id-on-this-server"]
                  }) { id displayName } }
                """, roomA66))
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // Unknown id is silently dropped; only Room A66 returned.
        assertEquals(1, got.size(), () -> "expected only Room A66 (unknown id dropped silently), got " + got);
        assertEquals(roomA66, got.get(0).get("id"));
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void allocatablesFilterIdInDoesNotBypassPermissionsAsNonAdmin()
    {
        // monty queries idIn with both a readable and a hidden id —
        // result must be byte-identical to the readable-only query
        // (§12 — existence not leaked, idIn does not bypass canRead).
        String roomA66 = idByDisplayName("Room A66");
        // Attempt to pick a synthetic id (definitely not visible).
        List<Map<String, Object>> mixed = tester.document(String.format("""
                { allocatables(filter: { idIn: ["%s", "definitely-hidden-or-unknown"] }) { id } }
                """, roomA66))
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        List<Map<String, Object>> readableOnly = tester.document(String.format("""
                { allocatables(filter: { idIn: ["%s"] }) { id } }
                """, roomA66))
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(readableOnly, mixed,
                () -> "mixed-id query must be byte-identical to readable-only — §12. mixed=" + mixed + " readableOnly=" + readableOnly);
    }

    // === §5d Phase 1 — generated <TypeKey>Where input SDL ====================

    /**
     * PRD 035 §5d Phase 1 — per resource/person DT the generator emits a
     * `<TypeKey>Where` input. Field types per attribute kind:
     *   STRING → StringWhere, INT → IntWhere, BOOLEAN → BooleanWhere,
     *   DATE → LocalDateTimeWhere, ALLOCATABLE (single) → AllocatableWhere,
     *   ALLOCATABLE (multi) → AllocatableListWhere,
     *   CATEGORY ORGANIZATION (single) → CategoryWhere,
     *   CATEGORY ORGANIZATION (multi) → CategoryListWhere,
     *   CATEGORY VALUE_LIST → generated `<Enum>Where` / `<Enum>ListWhere`.
     * Plus AND / OR / NOT combinators.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void roomWhereInputCarriesPerAttributePredicateTypes()
    {
        // testdefault.xml `room`: name(string), seats(int), belongsto(category, single).
        // The department root is treated as VALUE_LIST (depth-1) by §5b — so
        // belongsto's predicate is the generated `departmentWhere`, not CategoryWhere.
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "roomWhere") {
                    kind
                    inputFields {
                      name
                      type { name kind ofType { name kind ofType { name kind } } }
                    }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(result, "roomWhere input must be generated (PRD 035 §5d Phase 1)");
        assertEquals("INPUT_OBJECT", result.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("inputFields");
        Map<String, String> attrFieldTypes = new java.util.HashMap<>();
        for (Map<String, Object> f : fields) attrFieldTypes.put((String) f.get("name"), leafTypeName(f.get("type")));
        assertEquals("StringWhere",     attrFieldTypes.get("name"),       () -> "got: " + attrFieldTypes);
        assertEquals("IntWhere",        attrFieldTypes.get("seats"),      () -> "got: " + attrFieldTypes);
        assertEquals("departmentWhere", attrFieldTypes.get("belongsto"),  () -> "got: " + attrFieldTypes);
        assertEquals("roomWhere",       attrFieldTypes.get("AND"),        () -> "got: " + attrFieldTypes);
        assertEquals("roomWhere",       attrFieldTypes.get("OR"),         () -> "got: " + attrFieldTypes);
        assertEquals("roomWhere",       attrFieldTypes.get("NOT"),        () -> "got: " + attrFieldTypes);
    }

    /**
     * Phase 1 — reservation DynamicTypes do NOT get a `<TypeKey>Where` input.
     * Where on Reservations lands on `reservations(filter:)` and is its own
     * scope (deferred — PRD 035 §5d "Out of scope"). The fixture `event` DT
     * is reservation-kind; assert no `eventWhere` was emitted.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationDTGetsNoWhereInputInPhase1()
    {
        tester.document("{ __type(name: \"eventWhere\") { name } }")
                .execute()
                .path("__type")
                .valueIsNull();
    }

    /**
     * Phase 1 — per VALUE_LIST root, two enum *Where inputs are generated:
     *   <enum>Where     { eq, ne, in, isNull }
     *   <enum>ListWhere { contains, containsAny, containsAll, isEmpty, isNull }
     * testdefault.xml has the `department` VALUE_LIST root.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void enumWhereInputsGeneratedPerValueListRoot()
    {
        Map<String, Object> single = tester.document("""
                {
                  __type(name: "departmentWhere") {
                    kind
                    inputFields { name type { name ofType { name } } }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(single, "departmentWhere must be generated");
        assertEquals("INPUT_OBJECT", single.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> singleFields = (List<Map<String, Object>>) single.get("inputFields");
        java.util.Set<String> singleNames = new java.util.HashSet<>();
        for (Map<String, Object> f : singleFields) singleNames.add((String) f.get("name"));
        assertTrue(singleNames.containsAll(java.util.List.of("eq", "ne", "in", "isNull")),
                () -> "departmentWhere should have eq/ne/in/isNull, got " + singleNames);

        Map<String, Object> list = tester.document("""
                {
                  __type(name: "departmentListWhere") {
                    kind
                    inputFields { name }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(list, "departmentListWhere must be generated");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listFields = (List<Map<String, Object>>) list.get("inputFields");
        java.util.Set<String> listNames = new java.util.HashSet<>();
        for (Map<String, Object> f : listFields) listNames.add((String) f.get("name"));
        assertTrue(listNames.containsAll(java.util.List.of("contains", "containsAny", "containsAll", "isEmpty", "isNull")),
                () -> "departmentListWhere should have contains/containsAny/containsAll/isEmpty/isNull, got " + listNames);
    }

    /**
     * §5d Phase 2 — `AllocatableFilter` gains one `where<TypeKey>` field per
     * resource/person DT (generated SDL type-extension). Resolver accepts
     * a where-shape and (Phase 2 only) treats it as a no-op — query returns
     * the same set as without the where. Verifies the binder doesn't reject
     * the unknown-by-record field after the resolver switch.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void emptyWhereBlockDoesNotFilter()
    {
        // Empty where block stays a no-op across all phases — no predicates,
        // no constraint. Distinct from Phase 2's broader "where is always
        // no-op" check (superseded by Phase 3 predicate tests below).
        List<Map<String, Object>> baseline = tester.document("""
                { allocatables(filter: { typeKeyEq: "room" }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        List<Map<String, Object>> empty = tester.document("""
                { allocatables(filter: { typeKeyEq: "room", whereRoom: {} }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(baseline.size(), empty.size(), () -> "baseline=" + baseline + " empty=" + empty);
    }

    /**
     * PRD 074/059 — a {@code where<Type>} block acts as an IMPLICIT TYPE GATE (option B′): setting
     * {@code whereRoom} alone (no typeKeyEq/typeKeyIn) restricts to rooms — it does NOT leave all
     * other allocatable types unfiltered. So whereRoom-only equals typeKeyEq:"room"+whereRoom.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void whereBlockImpliesTypeGateWithoutTypeKey()
    {
        List<Map<String, Object>> withGate = tester.document("""
                { allocatables(filter: { typeKeyEq: "room", whereRoom: { seats: { gte: 20 } } }) { displayName } }
                """)
                .execute().path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}).get();
        List<Map<String, Object>> implicit = tester.document("""
                { allocatables(filter: { whereRoom: { seats: { gte: 20 } } }) { displayName } }
                """)
                .execute().path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}).get();
        // B′: whereRoom alone gates to rooms — identical to the explicit-type version (no persons/others leak in).
        assertEquals(withGate.size(), implicit.size(),
                () -> "whereRoom must imply room-gate; withGate=" + withGate + " implicit=" + implicit);
        assertEquals("Room A66", implicit.get(0).get("displayName"));
    }

    // === §5d Phase 3 — predicate evaluator, one operator per kind ============

    /**
     * testdefault.xml fixture for Phase 3 tests:
     *   - "Room A66" (room, seats=30, belongsto=springfield-powerplant)
     *   - "erwin"    (room, seats=10, belongsto=elementary-springfield)
     * Category keys with dashes are PRD 058-migrated to underscore at load
     * time, so the enum values are {@code springfield_powerplant} and
     * {@code elementary_springfield}.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void stringWhereEqMatchesOneRoom()
    {
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { name: { eq: "Room A66" } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "expected only 'Room A66', got " + got);
        assertEquals("Room A66", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void stringWhereContainsMatchesOneRoom()
    {
        // case-insensitive substring per StringWhere.contains semantics.
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { name: { contains: "RWIN" } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "expected only 'erwin' (substring 'RWIN' i-c), got " + got);
        assertEquals("erwin", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void stringWhereStartsWithMatchesOneRoom()
    {
        // case-sensitive prefix per StringWhere.startsWith.
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { name: { startsWith: "Room" } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "expected only 'Room A66' (prefix 'Room'), got " + got);
        assertEquals("Room A66", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void intWhereGteMatchesOneRoom()
    {
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { seats: { gte: 20 } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "expected only 'Room A66' (seats>=20), got " + got);
        assertEquals("Room A66", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void intWhereLteMatchesOneRoom()
    {
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { seats: { lte: 15 } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "expected only 'erwin' (seats<=15), got " + got);
        assertEquals("erwin", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void enumWhereEqMatchesOneRoom()
    {
        // testdefault category leaf "springfield-powerplant" → enum value
        // springfield_powerplant after PRD 058 migration.
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { belongsto: { eq: springfield_powerplant } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "expected only 'Room A66' (springfield-powerplant), got " + got);
        assertEquals("Room A66", got.get(0).get("displayName"));
    }

    // === §5d Phase 5 — remaining operators + isNull + permission-leak =======

    /** Helper — count of allocatables for a where-predicate query (default room scope). */
    private int countWhereRoom(String wherePredicate)
    {
        List<Map<String, Object>> got = tester.document(
                "{ allocatables(filter: { typeKeyEq: \"room\", whereRoom: " + wherePredicate + " }) { displayName } }")
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        return got.size();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void stringWhereOperators_neInEndsWithIsNull()
    {
        // ne
        assertEquals(1, countWhereRoom("{ name: { ne: \"Room A66\" } }"));
        // in
        assertEquals(1, countWhereRoom("{ name: { in: [\"Room A66\", \"does-not-exist\"] } }"));
        // endsWith
        assertEquals(1, countWhereRoom("{ name: { endsWith: \"win\" } }"));
        // isNull:true — both rooms have name set, so 0 match
        assertEquals(0, countWhereRoom("{ name: { isNull: true } }"));
        // isNull:false — explicit non-null, both rooms match
        assertEquals(2, countWhereRoom("{ name: { isNull: false } }"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void intWhereOperators_eqNeInGtLtIsNull()
    {
        assertEquals(1, countWhereRoom("{ seats: { eq: 30 } }"));     // Room A66
        assertEquals(1, countWhereRoom("{ seats: { ne: 30 } }"));     // erwin
        assertEquals(2, countWhereRoom("{ seats: { in: [10, 30] } }"));
        assertEquals(1, countWhereRoom("{ seats: { gt: 20 } }"));     // Room A66
        assertEquals(1, countWhereRoom("{ seats: { lt: 20 } }"));     // erwin
        assertEquals(0, countWhereRoom("{ seats: { isNull: true } }"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void enumWhereOperators_neIn()
    {
        assertEquals(1, countWhereRoom("{ belongsto: { ne: springfield_powerplant } }"));
        assertEquals(2, countWhereRoom("{ belongsto: { in: [springfield_powerplant, elementary_springfield] } }"));
    }

    /**
     * "and not null" — an operator other than isNull implies the attribute
     * must be non-null. Tested by querying for an attribute equality where
     * one of the rooms is missing the value (not applicable in testdefault
     * since both rooms have all attrs set — but we can validate the
     * invariant by asserting that `eq` on a value-only-present-on-one-row
     * narrows correctly without leaking the other row).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void implicitNotNullSemantic()
    {
        // Sanity — seats: { gte: 1 } matches both (both have seats set).
        assertEquals(2, countWhereRoom("{ seats: { gte: 1 } }"));
        // Sanity — seats: { eq: 0 } matches neither (both have positive seats).
        assertEquals(0, countWhereRoom("{ seats: { eq: 0 } }"));
    }

    /**
     * §12 permission leak — non-admin user "monty" cannot read "Room A66"
     * (the room is owner-less, only allocate_conflicts; the user is in
     * powerplant group but doesn't have read on this room). Where predicates
     * MUST filter post-canRead — the predicate's match set is the visible
     * subset. Asserts no hidden allocatable leaks via row count or content.
     */
    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void permissionLeakWherePredicateMatchesHiddenRoom()
    {
        // monty's visible rooms — baseline.
        List<Map<String, Object>> visible = tester.document("""
                { allocatables(filter: { typeKeyEq: "room" }) { id displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // A predicate that would have matched Room A66 if monty could see it
        // (seats=30 is only Room A66) must NOT return Room A66 in monty's results
        // and must be byte-identical to the visible-only subset filtered the same way.
        List<Map<String, Object>> withWhere = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { seats: { eq: 30 } } }) { id displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // Either monty can see Room A66 (in which case both are 1) OR she cannot
        // (in which case withWhere = 0 and visible has just erwin). Critical
        // invariant: withWhere ⊆ visible.
        for (Map<String, Object> row : withWhere)
        {
            assertTrue(visible.stream().anyMatch(v -> row.get("id").equals(v.get("id"))),
                    () -> "where-predicate result " + row + " not in visible set " + visible);
        }
    }

    // === §5d Phase 4 — AND / OR / NOT combinators ============================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void andCombinatorAllClausesMustMatch()
    {
        // Both clauses match Room A66 only.
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { AND: [
                      { name:  { eq: "Room A66" } }
                      { seats: { gte: 20 } }
                    ] } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "got: " + got);
        assertEquals("Room A66", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void andCombinatorOneFailingClauseRejects()
    {
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { AND: [
                      { name:  { eq: "Room A66" } }
                      { seats: { gte: 100 } }
                    ] } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(0, got.size(), () -> "got: " + got);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void orCombinatorAnyClauseMatching()
    {
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { OR: [
                      { name: { eq: "Room A66" } }
                      { name: { eq: "erwin"    } }
                    ] } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, got.size(), () -> "got: " + got);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void notCombinatorNegates()
    {
        // NOT name = "Room A66" → only erwin.
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { NOT: { name: { eq: "Room A66" } } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "got: " + got);
        assertEquals("erwin", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void nestedCombinators()
    {
        // AND[{seats >= 20}, {NOT belongsto = elementary_springfield}]
        // → Room A66 (seats=30, belongsto=springfield_powerplant)
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { AND: [
                      { seats: { gte: 20 } }
                      { NOT: { belongsto: { eq: elementary_springfield } } }
                    ] } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, got.size(), () -> "got: " + got);
        assertEquals("Room A66", got.get(0).get("displayName"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void emptyAndIsTrueEmptyOrIsFalse()
    {
        // AND: [] vacuously true → no filter (both rooms).
        List<Map<String, Object>> emptyAnd = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { AND: [] } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, emptyAnd.size(), () -> "AND:[] should match all rooms; got " + emptyAnd);

        // OR: [] vacuously false → empty result.
        List<Map<String, Object>> emptyOr = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereRoom: { OR: [] } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(0, emptyOr.size(), () -> "OR:[] should match nothing; got " + emptyOr);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void booleanWhereEqIgnoredForNonMatchingType()
    {
        // testdefault has no BOOLEAN attribute on room — wherePerson against
        // a room-only query is a no-op (allocatable belongs to one DT;
        // per-PRD a where<OtherType> contributes no constraint).
        List<Map<String, Object>> got = tester.document("""
                { allocatables(filter: { typeKeyEq: "room",
                    whereLecturer: { surname: { eq: "Burns" } } }) { displayName } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // whereLecturer against rooms = no constraint. 2 rooms.
        assertEquals(2, got.size(), () -> "expected 2 rooms (whereLecturer doesn't constrain rooms), got " + got);
    }

    /**
     * §5d Phase 2 — confirm `AllocatableFilter.whereRoom` is in the schema,
     * typed `roomWhere`. Introspection on `AllocatableFilter`'s inputFields.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatableFilterCarriesWherePerResourcePersonType()
    {
        Map<String, Object> filter = tester.document("""
                {
                  __type(name: "AllocatableFilter") {
                    inputFields { name type { name kind ofType { name } } }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fs = (List<Map<String, Object>>) filter.get("inputFields");
        Map<String, String> byName = new java.util.HashMap<>();
        for (Map<String, Object> f : fs) byName.put((String) f.get("name"), leafTypeName(f.get("type")));
        assertEquals("roomWhere",     byName.get("whereRoom"),     () -> "got: " + byName);
        assertEquals("lecturerWhere", byName.get("whereLecturer"), () -> "got: " + byName);
        assertFalse(byName.containsKey("whereEvent"),              () -> "reservation DT should not appear; got: " + byName);
    }

    /**
     * Phase 1 — multi-select allocatable attribute. testdefault.xml resource2.a1
     * is multi-select=true allocatable → predicate type is `AllocatableListWhere`
     * (added alongside the existing static AllocatableWhere for symmetry with
     * CategoryWhere/CategoryListWhere).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void multiAllocatableAttributeUsesAllocatableListWhere()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "resource2Where") {
                    inputFields { name type { name kind ofType { name kind } } }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("inputFields");
        Map<String, Object> a1 = fields.stream()
                .filter(f -> "a1".equals(f.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("a1 missing on resource2Where"));
        assertEquals("AllocatableListWhere", leafTypeName(a1.get("type")),
                () -> "a1 should be AllocatableListWhere, got " + a1.get("type"));
    }

    // === allocatables / allocatable ==========================================

    @Test
    @WithAnonymousUser
    void anonymousAllocatablesRejected()
    {
        tester.document("{ allocatables { id } }")
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous allocatables query must error");
                    assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                            () -> "expected UNAUTHENTICATED; got " + errs);
                });
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminSeesAllocatables()
    {
        List<Map<String, Object>> all = tester.document("""
                { allocatables { id displayName type classification { typeKey } } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertFalse(all.isEmpty(), "admin should see allocatables");
        // Every allocatable must carry a classification.
        all.forEach(a -> assertNotNull(((Map<?, ?>) a.get("classification")).get("typeKey"),
                () -> "every allocatable must have a classification.typeKey: " + a));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterByTypeKeyNarrowsResults()
    {
        List<Map<String, Object>> rooms = tester.document("""
                {
                  allocatables(filter: { typeKeyEq: "room" }) {
                    displayName
                  }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // testdefault.xml has 2 rooms ("Room A66", "erwin")
        assertEquals(2, rooms.size(), () -> "expected 2 rooms, got " + rooms);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterByTypeKeyInReturnsUnionAcrossTypes()
    {
        // Cross-DT union — testdefault.xml has 2 rooms (Room A66, erwin) and
        // 2 lecturers (Simpson Homer, Burns Monty). The union should return
        // all four.
        List<Map<String, Object>> union = tester.document("""
                {
                  allocatables(filter: { typeKeyIn: ["room", "lecturer"] }) {
                    displayName
                    classification { typeKey }
                  }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(4, union.size(), () -> "expected 4 entries (2 rooms + 2 lecturers), got " + union);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterTypeKeyEqOverridesTypeKeyIn()
    {
        // When both typeKeyEq and typeKeyIn are set, typeKeyEq wins per the
        // schema doc. Confirms intent: stricter narrowing trumps the broader
        // list, mirroring how SQL "= X AND IN (X, Y)" reduces to "= X".
        List<Map<String, Object>> only = tester.document("""
                {
                  allocatables(filter: { typeKeyEq: "room", typeKeyIn: ["room", "lecturer"] }) {
                    displayName
                  }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, only.size(), () -> "expected 2 rooms only, got " + only);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterTypeKeyInWithUnknownKeyIgnoresIt()
    {
        // Unknown keys in the list are silently dropped — matches typeKeyEq's
        // existing behavior of "no match → empty". A typo in one key shouldn't
        // hide the matches for the other keys.
        List<Map<String, Object>> result = tester.document("""
                {
                  allocatables(filter: { typeKeyIn: ["room", "does-not-exist"] }) {
                    displayName
                  }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, result.size(), () -> "expected 2 rooms (unknown key ignored), got " + result);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterByNameContainsIsCaseInsensitive()
    {
        // testdefault.xml has 2 allocatables matching "ROOM A": "Room A66" (a
        // dynatt:room) and "Room A66.1" (a dynatt:resource1). Both should
        // appear since the filter is name-substring, not type-restricted.
        List<Map<String, Object>> found = tester.document("""
                {
                  allocatables(filter: { nameContains: "ROOM A" }) {
                    displayName
                  }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, found.size(), () -> "expected 2 matches for 'ROOM A', got " + found);
        List<String> names = found.stream().map(m -> (String) m.get("displayName")).toList();
        assertTrue(names.contains("Room A66"),   () -> "missing 'Room A66' in " + names);
        assertTrue(names.contains("Room A66.1"), () -> "missing 'Room A66.1' in " + names);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatablesFilterLimitCapsResultSize()
    {
        // testdefault has ~6 allocatables visible to admin; limit:2 must cap at 2.
        List<Map<String, Object>> capped = tester.document("""
                { allocatables(filter: { limit: 2 }) { id } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, capped.size(), () -> "limit:2 should cap at 2 entries, got " + capped.size());
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatableByUnknownIdReturnsNull()
    {
        tester.document("{ allocatable(id: \"does-not-exist\") { id } }")
                .execute()
                .path("allocatable")
                .valueIsNull();
    }

    // === Classification interface =============================================
    //
    // β refactor 2026-05-28: dropped `classificationInterfaceExposesAttributesGenerically`.
    // The `attributes: [AttributeValue!]!` field is removed from the
    // Classification interface; the SPA path is dynamic typed-narrow queries
    // constructed from introspection. See classificationTypedNarrowingReturnsRawValue
    // below for the path locked-in to test.

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void classificationTypedNarrowingReturnsRawValue()
    {
        // This is the codegen-consumer path: query the GENERATED type's
        // typed field. The SPA must NOT use this; PRD 035 §540 lock-in.
        Map<String, Object> first = tester.document("""
                {
                  allocatables(filter: { typeKeyEq: "room" }) {
                    displayName
                    classification {
                      ... on roomClassification {
                        name
                        seats
                      }
                    }
                  }
                }
                """)
                .execute()
                .path("allocatables[0]")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        Map<String, Object> classification = (Map<String, Object>) first.get("classification");
        assertNotNull(classification.get("name"));
        Object seats = classification.get("seats");
        assertNotNull(seats, "typed seats field must be populated");
        assertTrue(seats instanceof Number, () -> "typed seats should be a Number, got " + seats);
    }

    // === Introspection of generated types =====================================

    /**
     * PRD 035 §5b: every VALUE_LIST category root referenced by an attribute
     * becomes a generated enum. testdefault.xml has the {@code department}
     * root (depth-1, 4 leaves) referenced by {@code dynatt:room.belongsto},
     * so {@code enum Department} should exist with the 4 leaf keys as values.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void valueListRootGeneratesEnum()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "department") {
                    name
                    kind
                    enumValues { name description }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(result, "Department enum must be generated for VALUE_LIST root");
        assertEquals("ENUM", result.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) result.get("enumValues");
        List<String> names = values.stream().map(v -> (String) v.get("name")).toList();
        // PRD 058 + enumValueFor verbatim-emission: leaf keys are returned as-is
        // post-migration. testdefault.xml ships hyphen-bearing keys; the startup
        // migration renames them to underscore form (channel-6 → channel_6 etc.)
        // and enumValueFor emits the renamed key directly. SCREAMING_SNAKE_CASE
        // is GraphQL convention for enum values; PRD 035 §5b's PascalCase rule
        // applied to enum values stripped underscores and silently dropped
        // collision-bearing leaves (dhbw DIN_5_2_3_11 → DIN52311 collision).
        assertTrue(names.contains("channel_6"),               () -> "missing channel_6 in " + names);
        assertTrue(names.contains("elementary_springfield"),  () -> "missing elementary_springfield in " + names);
        assertTrue(names.contains("springfield_powerplant"),  () -> "missing springfield_powerplant in " + names);
        assertTrue(names.contains("testdepartment"),          () -> "missing testdepartment in " + names);
        assertEquals(4, names.size(), () -> "expected exactly 4 enum values, got " + names);
    }

    /**
     * §5b: the generated classification type's CATEGORY-attribute field that
     * targets a VALUE_LIST root is typed as the enum, not as Category.
     * testdefault.xml: {@code dynatt:room.belongsto} targets {@code department}
     * (VALUE_LIST), so {@code roomClassification.belongsto} should have type
     * {@code Department}, not {@code Category}.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void valueListAttributeUsesEnumTypeOnGeneratedClassification()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> fields = (List<Map<String, Object>>) (List) tester.document("""
                {
                  __type(name: "roomClassification") {
                    fields { name type { name kind } }
                  }
                }
                """)
                .execute()
                .path("__type.fields")
                .entityList(Map.class).get();
        Map<String, Object> belongsto = fields.stream()
                .filter(f -> "belongsto".equals(f.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("belongsto field missing on roomClassification"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldType = (Map<String, Object>) belongsto.get("type");
        assertEquals("department", fieldType.get("name"));
        assertEquals("ENUM", fieldType.get("kind"));
    }

    /**
     * §5b: reading a CATEGORY value backed by a VALUE_LIST enum returns
     * the sanitized enum-value-name string on the wire (graphql-java
     * serializes enums as bare strings). The test queries a room whose
     * belongsto is set and asserts the wire value matches.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void valueListCategoryReadReturnsEnumValueString()
    {
        // From testdefault.xml: "Room A66" has belongsto=springfield-powerplant
        Map<String, Object> roomA66 = tester.document("""
                {
                  allocatables(filter: { typeKeyEq: "room", nameContains: "Room A66" }) {
                    displayName
                    classification {
                      ... on roomClassification {
                        seats
                        belongsto
                      }
                    }
                  }
                }
                """)
                .execute()
                .path("allocatables[0]")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        Map<String, Object> classification = (Map<String, Object>) roomA66.get("classification");
        // belongsto returns the renamed leaf key directly (PRD 058 verbatim enum-value emission).
        assertEquals("springfield_powerplant", classification.get("belongsto"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void introspectionShowsGeneratedClassificationType()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "roomClassification") {
                    name
                    interfaces { name }
                    fields { name }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("roomClassification", result.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> interfaces = (List<Map<String, Object>>) result.get("interfaces");
        List<String> ifaceNames = interfaces.stream().map(i -> (String) i.get("name")).toList();
        assertTrue(ifaceNames.contains("Classification"),
                () -> "roomClassification should implement Classification, got " + ifaceNames);
        assertTrue(ifaceNames.contains("AllocatableClassification"),
                () -> "roomClassification should implement AllocatableClassification (resource kind), got " + ifaceNames);
        assertFalse(ifaceNames.contains("ReservationClassification"),
                () -> "roomClassification must NOT implement ReservationClassification, got " + ifaceNames);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        // Must carry interface fields + the typed attribute fields. Note:
        // β refactor 2026-05-28 — `attributes: [AttributeValue!]!` dropped.
        // 2026-05-29 — typeId dropped (PRD 035 §11); `typeKey` + `type` are
        // the only surviving interface fields (use `type { id }` for the UUID).
        List<String> fieldNames = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(fieldNames.contains("typeKey"),   () -> "missing interface field typeKey in " + fieldNames);
        assertTrue(fieldNames.contains("type"),      () -> "missing interface field type in " + fieldNames);
        assertFalse(fieldNames.contains("typeId"),
                () -> "typeId was dropped 2026-05-29 — use type { id } for UUID; got " + fieldNames);
        assertFalse(fieldNames.contains("attributes"),
                () -> "β refactor: `attributes` should NOT be in generated type's fields; got " + fieldNames);
        assertTrue(fieldNames.contains("name"),      () -> "missing typed field name in " + fieldNames);
        assertTrue(fieldNames.contains("seats"),     () -> "missing typed field seats in " + fieldNames);
        assertTrue(fieldNames.contains("belongsto"), () -> "missing typed field belongsto in " + fieldNames);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void eventClassificationIsSeparateInterface()
    {
        // The reservation type 'event' generates eventClassification (PascalCase
        // of bare key) implementing Classification & ReservationClassification.
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "eventClassification") {
                    name
                    interfaces { name }
                  }
                }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> interfaces = (List<Map<String, Object>>) result.get("interfaces");
        List<String> ifaceNames = interfaces.stream().map(i -> (String) i.get("name")).toList();
        assertTrue(ifaceNames.contains("ReservationClassification"),
                () -> "eventClassification should implement ReservationClassification, got " + ifaceNames);
        assertFalse(ifaceNames.contains("AllocatableClassification"),
                () -> "eventClassification must NOT implement AllocatableClassification, got " + ifaceNames);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void eventClassificationFragmentOnAllocatableFieldFailsValidation()
    {
        // SPA mistake protection: inline fragment on an event-only type
        // against the resource-typed Allocatable.classification field must
        // fail GraphQL validation up-front, not return null silently.
        tester.document("""
                {
                  allocatables(filter: { typeKeyEq: "room" }) {
                    classification {
                      ... on eventClassification {
                        typeKey
                      }
                    }
                  }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(),
                            "expected validation error for eventClassification fragment on AllocatableClassification field");
                    String joined = errs.toString();
                    assertTrue(joined.contains("eventClassification")
                                    || joined.toLowerCase().contains("fragment")
                                    || joined.toLowerCase().contains("possible"),
                            () -> "expected fragment-type error, got " + joined);
                });
    }

    // === checkGraphQlCompliantName unit (cheap pure-function check) ===========
    //
    // Per PRD 035 §5b revision 2026-05-28: the generator NEVER transforms a
    // rapla key. checkGraphQlCompliantName verifies spec compliance and
    // returns the key verbatim (with reserved-keyword trailing-underscore
    // disambiguation only). Admins choose case style at the source — if they
    // key a DynamicType `Room`, the generated GraphQL type is
    // `roomClassification`; if they key `room`, it's `roomClassification`.
    // Non-spec input throws IllegalStateException — migration should have
    // caught it.

    @Test
    void keyVerbatimEmissionRespectsAdminCase()
    {
        assertEquals("room",         ClassificationSdlGenerator.checkGraphQlCompliantName("room"));
        assertEquals("Room",         ClassificationSdlGenerator.checkGraphQlCompliantName("Room"));
        assertEquals("exam_prep",    ClassificationSdlGenerator.checkGraphQlCompliantName("exam_prep"));
        assertEquals("DIN_5_2_3_11", ClassificationSdlGenerator.checkGraphQlCompliantName("DIN_5_2_3_11"));
        assertEquals("pruefer_extern", ClassificationSdlGenerator.checkGraphQlCompliantName("pruefer_extern"));
    }

    @Test
    void nonSpecInputThrows()
    {
        // Defensive invariant — migration should have caught this.
        assertThrows(IllegalStateException.class,
                () -> ClassificationSdlGenerator.checkGraphQlCompliantName("dynatt:room"));
        assertThrows(IllegalStateException.class,
                () -> ClassificationSdlGenerator.checkGraphQlCompliantName("exam-prep"));
        assertThrows(IllegalStateException.class,
                () -> ClassificationSdlGenerator.checkGraphQlCompliantName("Prüfer"));
    }

    @Test
    void fieldNameSanitizationPreservesValidKeysAndAvoidsReserved()
    {
        assertEquals("seats", ClassificationSdlGenerator.checkGraphQlCompliantName("seats"));
        assertEquals("course_number", ClassificationSdlGenerator.checkGraphQlCompliantName("course_number"));
        assertEquals("type_",  ClassificationSdlGenerator.checkGraphQlCompliantName("type"));
    }

    // ============================================================ PRD 028 Phase 1 — searchText + matchKind

    /** Schema introspection: searchText + matchKind on AllocatableFilter. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allocatableFilterHasSearchTextAndMatchKind()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "AllocatableFilter") { inputFields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) result.get("inputFields");
        List<String> names = inputs.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("searchText"), () -> "missing searchText in AllocatableFilter: " + names);
        assertTrue(names.contains("matchKind"),  () -> "missing matchKind in AllocatableFilter: " + names);
    }

    /** MatchKind enum exists with PREFIX / SUBSTRING / FUZZY values. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void matchKindEnumExists()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "MatchKind") { kind enumValues { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("ENUM", result.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) result.get("enumValues");
        List<String> names = values.stream().map(v -> (String) v.get("name")).toList();
        assertTrue(names.containsAll(List.of("PREFIX", "SUBSTRING", "FUZZY")),
                () -> "MatchKind missing one of PREFIX/SUBSTRING/FUZZY: " + names);
    }

    /** PREFIX matches a room by name prefix. testdefault.xml has "Room A66". */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void searchTextPrefixMatchesRoom()
    {
        List<Map<String, Object>> rooms = tester.document("""
                {
                  allocatables(filter: {
                    typeKeyEq: "room",
                    searchText: "Room",
                    matchKind: PREFIX
                  }) { id displayName }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // Room A66 matches; erwin does NOT prefix-match "Room"
        assertEquals(1, rooms.size(), () -> "expected only 'Room A66', got " + rooms);
        assertEquals("Room A66", rooms.get(0).get("displayName"));
    }

    /** SUBSTRING matches both rooms when search text is part of either name. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void searchTextSubstringMatchesBothByLetter()
    {
        // "r" is in both "Room A66" and "erwin" → both come back
        List<Map<String, Object>> rooms = tester.document("""
                {
                  allocatables(filter: {
                    typeKeyEq: "room",
                    searchText: "r",
                    matchKind: SUBSTRING
                  }) { id displayName }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(2, rooms.size(), () -> "expected both rooms, got " + rooms);
    }

    /** Ranking — server pre-sorts by match strength + position. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void searchTextResultsAreServerRanked()
    {
        // PREFIX-only query; only Room A66 matches (sorts at the top of an empty rest)
        List<Map<String, Object>> rooms = tester.document("""
                {
                  allocatables(filter: {
                    typeKeyEq: "room",
                    searchText: "Room",
                    matchKind: PREFIX
                  }) { displayName }
                }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("Room A66", rooms.get(0).get("displayName"),
                () -> "PREFIX hit should rank first; got " + rooms);
    }
}
