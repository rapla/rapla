package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 069 — tier-3 for the admin-scoped access-by-target filters on
 * {@code allocatables(filter:)} and {@code reservations(filter:)}.
 *
 * <p>Fixture (testdefault.xml): homer (admin), monty (non-admin, not a
 * group-admin); user-groups subtree incl. my-group.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ResourceAccessQueryGraphQLTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ResourceAccessQueryGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
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

    // === happy path ==========================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminTargetAtReadEqualsUnfilteredSet()
    {
        // homer is admin → reads everything; target homer (admin) has access to
        // everything → the access-filtered set must equal the unfiltered set.
        List<String> unfiltered = tester.document("{ allocatables { id } }")
                .execute().path("allocatables[*].id").entityList(String.class).get();
        List<String> filtered = tester.document(
                "{ allocatables(filter: { accessibleByUsername: \"homer\", accessLevel: READ }) { id } }")
                .execute().path("allocatables[*].id").entityList(String.class).get();
        assertFalse(unfiltered.isEmpty(), "fixture must expose some allocatables");
        assertEquals(Set.copyOf(unfiltered), Set.copyOf(filtered),
                "admin target at READ must equal the unfiltered readable set");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminCanQueryByUserIdAndByGroupWithoutError()
    {
        // by-id form (homer's own id) — just assert it resolves without error.
        String homerId = tester.document("{ user(username: \"homer\") { id } }")
                .execute().path("user.id").entity(String.class).get();
        tester.document("query($id: ID!) { allocatables(filter: { accessibleByUserId: $id, accessLevel: READ }) { id } }")
                .variable("id", homerId)
                .execute().errors().verify();   // no errors
        // group form — admin may target any group; result may be empty, must not error.
        tester.document("{ allocatables(filter: { accessibleByGroup: [\"my-group\"], accessLevel: READ }) { id } }")
                .execute().errors().verify();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void eventsAccessByUserResolvesWithoutError()
    {
        tester.document("""
                { reservations(filter: {
                    from: "2026-01-01T00:00:00", to: "2026-12-31T00:00:00",
                    accessibleByUsername: "monty", accessLevel: READ }) { id } }
                """)
                .execute().errors().verify();
    }

    // === §12 leak tests ======================================================

    @Test
    @WithMockUser(username = "monty")
    void nonAdminTargetingOthersIsForbiddenAndUniform()
    {
        // out-of-scope existing user vs non-existent user → identical FORBIDDEN
        // response (no existence leak).
        String[] outOfScope = new String[1];
        tester.document("{ allocatables(filter: { accessibleByUsername: \"homer\" }) { id } }")
                .execute().errors().satisfy(errs -> {
                    assertFalse(errs.isEmpty());
                    assertEquals("FORBIDDEN", errs.get(0).getExtensions().get("code"));
                    outOfScope[0] = errs.get(0).getMessage();
                });

        String[] unknown = new String[1];
        tester.document("{ allocatables(filter: { accessibleByUsername: \"ghost_does_not_exist\" }) { id } }")
                .execute().errors().satisfy(errs -> {
                    assertFalse(errs.isEmpty());
                    assertEquals("FORBIDDEN", errs.get(0).getExtensions().get("code"));
                    unknown[0] = errs.get(0).getMessage();
                });

        assertEquals(outOfScope[0], unknown[0],
                "out-of-scope and unknown handles must return byte-identical messages (§12)");
    }

    @Test
    @WithMockUser(username = "monty")
    void nonAdminGroupSelectorForbidden()
    {
        tester.document("{ allocatables(filter: { accessibleByGroup: [\"my-group\"] }) { id } }")
                .execute().errors().satisfy(errs -> {
                    assertFalse(errs.isEmpty());
                    assertEquals("FORBIDDEN", errs.get(0).getExtensions().get("code"));
                });
    }

    @Test
    @WithMockUser(username = "monty")
    void nonAdminEventsTargetingOthersForbidden()
    {
        tester.document("""
                { reservations(filter: {
                    from: "2026-01-01T00:00:00", to: "2026-12-31T00:00:00",
                    accessibleByUsername: "homer" }) { id } }
                """)
                .execute().errors().satisfy(errs -> {
                    assertFalse(errs.isEmpty());
                    assertEquals("FORBIDDEN", errs.get(0).getExtensions().get("code"));
                });
    }

    // === validation ==========================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void multipleSelectorsRejected()
    {
        tester.document(
                "{ allocatables(filter: { accessibleByUsername: \"homer\", accessibleByGroup: [\"my-group\"] }) { id } }")
                .execute().errors().satisfy(errs -> {
                    assertFalse(errs.isEmpty());
                    assertTrue("INVALID_VALUE".equals(errs.get(0).getExtensions().get("code")),
                            () -> "expected INVALID_VALUE, got " + errs);
                });
    }
}
