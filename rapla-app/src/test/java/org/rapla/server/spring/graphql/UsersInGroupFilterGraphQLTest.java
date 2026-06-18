package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

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
 * PRD 069 — tier-3 for the server-side {@code UserFilter.inGroup} group filter
 * on {@code users(filter:)}.
 *
 * <p>Fixture (testdefault.xml): monty is a member of {@code my-group},
 * {@code powerplant} and {@code powerplant/powerplant-admins}; homer is the
 * admin. Caller is homer (sees all users).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class UsersInGroupFilterGraphQLTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = UsersInGroupFilterGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

    private List<String> usernames(String inGroupArg)
    {
        return tester.document("{ users(filter: { inGroup: " + inGroupArg + " }) { username } }")
                .execute().path("users[*].username").entityList(String.class).get();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void filtersToMembersOfTopLevelGroup()
    {
        List<String> us = usernames("[\"my-group\"]");
        assertTrue(us.contains("monty"), () -> "monty is in my-group, got " + us);
        assertFalse(us.contains("homer"), () -> "homer is not in my-group, got " + us);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void nestedPathResolves()
    {
        // monty is a direct member of powerplant/powerplant-admins
        assertTrue(usernames("[\"powerplant/powerplant-admins\"]").contains("monty"));
        // and belongsTo the parent group too (membership includes sub-groups)
        assertTrue(usernames("[\"powerplant\"]").contains("monty"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void groupWithNoMatchingMembersExcludesMonty()
    {
        assertFalse(usernames("[\"registerer\"]").contains("monty"),
                "monty is not in registerer");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unionAcrossGroups()
    {
        // my-group ∪ registerer — monty matches via my-group.
        assertTrue(usernames("[\"registerer\", \"my-group\"]").contains("monty"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unknownGroupPathIsInvalidValue()
    {
        tester.document("{ users(filter: { inGroup: [\"does-not-exist\"] }) { username } }")
                .execute().errors().satisfy(errs -> {
                    assertFalse(errs.isEmpty());
                    assertEquals("INVALID_VALUE", errs.get(0).getExtensions().get("code"));
                });
    }
}
