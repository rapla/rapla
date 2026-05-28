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
 * PRD 057 — tier-3 tests for the DynamicType (schema editor) mutations.
 * Same harness as the other controller tests: testdefault.xml via @TempDir,
 * MockMvc → HttpGraphQlTester, filter chain bypassed so @WithMockUser
 * reaches the resolver.
 *
 * <p>v1 scope covered: schema smoke, §12 admin-only gate (anonymous +
 * non-admin), happy-path create, key collision, classification-type
 * change rejected, delete unknown id.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class DynamicTypeMutationControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = DynamicTypeMutationControllerTest.class.getResourceAsStream("/testdefault.xml"))
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
    void schemaIncludesDynamicTypeMutations()
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
        assertTrue(names.contains("saveDynamicType"),    () -> "missing saveDynamicType in " + names);
        assertTrue(names.contains("deleteDynamicTypes"), () -> "missing deleteDynamicTypes in " + names);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void dynamicTypeInputShapeIsIntrospectable()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "DynamicTypeInput") { kind inputFields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("INPUT_OBJECT", result.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputFields = (List<Map<String, Object>>) result.get("inputFields");
        List<String> names = inputFields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.containsAll(List.of("id", "key", "name", "classificationType", "attributes")),
                () -> "expected DynamicTypeInput core fields; got " + names);
    }

    // ============================================================ §12 — admin gate

    @Test
    @WithAnonymousUser
    void anonymousSaveDynamicTypeRejected()
    {
        tester.document("""
                mutation {
                  saveDynamicType(input: {
                    key: "testtype",
                    name: { default: "Test Type" },
                    classificationType: RESERVATION,
                    attributes: [
                      { key: "name",
                        name: { default: "Name" },
                        valueType: STRING,
                        multiplicity: SINGLE,
                        required: true }
                    ]
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("PERMISSION_DENIED") || joined.toLowerCase().contains("authentic"),
                            () -> "expected PERMISSION_DENIED, got " + joined);
                });
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void nonAdminSaveDynamicTypeRejected()
    {
        tester.document("""
                mutation {
                  saveDynamicType(input: {
                    key: "testtype",
                    name: { default: "Test Type" },
                    classificationType: RESERVATION,
                    attributes: [
                      { key: "name",
                        name: { default: "Name" },
                        valueType: STRING,
                        multiplicity: SINGLE,
                        required: true }
                    ]
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "non-admin must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("PERMISSION_DENIED") || joined.toLowerCase().contains("admin"),
                            () -> "expected admin-only rejection, got " + joined);
                });
    }

    // ============================================================ happy path create

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminCreatesDynamicType()
    {
        // Create a brand-new reservation type with one STRING attribute
        Map<String, Object> created = tester.document("""
                mutation {
                  saveDynamicType(input: {
                    key: "workshop",
                    name: { default: "Workshop" },
                    classificationType: RESERVATION,
                    attributes: [
                      { key: "name",
                        name: { default: "Name" },
                        valueType: STRING,
                        multiplicity: SINGLE,
                        required: true }
                    ]
                  }) {
                    id
                    key
                    classificationType
                  }
                }
                """)
                .execute()
                .path("saveDynamicType")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(created.get("id"), "created.id must populate");
        assertEquals("workshop", created.get("key"));
        assertEquals("RESERVATION", created.get("classificationType"));

        // Verify by querying types
        List<String> keys = tester.document("{ types { key } }")
                .execute()
                .path("types[*].key")
                .entityList(String.class)
                .get();
        assertTrue(keys.contains("workshop"),
                () -> "newly-created type 'workshop' must appear in types list: " + keys);
    }

    // ============================================================ validation

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void duplicateKeyRejectedOnCreate()
    {
        // testdefault.xml already has type 'event'; creating another fails
        tester.document("""
                mutation {
                  saveDynamicType(input: {
                    key: "event",
                    name: { default: "Duplicate Event" },
                    classificationType: RESERVATION,
                    attributes: [
                      { key: "name",
                        name: { default: "Name" },
                        valueType: STRING,
                        multiplicity: SINGLE,
                        required: true }
                    ]
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "duplicate key must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("KEY_COLLISION") || joined.toLowerCase().contains("exists"),
                            () -> "expected KEY_COLLISION, got " + joined);
                });
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void belongsToOnStringAttributeRejected()
    {
        tester.document("""
                mutation {
                  saveDynamicType(input: {
                    key: "bad_multi",
                    name: { default: "Bad Multi" },
                    classificationType: RESERVATION,
                    attributes: [
                      { key: "name",
                        name: { default: "Name" },
                        valueType: STRING,
                        multiplicity: BELONGS_TO,
                        required: true }
                    ]
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "BELONGS_TO on STRING must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("INVALID_VALUE") || joined.contains("BELONGS_TO"),
                            () -> "expected multiplicity validation error, got " + joined);
                });
    }

    // ============================================================ delete

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void deleteUnknownIdReportsReferenceNotFound()
    {
        Map<String, Object> result = tester.document("""
                mutation {
                  deleteDynamicTypes(ids: ["00000000-0000-0000-0000-deadbeef0000"]) {
                    overallStatus
                    results { index errors { code path message } }
                  }
                }
                """)
                .execute()
                .path("deleteDynamicTypes")
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

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void nonAdminDeleteDynamicTypeRejected()
    {
        tester.document("""
                mutation {
                  deleteDynamicTypes(ids: ["any-id"]) { overallStatus }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "non-admin delete must be rejected");
                    String joined = errs.toString();
                    assertTrue(joined.contains("PERMISSION_DENIED") || joined.toLowerCase().contains("admin"),
                            () -> "expected admin-only rejection, got " + joined);
                });
    }
}
