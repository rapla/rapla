package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 113 § 5d (W2) — tier-3 for {@code saveDynamicType}'s {@code typeAccess} / {@code resourceInstanceDefaults} /
 * {@code eventInstanceDefaults}: global-admin gate, wrong-list rejection, partial replace per API list, the
 * instance-default window rules, inheritance by new instances and no spurious schema rebuild.
 *
 * <p>Every test works on a type it creates itself (one STRING attribute {@code name}), so the fixture's
 * room/event types and the other tests stay untouched.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class DynamicTypePermissionWriteGraphQLTest
{
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = DynamicTypePermissionWriteGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
    @Autowired
    HotSwappableGraphQlSource graphQlSource;

    HttpGraphQlTester tester;
    Category myGroup;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
        myGroup = operator.getSuperCategory().getCategory("user-groups").getCategory("my-group");
    }

    private static String freshKey()
    {
        return "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** A saveDynamicType document for a type with one STRING attribute; {@code extra} is appended to the input. */
    private static String save(String id, String key, String classificationType, String extra)
    {
        return "mutation { saveDynamicType(input: { " + (id == null ? "" : "id: \"" + id + "\", ")
                + "key: \"" + key + "\", name: { default: \"" + key + "\" }, classificationType: " + classificationType + ","
                + " attributes: [{ key: \"name\", name: { default: \"Name\" }, valueType: STRING, multiplicity: SINGLE, required: false }]"
                + (extra == null ? "" : ", " + extra) + " }) { id } }";
    }

    private String create(String key, String classificationType)
    {
        return tester.document(save(null, key, classificationType, null)).execute()
                .path("saveDynamicType.id").entity(String.class).get();
    }

    private String group(String level)
    {
        return "{ principal: { groupId: \"" + myGroup.getId() + "\" }, level: " + level + " }";
    }

    @SuppressWarnings("unchecked")
    private List<String> levels(String key, String list)
    {
        Map<String, Object> type = tester.document("{ type(key: \"" + key + "\") { typeAccess { level } instanceDefaults { level } } }")
                .execute().path("type").entity(MAP).get();
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) type.get(list))
        {
            out.add((String) row.get("level"));
        }
        return out;
    }

    private void fails(String document, String code, String path)
    {
        tester.document(document).execute().errors().satisfy(errs -> {
            String joined = errs.toString();
            assertTrue(joined.contains(code) && (path == null || joined.contains(path)),
                    () -> "expected " + code + (path == null ? "" : " @ " + path) + ", got: " + joined);
        });
    }

    private void failsValidation(String document)
    {
        tester.document(document).execute().errors().satisfy(errs -> assertTrue(
                errs.toString().contains("ValidationError"), () -> "expected a validation error, got: " + errs));
    }

    // === 1 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void nonAdminSaveDynamicTypeDenied()
    {
        String roomId = operator.getDynamicType("room").getId();
        fails(save(roomId, "room", "RESOURCE", "typeAccess: [{ principal: { everyone: true }, level: CREATE }]"),
                "PERMISSION_DENIED", null);
    }

    // === 2 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void wrongInstanceListRejected()
    {
        String eventKey = freshKey();
        String eventId = create(eventKey, "RESERVATION");
        fails(save(eventId, eventKey, "RESERVATION", "resourceInstanceDefaults: [" + group("READ") + "]"),
                "INVALID_VALUE", "input.resourceInstanceDefaults");

        String roomKey = freshKey();
        String roomId = create(roomKey, "RESOURCE");
        fails(save(roomId, roomKey, "RESOURCE", "eventInstanceDefaults: [" + group("READ") + "]"),
                "INVALID_VALUE", "input.eventInstanceDefaults");
    }

    // === 3 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void partialReplaceKeepsOtherSubset()
    {
        String key = freshKey();
        String id = create(key, "RESOURCE");

        tester.document(save(id, key, "RESOURCE", "typeAccess: [{ principal: { everyone: true }, level: READ_TYPE }, "
                + "{ principal: { groupId: \"" + myGroup.getId() + "\" }, level: CREATE }], "
                + "resourceInstanceDefaults: [" + group("EDIT") + "]")).execute().errors().verify();
        assertEquals(List.of("READ_TYPE", "CREATE"), levels(key, "typeAccess"));
        assertEquals(List.of("EDIT"), levels(key, "instanceDefaults"));

        tester.document(save(id, key, "RESOURCE", "typeAccess: [{ principal: { everyone: true }, level: CREATE }]"))
                .execute().errors().verify();
        assertEquals(List.of("CREATE"), levels(key, "typeAccess"));
        assertEquals(List.of("EDIT"), levels(key, "instanceDefaults"), "instance rows untouched by a typeAccess-only save");

        tester.document(save(id, key, "RESOURCE", "resourceInstanceDefaults: [" + group("ADMIN")
                + ", { principal: { everyone: true }, level: READ }]")).execute().errors().verify();
        assertEquals(List.of("CREATE"), levels(key, "typeAccess"), "type rows untouched by an instance-only save");
        assertEquals(List.of("ADMIN", "READ"), levels(key, "instanceDefaults"));
    }

    // === 4 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminInTypeAccessFailsValidation()
    {
        String key = freshKey();
        failsValidation(save(create(key, "RESOURCE"), key, "RESOURCE", "typeAccess: [{ principal: { everyone: true }, level: ADMIN }]"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void userPrincipalInTypeAccessFailsValidation() throws Exception
    {
        String key = freshKey();
        String homerId = operator.getUser("homer").getId();
        failsValidation(save(create(key, "RESOURCE"), key, "RESOURCE",
                "typeAccess: [{ principal: { userId: \"" + homerId + "\" }, level: READ_TYPE }]"));
    }

    // === 5 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void instanceDefaultsWindowRules()
    {
        String key = freshKey();
        String id = create(key, "RESOURCE");
        fails(save(id, key, "RESOURCE", "resourceInstanceDefaults: [{ principal: { everyone: true }, level: READ, start: \"2026-01-01T00:00:00\" }]"),
                "INVALID_VALUE", "input.resourceInstanceDefaults[0].level");
        fails(save(id, key, "RESOURCE", "resourceInstanceDefaults: [{ principal: { everyone: true }, level: ALLOCATE,"
                + " start: \"2026-01-01T00:00:00\", minAdvance: 1 }]"), "INVALID_VALUE", "input.resourceInstanceDefaults[0].start");
        fails(save(id, key, "RESOURCE", "resourceInstanceDefaults: [{ principal: { everyone: true }, level: EDIT,"
                + " start: \"2026-02-01T00:00:00\", end: \"2026-01-01T00:00:00\" }]"), "INVALID_VALUE", "input.resourceInstanceDefaults[0].end");
        tester.document(save(id, key, "RESOURCE", "resourceInstanceDefaults: [{ principal: { everyone: true }, level: REQUEST,"
                + " minAdvance: 1, maxAdvance: 14 }]")).execute().errors().verify();
        assertEquals(List.of("REQUEST"), levels(key, "instanceDefaults"));
    }

    // === 6 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void newInstanceInheritsEditedDefaults()
    {
        String key = freshKey();
        String id = create(key, "RESOURCE");
        tester.document(save(id, key, "RESOURCE", "resourceInstanceDefaults: [" + group("EDIT")
                + ", { principal: { everyone: true }, level: READ }]")).execute().errors().verify();
        graphQlSource.rebuild();

        String allocatableId = UUID.randomUUID().toString();
        tester.document("mutation { createResource(input: { id: \"" + allocatableId + "\", typeKey: \"" + key
                + "\", classification: { " + key + ": {} } }) { id } }").execute().errors().verify();
        PermissionContainer stored = operator.tryResolve(new ReferenceInfo<>(allocatableId, Allocatable.class));
        List<String> levels = new ArrayList<>();
        stored.getPermissionList().forEach(p -> levels.add(p.getAccessLevel().name()));
        assertEquals(List.of("EDIT", "READ"), levels);
    }

    // === 9 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaRebuildNotTriggeredByPermissionOnlySave()
    {
        String key = freshKey();
        String id = create(key, "RESOURCE");
        graphQlSource.rebuild();
        tester.document(save(id, key, "RESOURCE", "typeAccess: [{ principal: { everyone: true }, level: CREATE }]"))
                .execute().errors().verify();
        assertFalse(graphQlSource.rebuild(), "a permission-only save must leave the generated SDL unchanged");
    }

    // === P1s — a create without lists seeds the Swing default rows ============

    @SuppressWarnings("unchecked")
    private List<String> principals(String key, String list)
    {
        Map<String, Object> type = tester.document("{ type(key: \"" + key + "\") { typeAccess { level principal { group { id } everyone } }"
                + " instanceDefaults { level principal { group { id } everyone } } } }").execute().path("type").entity(MAP).get();
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) type.get(list))
        {
            Map<String, Object> principal = (Map<String, Object>) row.get("principal");
            Map<String, Object> group = (Map<String, Object>) principal.get("group");
            out.add(row.get("level") + ":" + (group != null ? group.get("id") : "everyone"));
        }
        return out;
    }

    private String groupId(String key)
    {
        return operator.getSuperCategory().getCategory("user-groups").getCategory(key).getId();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createResourceTypeWithoutListsSeedsDefaults()
    {
        String key = freshKey();
        create(key, "RESOURCE");
        assertEquals(List.of("READ_TYPE:everyone", "CREATE:" + groupId("registerer")), principals(key, "typeAccess"));
        assertEquals(List.of("ALLOCATE_CONFLICTS:everyone"), principals(key, "instanceDefaults"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createEventTypeWithoutListsSeedsDefaults()
    {
        String key = freshKey();
        create(key, "RESERVATION");
        assertEquals(List.of("READ_TYPE:everyone", "CREATE:" + groupId("create-events")), principals(key, "typeAccess"));
        assertEquals(List.of("READ:" + groupId("read-events-from-others")), principals(key, "instanceDefaults"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void explicitListOnCreateSuppressesDefaultsAndUpdateNeverSeeds()
    {
        String key = freshKey();
        String id = tester.document(save(null, key, "RESOURCE", "typeAccess: [{ principal: { everyone: true }, level: CREATE }]"))
                .execute().path("saveDynamicType.id").entity(String.class).get();
        assertEquals(List.of("CREATE:everyone"), principals(key, "typeAccess"));
        assertEquals(List.of(), principals(key, "instanceDefaults"));

        tester.document(save(id, key, "RESOURCE", null)).execute().errors().verify();
        assertEquals(List.of("CREATE:everyone"), principals(key, "typeAccess"), "an update without lists keeps the rows");
        assertEquals(List.of(), principals(key, "instanceDefaults"), "an update never seeds defaults");
    }
}
