package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Category;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 113 § 5e (P6) — tier-3 for {@code Group.parent} / {@code Group.children}: same gate as {@code groups()},
 * never outside the {@code user-groups} subtree (the root itself is not a group), no member expansion.
 *
 * <p>Fixture (testdefault.xml) user-groups subtree: my-group, powerplant { powerplant-admins, powerplant-staff },
 * registerer, modify-preferences, read-events-from-others, create-events.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupHierarchyGraphQLTest
{
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = GroupHierarchyGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
    @Autowired
    CachableStorageOperator operator;

    WebTestClient client;
    HttpGraphQlTester tester;
    Category userGroups;

    @BeforeEach
    void setUp()
    {
        client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
        userGroups = operator.getSuperCategory().getCategory("user-groups");
    }

    private String raw(String document)
    {
        return client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", document))
                .exchange().expectBody(String.class).returnResult().getResponseBody();
    }

    private Map<String, Object> group(String id, String selection)
    {
        return tester.document("{ group(id: \"" + id + "\") { " + selection + " } }").execute().path("group").entity(MAP).get();
    }

    @Test
    @WithMockUser(username = "monty")
    void parentOfTopLevelGroupIsNull()
    {
        Map<String, Object> myGroup = group(userGroups.getCategory("my-group").getId(), "id parent { id }");
        assertNull(myGroup.get("parent"), "the user-groups root is not a group");
    }

    @Test
    @WithMockUser(username = "monty")
    @SuppressWarnings("unchecked")
    void childrenRoundTrip()
    {
        Category powerplant = userGroups.getCategory("powerplant");
        Map<String, Object> pp = group(powerplant.getId(), "children { id key parent { id } }");
        List<Map<String, Object>> children = (List<Map<String, Object>>) pp.get("children");
        assertEquals(Set.of("powerplant-admins", "powerplant-staff"),
                children.stream().map(c -> (String) c.get("key")).collect(Collectors.toSet()));
        for (Map<String, Object> child : children)
        {
            assertEquals(powerplant.getId(), ((Map<String, Object>) child.get("parent")).get("id"));
        }
        Map<String, Object> admins = group(powerplant.getCategory("powerplant-admins").getId(), "children { id }");
        assertTrue(((List<?>) admins.get("children")).isEmpty(), "a leaf group has no children");
    }

    @Test
    @WithAnonymousUser
    void anonymousAnswersLikeGroups()
    {
        String plain = raw("{ groups { id } }");
        assertTrue(plain.contains("\"errors\""), "precondition: groups() rejects anonymous callers: " + plain);
        assertEquals(plain, raw("{ groups { id parent { id } children { id } } }"),
                "parent/children add nothing to what an anonymous caller gets from groups()");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void nonGroupCategoryNeverAppears()
    {
        String outside = null;
        for (Category c : operator.getSuperCategory().getCategories())
        {
            if (!"user-groups".equals(c.getKey())) { outside = c.getId(); break; }
        }
        assertTrue(outside != null, "fixture must have a category outside user-groups");
        tester.document("{ group(id: \"" + outside + "\") { id } }").execute().path("group").valueIsNull();

        String all = raw("{ groups { id parent { id key } children { id key } } }");
        assertFalse(all.contains("\"errors\""), all);
        assertFalse(all.contains(outside), "a non-group category never surfaces: " + all);
        assertFalse(all.contains(userGroups.getId()), "the user-groups root never surfaces as parent: " + all);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    @SuppressWarnings("unchecked")
    void noUserExpansionThroughChildren()
    {
        Map<String, Object> type = tester.document("{ __type(name: \"Group\") { fields { name } } }")
                .execute().path("__type").entity(MAP).get();
        Set<String> fields = ((List<Map<String, Object>>) type.get("fields")).stream()
                .map(f -> (String) f.get("name")).collect(Collectors.toSet());
        assertEquals(Set.of("id", "key", "name", "parent", "children"), fields, "Group carries no member expansion");
    }
}
