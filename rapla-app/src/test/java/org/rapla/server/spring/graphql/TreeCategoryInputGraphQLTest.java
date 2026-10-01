package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 096 OQ6 — a tree-category attribute (ORGANIZATION root → write input {@code ID}) must only
 * accept categories below its {@code @rootCategory}. The fixture copy adds {@code tree/inner/leaf}
 * and re-roots {@code resource3.testdep} (no values in the fixture) onto {@code tree}.
 * Security filters are off: this pins the mapper, not the auth chain; {@code @WithMockUser} carries the create gate.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Tag("e2e")
class TreeCategoryInputGraphQLTest
{
    static final String LEAF = "c0a1b2c3-0000-4000-8000-000000000003";
    static final String DEPARTMENT_CHILD = "c25d8971-2936-4ea9-b2d9-af63802155cc";
    static final String USER_GROUP = "c2642cdd-6144-4e66-bf54-b3ff76c76f04";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        String xml;
        try (InputStream in = TreeCategoryInputGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String stamp = "created-at=\"2026-10-01T00:00:00.000Z\" last-changed=\"2026-10-01T00:00:00.000Z\"";
        xml = replaceOnce(xml, "<rapla:categories>", "<rapla:categories>\n"
                + "<rapla:category " + stamp + " id=\"c0a1b2c3-0000-4000-8000-000000000001\" key=\"tree\"><doc:name lang=\"en\">tree</doc:name>\n"
                + "<rapla:category " + stamp + " id=\"c0a1b2c3-0000-4000-8000-000000000002\" key=\"inner\"><doc:name lang=\"en\">inner</doc:name>\n"
                + "<rapla:category " + stamp + " id=\"" + LEAF + "\" key=\"leaf\"><doc:name lang=\"en\">leaf</doc:name></rapla:category>\n"
                + "</rapla:category></rapla:category>");
        String testdep = "<relax:element name=\"testdep\">\n                  <relax:data type=\"rapla:category\"/>\n"
                + "                  <rapla:constraint name=\"root-category\">category[key='";
        xml = replaceOnce(xml, testdep + "department']", testdep + "tree']");
        dataFile = tempDir.resolve("rapla-data.xml");
        Files.writeString(dataFile, xml);
    }

    private static String replaceOnce(String s, String anchor, String replacement)
    {
        int at = s.indexOf(anchor);
        assertTrue(at >= 0 && s.indexOf(anchor, at + 1) < 0, "fixture anchor must occur exactly once: " + anchor);
        return s.replace(anchor, replacement);
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

    private Object storedTestdep(String categoryId)
    {
        Map<String, Object> cls = tester.document("""
                mutation ($id: ID!, $c: ID!) {
                  createResource(input: { id: $id, typeKey: "resource3",
                    classification: { resource3: { name: "Baum", testdep: $c } } }) {
                    classification { ... on resource3Classification { testdep { id } } }
                  }
                }
                """)
                .variable("id", UUID.randomUUID().toString())
                .variable("c", categoryId)
                .execute()
                .path("createResource.classification")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        Object testdep = cls.get("testdep");
        return testdep == null ? null : ((Map<?, ?>) testdep).get("id");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void categoryBelowTheRootIsStored()
    {
        assertEquals(LEAF, storedTestdep(LEAF));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void categoryOutsideTheRootIsNeverStored()
    {
        assertNull(storedTestdep(DEPARTMENT_CHILD), "a category of another root must not be stored");
        assertNull(storedTestdep(USER_GROUP), "a permission group must not be stored as an attribute value");
    }
}
