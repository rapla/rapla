package org.rapla.server.spring.graphql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 119 D11 — {@code Resource.groupPaths} on the lean picker list, and §12: a group only ever arrives on a resource
 * the caller can read, and a categorization value that is itself a resource the caller cannot read is left out (its
 * name would leak otherwise). Fixture {@code testdefault.xml}: rooms "Room A66" (springfield-powerplant) and "erwin"
 * (set to channel-6) grouped by the {@code room} category attribute {@code belongsto}; "Room A66.1" grouped by its
 * room reference {@code resource1.a1} → "Room A66".
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ResourceTreeFieldsGraphQLTest
{
    private static final String ROOM_A66 = "c24ce517-4697-4e52-9917-ec000c84563c";
    private static final String ROOM_A66_1 = "rdd6b473-7c77-4344-a73d-1f27008341cb";
    private static final String ERWIN = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";

    private static final ParameterizedTypeReference<Map<String, Object>> ROW = new ParameterizedTypeReference<>() {};

    @TempDir
    static Path tempDir;
    static Path dataFile;
    private static boolean seeded;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ResourceTreeFieldsGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired CachableStorageOperator operator;
    @Autowired RaplaFacade facade;

    WebTestClient client;
    HttpGraphQlTester tester;

    @BeforeEach
    void setUp() throws Exception
    {
        client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
        seedOnce();
    }

    /** By English name: the app's startup key migration rewrites non-GraphQL keys like {@code channel-6}. */
    private Category department(String name)
    {
        Category department = null;
        for (Category c : operator.getSuperCategory().getCategoryList())
        {
            if ("department".equals(c.getName(Locale.ENGLISH))) department = c;
        }
        assertNotNull(department, "fixture category 'department'");
        for (Category c : department.getCategoryList())
        {
            if (name.equals(c.getName(Locale.ENGLISH))) return c;
        }
        throw new AssertionError("fixture category " + name);
    }

    private static List<List<String>> path(String name)
    {
        return List.of(List.of(name));
    }

    /** Room A66 hidden from monty; erwin and Room A66.1 readable by everyone. */
    private void seedOnce() throws Exception
    {
        if (seeded) return;
        DynamicType room = facade.edit(operator.getDynamicType("room"));
        room.getAttribute("belongsto").setAnnotation(AttributeAnnotations.KEY_CATEGORIZATION, "true");
        DynamicType resource1 = facade.edit(operator.getDynamicType("resource1"));
        resource1.getAttribute("a1").setAnnotation(AttributeAnnotations.KEY_CATEGORIZATION, "true");
        facade.storeObjects(new Entity[] { room, resource1 });
        Attribute belongsto = operator.getDynamicType("room").getAttribute("belongsto");

        Allocatable hidden = facade.edit(operator.resolve(ROOM_A66, Allocatable.class));
        clearPermissions(hidden);
        Allocatable erwin = facade.edit(operator.resolve(ERWIN, Allocatable.class));
        everyoneReads(erwin);
        erwin.getClassification().setValues(belongsto, List.of(department("channel-6")));
        Allocatable part = facade.edit(operator.resolve(ROOM_A66_1, Allocatable.class));
        everyoneReads(part);
        facade.storeObjects(new Entity[] { hidden, erwin, part });
        seeded = true;
    }

    private static void clearPermissions(Allocatable a)
    {
        for (Permission p : a.getPermissionList().toArray(new Permission[0])) a.removePermission(p);
    }

    private static void everyoneReads(Allocatable a)
    {
        clearPermissions(a);
        Permission read = a.newPermission();
        read.setAccessLevel(Permission.AccessLevel.READ);
        a.addPermission(read);
    }

    private Map<String, Map<String, Object>> rows()
    {
        List<Map<String, Object>> list = tester.document("{ resources(filter: { idIn: [\"" + ROOM_A66 + "\", \"" + ERWIN
                        + "\", \"" + ROOM_A66_1 + "\"] }) { id groupPaths } }")
                .execute()
                .path("resources")
                .entityList(ROW)
                .get();
        return list.stream().collect(Collectors.toMap(m -> (String) m.get("id"), m -> m));
    }

    private String raw(String query)
    {
        return client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void groupPathsNameTheCategorizationValues()
    {
        Map<String, Map<String, Object>> byId = rows();
        assertEquals(path(department("springfield powerplant").getName(Locale.ENGLISH)), byId.get(ROOM_A66).get("groupPaths"));
        assertEquals(path(department("channel-6").getName(Locale.ENGLISH)), byId.get(ERWIN).get("groupPaths"));
        assertEquals(path("Room A66"), byId.get(ROOM_A66_1).get("groupPaths"));
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void anUnreadableResourceContributesNeitherItsIdNorItsGroupNorItsName()
    {
        Map<String, Map<String, Object>> byId = rows();
        assertFalse(byId.containsKey(ROOM_A66), "precondition: monty must not read Room A66");
        assertEquals(path(department("channel-6").getName(Locale.ENGLISH)), byId.get(ERWIN).get("groupPaths"));
        assertEquals(List.of(), byId.get(ROOM_A66_1).get("groupPaths"));
        String body = raw("{ resources { id groupPaths } }");
        assertFalse(body.contains(ROOM_A66), body);
        assertFalse(body.contains(department("springfield powerplant").getName(Locale.ENGLISH)), body);
        assertFalse(body.contains("[\"Room A66\"]"), body);
    }
}
