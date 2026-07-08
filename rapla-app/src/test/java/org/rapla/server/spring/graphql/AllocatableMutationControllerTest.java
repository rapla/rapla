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
 * PRD 063 — tier-3 tests for the Allocatable mutation surface.
 * Same harness as the other controller tests.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class AllocatableMutationControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AllocatableMutationControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    // ============================================================ schema smoke

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaIncludesAllocatableMutations()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Mutation") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("createAllocatable"),  () -> "missing createAllocatable in " + names);
        assertTrue(names.contains("updateAllocatable"),  () -> "missing updateAllocatable in " + names);
        assertTrue(names.contains("deleteAllocatables"), () -> "missing deleteAllocatables in " + names);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void changeOpHasAllocatableVariants()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "ChangeOp") { inputFields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) result.get("inputFields");
        List<String> names = inputs.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("createAllocatable"), () -> "missing createAllocatable in ChangeOp: " + names);
        assertTrue(names.contains("updateAllocatable"), () -> "missing updateAllocatable in ChangeOp: " + names);
        assertTrue(names.contains("deleteAllocatable"), () -> "missing deleteAllocatable in ChangeOp: " + names);
    }

    // ============================================================ §12 gates

    @Test
    @WithAnonymousUser
    void anonymousCreateRejected()
    {
        tester.document("""
                mutation {
                  createAllocatable(input: {
                    typeKey: "room",
                    classification: { room: {} }
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("PERMISSION_DENIED") || joined.toLowerCase().contains("authentic"),
                            () -> "expected auth rejection; got " + joined);
                });
    }

    // ============================================================ happy path

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminCreateRoomThenReadBack()
    {
        String createdId = tester.document("""
                mutation {
                  createAllocatable(input: {
                    id: "f7777777-7777-4777-8777-777777777777",
                    typeKey: "room",
                    classification: { room: {} }
                  }) { id }
                }
                """)
                .execute()
                .path("createAllocatable.id")
                .entity(String.class)
                .get();
        assertNotNull(createdId, "createAllocatable must return the stored id");
        assertFalse(createdId.isBlank());

        Map<String, Object> readBack = tester.document("""
                query ($id: ID!) {
                  allocatable(id: $id) {
                    id
                    type
                    classification { typeKey type { key } }
                    owner { username }
                  }
                }
                """)
                .variable("id", createdId)
                .execute()
                .path("allocatable")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(readBack);
        assertEquals(createdId, readBack.get("id"));
        assertEquals("RESOURCE", readBack.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> classification = (Map<String, Object>) readBack.get("classification");
        assertEquals("room", classification.get("typeKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> type = (Map<String, Object>) classification.get("type");
        assertEquals("room", type.get("key"));
        @SuppressWarnings("unchecked")
        Map<String, Object> owner = (Map<String, Object>) readBack.get("owner");
        assertEquals("homer", owner.get("username"), "owner defaults to caller");
    }

    // ============================================================ PRD 056 §9 — mandatory client id

    /**
     * PRD 056 §9 (decided 2026-07-06): createAllocatable REQUIRES a
     * client-supplied id — no server-side fallback. See
     * ReservationMutationControllerTest#createReservationWithoutIdRejected
     * for the rationale.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createWithoutIdRejected()
    {
        tester.document("""
                mutation {
                  createAllocatable(input: {
                    typeKey: "room",
                    classification: { room: {} }
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "create without id must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("REQUIRED") && joined.contains("id"),
                            () -> "expected REQUIRED-on-id error; got " + joined);
                });
    }

    /**
     * PRD 056 §9 check #1: createAllocatable with an id that already resolves
     * → {@code ID_COLLISION} (retry contract — no silent overwrite, no
     * content comparison).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createWithExistingIdReturnsIdCollision()
    {
        String document = """
                mutation {
                  createAllocatable(input: {
                    id: "f8888888-8888-4888-8888-888888888888",
                    typeKey: "room",
                    classification: { room: {} }
                  }) { id }
                }
                """;
        String createdId = tester.document(document)
                .execute()
                .path("createAllocatable.id")
                .entity(String.class)
                .get();
        assertEquals("f8888888-8888-4888-8888-888888888888", createdId);

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

    // ============================================================ validation

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unknownTypeKeyRejected()
    {
        tester.document("""
                mutation {
                  createAllocatable(input: {
                    typeKey: "nonexistent",
                    classification: { nonexistent: {} }
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "unknown typeKey must be rejected");
                    String joined = errs.toString();
                    // The @oneOf input shape would reject "nonexistent" variant first
                    // at parse time; if it gets through, the server rejects with
                    // REFERENCE_NOT_FOUND or MISMATCHED_TYPE.
                    assertTrue(joined.contains("REFERENCE_NOT_FOUND")
                                    || joined.contains("INVALID")
                                    || joined.toLowerCase().contains("not found")
                                    || joined.contains("oneOf")
                                    || joined.contains("WrongType")
                                    || joined.contains("not in 'AllocatableClassificationInput'"),
                            () -> "expected type-rejection error; got " + joined);
                });
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void mismatchedClassificationVariantRejected()
    {
        // typeKey says "room" but classification @oneOf variant is "lecturer"
        // → either @oneOf engine validation OR our resolver MISMATCHED_TYPE
        tester.document("""
                mutation {
                  createAllocatable(input: {
                    typeKey: "room",
                    classification: { lecturer: {} }
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "mismatched variant must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("MISMATCHED_TYPE")
                                    || joined.contains("does not match")
                                    || joined.contains("oneOf"),
                            () -> "expected mismatch error; got " + joined);
                });
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateUnknownIdReturnsReferenceNotFound()
    {
        tester.document("""
                mutation {
                  updateAllocatable(
                    id: "00000000-0000-0000-0000-deadbeef0000",
                    input: { typeKey: "room", classification: { room: {} } }
                  ) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "unknown id must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("REFERENCE_NOT_FOUND")
                                    || joined.toLowerCase().contains("not found"),
                            () -> "expected REFERENCE_NOT_FOUND; got " + joined);
                });
    }

    // ============================================================ delete

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void deleteUnknownIdReportsReferenceNotFound()
    {
        Map<String, Object> result = tester.document("""
                mutation {
                  deleteAllocatables(ids: ["00000000-0000-0000-0000-deadbeef0000"]) {
                    overallStatus
                    results { index errors { code path message } }
                  }
                }
                """)
                .execute()
                .path("deleteAllocatables")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("REJECTED", result.get("overallStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertEquals(1, results.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errs = (List<Map<String, Object>>) results.get(0).get("errors");
        assertFalse(errs.isEmpty(), "expected errors[]");
        assertEquals("REFERENCE_NOT_FOUND", errs.get(0).get("code"));
    }

    // ============================================================ type change (PRD 096 Phase 4, 2026-07-07)

    /**
     * Mirror of the PRD 056 OQ1.c revision for allocatables: updateAllocatable
     * ACCEPTS a typeKey differing from stored when the classification @oneOf
     * variant matches the NEW typeKey; the caller passes the createAllocatable
     * create-gate on the target type. Fixture has resource types room /
     * resource1 / resource2.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateAllocatableChangesType()
    {
        String created = tester.document("""
                mutation {
                  createAllocatable(input: {
                    id: "b1111111-1111-4111-8111-111111111111",
                    typeKey: "room",
                    classification: { room: { name: "Umwidmungsraum" } }
                  }) { id }
                }
                """)
                .execute()
                .path("createAllocatable.id")
                .entity(String.class)
                .get();

        Map<String, Object> updated = tester.document("""
                mutation ($id: ID!) {
                  updateAllocatable(id: $id, input: {
                    typeKey: "resource1",
                    classification: { resource1: { name: "Umgewidmet" } }
                  }) {
                    displayName
                    classification { typeKey }
                  }
                }
                """)
                .variable("id", created)
                .execute()
                .path("updateAllocatable")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        Map<String, Object> classification = (Map<String, Object>) updated.get("classification");
        assertEquals("resource1", classification.get("typeKey"), "typeKey must switch");
        assertEquals("Umgewidmet", updated.get("displayName"), "new-type values must persist");

        // stored, not just echoed
        String storedTypeKey = tester.document("""
                query ($id: ID!) { allocatable(id: $id) { classification { typeKey } } }
                """)
                .variable("id", created)
                .execute()
                .path("allocatable.classification.typeKey")
                .entity(String.class)
                .get();
        assertEquals("resource1", storedTypeKey);
    }

    /**
     * PRD 099 ride-along — VALUE_LIST enum input on allocatables. `room` has
     * the CATEGORY attribute `belongsto` (root=department, VALUE_LIST → the
     * @oneOf variant field is the generated enum whose values are leaf keys).
     * The allocatable coerceValue was a raw pass-through ("extend when SPA
     * editor lands" — it has), so enum keys were never resolved to Categories.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void valueListEnumInputResolvesToCategoryOnUpdate()
    {
        Map<String, Object> typeInfo = tester.document("""
                { __type(name: "roomClassification") { fields { name type { name } } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        String enumName = ((java.util.List<Map<String, Object>>) typeInfo.get("fields")).stream()
                .filter(f -> "belongsto".equals(f.get("name")))
                .map(f -> (String) ((Map<String, Object>) f.get("type")).get("name"))
                .findFirst().orElseThrow();
        java.util.List<String> values = tester.document("""
                query ($n: String!) { __type(name: $n) { enumValues { name } } }
                """)
                .variable("n", enumName)
                .execute()
                .path("__type.enumValues[*].name")
                .entityList(String.class)
                .get();
        assertTrue(!values.isEmpty(), () -> "enum " + enumName + " must have values");
        String pick = values.get(0);

        String created = tester.document("""
                mutation {
                  createAllocatable(input: {
                    id: "b3030303-0303-4303-8303-030303030303",
                    typeKey: "room",
                    classification: { room: { name: "Enumraum" } }
                  }) { id }
                }
                """)
                .execute()
                .path("createAllocatable.id")
                .entity(String.class)
                .get();

        Map<String, Object> updated = tester.document("""
                mutation ($id: ID!) {
                  updateAllocatable(id: $id, input: {
                    typeKey: "room",
                    classification: { room: { name: "Enumraum", belongsto: %s } }
                  }) {
                    classification { ... on roomClassification { belongsto } }
                  }
                }
                """.formatted(pick))
                .variable("id", created)
                .execute()
                .path("updateAllocatable.classification")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(pick, updated.get("belongsto"),
                "enum key must resolve to the category on allocatable update");
    }

    /** Cross-validation stays: new typeKey with the OLD @oneOf variant is rejected. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateAllocatableTypeChangeWithMismatchedVariantRejected()
    {
        String created = tester.document("""
                mutation {
                  createAllocatable(input: {
                    id: "b2222222-2222-4222-8222-222222222222",
                    typeKey: "room",
                    classification: { room: { name: "Mismatch-Raum" } }
                  }) { id }
                }
                """)
                .execute()
                .path("createAllocatable.id")
                .entity(String.class)
                .get();

        tester.document("""
                mutation ($id: ID!) {
                  updateAllocatable(id: $id, input: {
                    typeKey: "resource1",
                    classification: { room: { name: "x" } }
                  }) { id }
                }
                """)
                .variable("id", created)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "mismatched @oneOf variant must be rejected");
                    assertTrue(errs.toString().contains("MISMATCHED_TYPE"),
                            () -> "expected MISMATCHED_TYPE, got " + errs);
                });
    }
}
