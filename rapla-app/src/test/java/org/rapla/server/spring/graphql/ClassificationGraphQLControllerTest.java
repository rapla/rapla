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
 * {@code RoomClassification} (sanitized PascalCase per
 * {@link ClassificationSdlGenerator#sanitizeTypeName(String)}).
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
    void typeByKeyReturnsDescriptorWithAttributes()
    {
        Map<String, Object> room = tester.document("""
                {
                  type(key: "room") {
                    key
                    classificationType
                    attributes { key valueType multiplicity required }
                  }
                }
                """)
                .execute()
                .path("type")
                .entity(Map.class)
                .get();
        assertEquals("room", room.get("key"));
        assertEquals("RESOURCE", room.get("classificationType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attrs = (List<Map<String, Object>>) room.get("attributes");
        assertEquals(3, attrs.size(), () -> "expected 3 attrs (name, seats, belongsto), got " + attrs);
        assertEquals("name",      attrs.get(0).get("key"));
        assertEquals("STRING",    attrs.get(0).get("valueType"));
        assertEquals("seats",     attrs.get(1).get("key"));
        assertEquals("INT",       attrs.get(1).get("valueType"));
        assertEquals("belongsto", attrs.get(2).get("key"));
        assertEquals("CATEGORY",  attrs.get(2).get("valueType"));
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

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void classificationInterfaceExposesAttributesGenerically()
    {
        // Pick any allocatable; query attributes via the interface — this is
        // the SPA path that must not name typed-classification fields.
        Map<String, Object> first = tester.document("""
                {
                  allocatables(filter: { typeKeyEq: "room" }) {
                    classification {
                      typeId
                      type { key }
                      attributes { key stringValue intValue }
                    }
                  }
                }
                """)
                .execute()
                .path("allocatables[0].classification")
                .entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        Map<String, Object> type = (Map<String, Object>) first.get("type");
        assertEquals("room", type.get("key"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attrs = (List<Map<String, Object>>) first.get("attributes");
        // Find seats attr — the only INT attribute in dynatt:room.
        Map<String, Object> seats = attrs.stream()
                .filter(a -> "seats".equals(a.get("key")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seats attribute missing in " + attrs));
        Object intValue = seats.get("intValue");
        assertNotNull(intValue, "seats.intValue should be populated");
        assertTrue(intValue instanceof Number, () -> "seats.intValue should be a number, got " + intValue);
    }

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
                      ... on RoomClassification {
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

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void introspectionShowsGeneratedClassificationType()
    {
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "RoomClassification") {
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
        assertEquals("RoomClassification", result.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> interfaces = (List<Map<String, Object>>) result.get("interfaces");
        List<String> ifaceNames = interfaces.stream().map(i -> (String) i.get("name")).toList();
        assertTrue(ifaceNames.contains("Classification"),
                () -> "RoomClassification should implement Classification, got " + ifaceNames);
        assertTrue(ifaceNames.contains("AllocatableClassification"),
                () -> "RoomClassification should implement AllocatableClassification (resource kind), got " + ifaceNames);
        assertFalse(ifaceNames.contains("ReservationClassification"),
                () -> "RoomClassification must NOT implement ReservationClassification, got " + ifaceNames);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        // Must carry interface fields + the typed attribute fields
        List<String> fieldNames = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(fieldNames.contains("typeId"),     () -> "missing interface field typeId in " + fieldNames);
        assertTrue(fieldNames.contains("type"),       () -> "missing interface field type in " + fieldNames);
        assertTrue(fieldNames.contains("attributes"), () -> "missing interface field attributes in " + fieldNames);
        assertTrue(fieldNames.contains("name"),       () -> "missing typed field name in " + fieldNames);
        assertTrue(fieldNames.contains("seats"),      () -> "missing typed field seats in " + fieldNames);
        assertTrue(fieldNames.contains("belongsto"),  () -> "missing typed field belongsto in " + fieldNames);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void eventClassificationIsSeparateInterface()
    {
        // The reservation type 'event' generates EventClassification (PascalCase
        // of bare key) implementing Classification & ReservationClassification.
        Map<String, Object> result = tester.document("""
                {
                  __type(name: "EventClassification") {
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
                () -> "EventClassification should implement ReservationClassification, got " + ifaceNames);
        assertFalse(ifaceNames.contains("AllocatableClassification"),
                () -> "EventClassification must NOT implement AllocatableClassification, got " + ifaceNames);
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
                      ... on EventClassification {
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
                            "expected validation error for EventClassification fragment on AllocatableClassification field");
                    String joined = errs.toString();
                    assertTrue(joined.contains("EventClassification")
                                    || joined.toLowerCase().contains("fragment")
                                    || joined.toLowerCase().contains("possible"),
                            () -> "expected fragment-type error, got " + joined);
                });
    }

    // === sanitization unit (cheap pure-function check) ========================

    @Test
    void typeNameSanitizationStripsNonAlnumAndPascalCases()
    {
        // Rapla strips the dynatt: prefix at XML parse time, so the actual
        // type keys are just 'room' / 'lecturer' / 'event'. The sanitizer
        // still handles non-alnum chars correctly for hypothetical keys.
        assertEquals("Room",        ClassificationSdlGenerator.sanitizeTypeName("room"));
        assertEquals("DynattRoom",  ClassificationSdlGenerator.sanitizeTypeName("dynatt:room"));
        assertEquals("ExamPrep",    ClassificationSdlGenerator.sanitizeTypeName("exam-prep"));
        assertEquals("CourseGroup", ClassificationSdlGenerator.sanitizeTypeName("course_group"));
        assertEquals("Foo123",      ClassificationSdlGenerator.sanitizeTypeName("foo.123"));
    }

    @Test
    void fieldNameSanitizationPreservesValidKeysAndAvoidsReserved()
    {
        assertEquals("seats", ClassificationSdlGenerator.sanitizeFieldName("seats"));
        assertEquals("course_number", ClassificationSdlGenerator.sanitizeFieldName("course_number"));
        assertEquals("type_",  ClassificationSdlGenerator.sanitizeFieldName("type"));
        // colon → field-name fallback to camelCase
        assertEquals("dynattFoo", ClassificationSdlGenerator.sanitizeFieldName("dynatt:foo"));
    }
}
