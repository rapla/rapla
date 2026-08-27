package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * Tier-3 coverage for the Abgleich worklist query. This context has NO snapshot provider —
 * staging is opt-in — so the query must answer empty rather than fail, and must never surface
 * anything to an anonymous caller. The permission semantics themselves are pinned at tier 2 in
 * {@code ExternalEventWorklistLeakTest}, where real allocatables and a real
 * {@code PermissionController} are cheap.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ExternalEventWorklistGraphQLTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ExternalEventWorklistGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaIncludesTheWorklistQuery()
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
        assertTrue(names.contains("externalEventWorklist"), () -> "missing Query.externalEventWorklist in " + names);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void worklistItemExposesTheDerivedState()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "ExternalEventItem") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.containsAll(List.of("sourceItemId", "scopeKey", "state", "columns", "changedSince",
                "ignoredSince", "boundReservationId")), () -> "ExternalEventItem missing fields; got " + names);
    }

    /** Staging is opt-in: without a provider the query answers empty instead of erroring.
     *  Whether a deployment syncs at all is NOT answered here — the SPA learns that from
     *  {@code GET /api/externaleventimport/metadata}, which only exists where an import
     *  implementation is enabled. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void withoutASnapshotProviderTheWorklistIsEmpty()
    {
        Integer total = tester.document("""
                query { externalEventWorklist(allocatableIds: ["a-any"]) {
                    groups { allocatableId } counts { total } } }
                """)
                .execute()
                .path("externalEventWorklist.counts.total")
                .entity(Integer.class)
                .get();
        assertEquals(0, total);
        tester.document("""
                query { externalEventWorklist(allocatableIds: ["a-any"]) { groups { allocatableId } } }
                """)
                .execute()
                .path("externalEventWorklist.groups")
                .entityList(Object.class)
                .hasSize(0);
    }

    @Test
    @WithAnonymousUser
    void anonymousWorklistIsRejected()
    {
        tester.document("""
                query { externalEventWorklist(allocatableIds: ["a-any"]) { counts { total } } }
                """)
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();
    }
}
