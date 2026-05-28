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

    // === allocatables / allocatable ==========================================

    @Test
    @WithAnonymousUser
    void anonymousSeesEmptyAllocatables()
    {
        List<?> result = tester.document("{ allocatables { id } }")
                .execute()
                .path("allocatables")
                .entityList(Object.class)
                .get();
        assertTrue(result.isEmpty(), () -> "anonymous should see no allocatables, got " + result);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminSeesAllocatables()
    {
        List<Map<String, Object>> all = tester.document("""
                { allocatables { id displayName type classification { typeId } } }
                """)
                .execute()
                .path("allocatables")
                .entityList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertFalse(all.isEmpty(), "admin should see allocatables");
        // Every allocatable must carry a classification.
        all.forEach(a -> assertNotNull(((Map<?, ?>) a.get("classification")).get("typeId"),
                () -> "every allocatable must have a classification.typeId: " + a));
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
        // β refactor 2026-05-28 — the `attributes: [AttributeValue!]!` interface
        // field was dropped; only `typeId` + `type` survive as interface fields.
        List<String> fieldNames = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(fieldNames.contains("typeId"),    () -> "missing interface field typeId in " + fieldNames);
        assertTrue(fieldNames.contains("type"),      () -> "missing interface field type in " + fieldNames);
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
                        typeId
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
}
