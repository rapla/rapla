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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 064 — tier-3 tests for the conflicts query. testdefault.xml has no
 * known overlapping reservations for the happy-path coverage, so the v1
 * tests focus on schema smoke + §12 enforcement (empty result on
 * anonymous + unknown id). Happy-path coverage with a conflict fixture
 * is deferred to a follow-up tier-3 spec (analog to PRD 055's deferred
 * restriction round-trip).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ConflictGraphQLControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ConflictGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaIncludesConflictsQuery()
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
        assertTrue(names.contains("conflicts"), () -> "missing Query.conflicts in " + names);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void conflictTypeShape()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Conflict") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.containsAll(List.of(
                "id", "allocatable", "reservation1", "reservation2",
                "appointment1", "appointment2", "startDate")),
                () -> "Conflict type missing expected fields; got " + names);
    }

    @Test
    @WithAnonymousUser
    void anonymousConflictsReturnsEmpty()
    {
        List<Map<String, Object>> result = tester.document("""
                query { conflicts(reservationId: "any-id") { id } }
                """)
                .execute()
                .path("conflicts")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertTrue(result.isEmpty(), "anonymous must get [] not " + result);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unknownReservationIdReturnsEmpty()
    {
        List<Map<String, Object>> result = tester.document("""
                query { conflicts(reservationId: "00000000-0000-0000-0000-deadbeef0000") { id } }
                """)
                .execute()
                .path("conflicts")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(0, result.size(),
                "unknown id must return [] (existence not leaked)");
    }
}
