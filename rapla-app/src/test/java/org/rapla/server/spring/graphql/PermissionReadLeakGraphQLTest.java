package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 113 § 5b (R1) — tier-3 §12 leak tests for the read-side permission fields:
 * {@code permissions} / {@code canAdmin} on Allocatable, Reservation, EventTemplate and Period,
 * the {@code DynamicType.typeAccess} / {@code instanceDefaults} projection and the two
 * template queries.
 *
 * <p>Fixture (testdefault.xml): homer (admin), monty (non-admin, member of my-group); seeded once
 * per class: resource/reservation A (my-group EDIT), B (monty ADMIN + lenny READ + everyone READ),
 * C (no rows, unreadable for monty), a template readable by monty and one that is not, one period,
 * and the non-admin user lenny whom monty cannot administer.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class PermissionReadLeakGraphQLTest
{
    private static final ParameterizedTypeReference<Map<String, Object>> ROW = new ParameterizedTypeReference<>() {};

    @TempDir
    static Path tempDir;
    static Path dataFile;

    static boolean seeded;
    static String resA, resB, resC, resD, evA, evB, evC, tplRead, tplPriv, periodId, lennyId;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PermissionReadLeakGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
    RaplaFacade facade;

    WebTestClient client;
    HttpGraphQlTester tester;

    @BeforeEach
    void setUp() throws Exception
    {
        client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
        seedOnce();
    }

    private void seedOnce() throws Exception
    {
        if (seeded) return;
        User homer = operator.getUser("homer");
        User monty = operator.getUser("monty");
        Category myGroup = operator.getSuperCategory().getCategory("user-groups").getCategory("my-group");

        User lenny = facade.newUser();
        lenny.setUsername("lenny");
        lenny.setName("Lenny");
        facade.store(lenny);
        lennyId = operator.getUser("lenny").getId();

        DynamicType room = facade.edit(operator.getDynamicType("room"));
        room.addPermission(row(AccessLevel.ADMIN, homer, null).on(room));
        facade.store(room);

        resA = store(resource("PRD113-A", homer), List.of(row(AccessLevel.EDIT, null, myGroup)));
        resB = store(resource("PRD113-B", homer), List.of(row(AccessLevel.ADMIN, monty, null), row(AccessLevel.READ, operator.getUser("lenny"), null),
                row(AccessLevel.READ, null, null)));
        resC = store(resource("PRD113-C", homer), List.of());
        Allocatable d = resource("PRD113-D", homer);
        Permission absolute = row(AccessLevel.ALLOCATE, null, myGroup).on(d);
        absolute.setStart(LocalDateTime.of(2026, 3, 1, 8, 0));
        absolute.setEnd(LocalDateTime.of(2026, 7, 31, 18, 30));
        Permission relative = row(AccessLevel.REQUEST, null, myGroup).on(d);
        relative.setMinAdvance(2);
        relative.setMaxAdvance(30);
        for (Permission p : d.getPermissionList().toArray(new Permission[0])) d.removePermission(p);
        d.addPermission(absolute);
        d.addPermission(relative);
        resD = store(d, null);

        evA = store(event(homer), List.of(row(AccessLevel.EDIT, null, myGroup)));
        evB = store(event(homer), List.of(row(AccessLevel.ADMIN, monty, null)));
        evC = store(event(homer), List.of());

        DynamicType templateType = operator.getDynamicType(StorageOperator.RAPLA_TEMPLATE);
        tplRead = store(named(templateType, "PRD113-TPL-READ", homer), List.of(row(AccessLevel.READ, null, myGroup)));
        tplPriv = store(named(templateType, "PRD113-TPL-PRIV", homer), List.of());

        DynamicType periodType = operator.getDynamicType(StorageOperator.PERIOD_TYPE);
        Classification pc = periodType.newClassification();
        pc.setValue("name", "PRD113-PERIOD");
        pc.setValue("start", LocalDateTime.of(2026, 3, 1, 0, 0));
        pc.setValue("end", LocalDateTime.of(2026, 7, 31, 0, 0));
        Allocatable period = facade.newAllocatable(pc, homer);
        periodId = store(period, null);
        seeded = true;
    }

    private Allocatable resource(String name, User owner) throws Exception
    {
        return named(operator.getDynamicType("room"), name, owner);
    }

    private Allocatable named(DynamicType type, String name, User owner) throws Exception
    {
        Classification c = type.newClassification();
        c.setValue("name", name);
        return facade.newAllocatable(c, owner);
    }

    private Reservation event(User owner) throws Exception
    {
        DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Reservation r = facade.newReservation(eventType.newClassification(), owner);
        Appointment app = facade.newAppointmentWithUser(LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), owner);
        r.addAppointment(app);
        return r;
    }

    private record RowSpec(AccessLevel level, User user, Category group)
    {
        Permission on(PermissionContainer container)
        {
            Permission p = container.newPermission();
            p.setAccessLevel(level);
            if (user != null) p.setUser(user);
            if (group != null) p.setGroup(group);
            return p;
        }
    }

    private static RowSpec row(AccessLevel level, User user, Category group)
    {
        return new RowSpec(level, user, group);
    }

    private String store(Entity<?> entity, List<RowSpec> rows) throws Exception
    {
        PermissionContainer container = (PermissionContainer) entity;
        if (rows != null)
        {
            for (Permission p : container.getPermissionList().toArray(new Permission[0]))
            {
                container.removePermission(p);
            }
            for (RowSpec r : rows)
            {
                container.addPermission(r.on(container));
            }
        }
        facade.storeObjects(new Entity[] { entity });
        return entity.getId();
    }

    private String raw(String query)
    {
        return client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    private List<Map<String, Object>> rows(String query, String path)
    {
        return tester.document(query).execute().path(path).entityList(ROW).get();
    }

    private static Map<String, Map<String, Object>> byId(List<Map<String, Object>> rows)
    {
        return rows.stream().collect(Collectors.toMap(m -> (String) m.get("id"), m -> m));
    }

    private static String ids(String... ids)
    {
        return "[" + java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").collect(Collectors.joining(",")) + "]";
    }

    // === 1 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void nonAdminPermissionsNullWhereNotAdmin()
    {
        Map<String, Map<String, Object>> res = byId(rows(
                "{ resources(filter: { idIn: " + ids(resA, resB, resC) + " }) { id canAdmin permissions { level } } }",
                "resources"));
        assertEquals(Set.of(resA, resB), res.keySet(), "C is unreadable and must be absent");
        assertEquals(false, res.get(resA).get("canAdmin"));
        assertNull(res.get(resA).get("permissions"), "EDIT-only → permissions null");
        assertEquals(true, res.get(resB).get("canAdmin"));
        assertFalse(((List<?>) res.get(resB).get("permissions")).isEmpty(), "ADMIN → rows visible");

        // reservations(filter:) is resource-first for non-admins and the seeded events carry no
        // allocatables, so the by-id read (canRead-gated, unreadable → null) is used here.
        String sel = "\") { id canAdmin permissions { level } } }";
        Map<String, Object> a = tester.document("{ reservation(id: \"" + evA + sel).execute().path("reservation").entity(ROW).get();
        Map<String, Object> b = tester.document("{ reservation(id: \"" + evB + sel).execute().path("reservation").entity(ROW).get();
        assertEquals(false, a.get("canAdmin"));
        assertNull(a.get("permissions"), "EDIT-only → permissions null");
        assertEquals(true, b.get("canAdmin"));
        assertFalse(((List<?>) b.get("permissions")).isEmpty(), "ADMIN → rows visible");
        tester.document("{ reservation(id: \"" + evC + sel).execute().path("reservation").valueIsNull();
    }

    // === 2 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void nonAdminNeverGetsEmptyListInsteadOfNull()
    {
        String body = raw("{ resources(filter: { idIn: " + ids(resA) + " }) { id permissions { level } } }");
        assertTrue(body.contains("\"permissions\":null"), body);
        assertFalse(body.contains("\"permissions\":[]"), body);
        assertFalse(body.contains("\"errors\""), body);
    }

    // === 3 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void mixedIdsByteIdentical()
    {
        String unknown = UUID.randomUUID().toString();
        String sel = " }) { id canAdmin permissions { level principal { everyone } } } }";
        assertEquals(raw("{ resources(filter: { idIn: " + ids(resA, resB) + sel),
                raw("{ resources(filter: { idIn: " + ids(resA, resB, resC, unknown) + sel));
        assertEquals(raw("{ resources(filter: { idIn: " + ids(unknown) + sel),
                raw("{ resources(filter: { idIn: " + ids(resC, unknown) + sel));

        String tpl = "\") { id name canAdmin canModify fixedTimeAndDuration permissions { level } } }";
        String readable = raw("{ eventTemplate(id: \"" + tplRead + tpl);
        assertTrue(readable.contains(tplRead), readable);
        assertEquals(raw("{ eventTemplate(id: \"" + unknown + tpl), raw("{ eventTemplate(id: \"" + tplPriv + tpl));

        List<String> listed = rows("{ eventTemplates { id } }", "eventTemplates").stream()
                .map(m -> (String) m.get("id")).toList();
        assertTrue(listed.contains(tplRead));
        assertFalse(listed.contains(tplPriv), "§12 — unreadable template omitted");
    }

    // === 4 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void adminSeesEveryPrincipalNameOnAdministeredEntity()
    {
        String body = raw("{ resources(filter: { idIn: " + ids(resB)
                + " }) { permissions { principal { user { id username } } } } }");
        assertTrue(body.contains("\"username\":\"lenny\""), "OQ 3 — entity admin sees every principal: " + body);

        String users = raw("{ users { id username } }");
        assertFalse(users.contains(lennyId), "users() must still hide lenny from monty: " + users);
        String search = raw("{ search(query: \"lenny\", kinds: [USER]) { groups { hits { id } } } }");
        assertFalse(search.contains(lennyId), "search must still hide lenny from monty: " + search);
    }

    // === R1b — principal exposes id + username + name only (OQ 3), no User/Group detail fields ===

    @Test
    @WithMockUser(username = "monty")
    void principalCarriesNoUserOrGroupDetailFields()
    {
        tester.document("{ resources(filter: { idIn: " + ids(resB) + " }) { permissions { principal { user { email } } } } }")
                .execute().errors().satisfy(errs -> assertTrue(
                        errs.stream().anyMatch(e -> String.valueOf(e.getMessage()).contains("FieldUndefined")),
                        () -> "user { email } must fail validation, got: " + errs));
        tester.document("{ resources(filter: { idIn: " + ids(resB) + " }) { permissions { principal { group { key } } } } }")
                .execute().errors().satisfy(errs -> assertTrue(
                        errs.stream().anyMatch(e -> String.valueOf(e.getMessage()).contains("FieldUndefined")),
                        () -> "group { key } must fail validation, got: " + errs));
    }

    // === 5 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void dynamicTypeListsNullForNonAdmin()
    {
        for (Map<String, Object> t : rows("{ types { key typeAccess { level } instanceDefaults { level } } }", "types"))
        {
            assertNull(t.get("typeAccess"), "non-admin typeAccess must be null: " + t);
            assertNull(t.get("instanceDefaults"), "non-admin instanceDefaults must be null: " + t);
        }
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    @SuppressWarnings("unchecked")
    void dynamicTypeListsSplitForAdmin()
    {
        Map<String, Object> room = rows("{ types { key typeAccess { level } instanceDefaults { level } } }", "types")
                .stream().filter(t -> "room".equals(t.get("key"))).findFirst().orElseThrow();
        List<String> typeAccess = ((List<Map<String, Object>>) room.get("typeAccess")).stream()
                .map(m -> (String) m.get("level")).toList();
        List<String> defaults = ((List<Map<String, Object>>) room.get("instanceDefaults")).stream()
                .map(m -> (String) m.get("level")).toList();
        assertTrue(Set.of("READ_TYPE", "CREATE").containsAll(typeAccess), "typeAccess: " + typeAccess);
        assertFalse(defaults.contains("READ_TYPE") || defaults.contains("CREATE"), "instanceDefaults: " + defaults);
        assertTrue(defaults.contains("ADMIN"), "ADMIN row belongs to instanceDefaults: " + defaults);
        int stored = operator.getDynamicType("room").getPermissionList().size();
        assertEquals(stored, typeAccess.size() + defaults.size(), "every stored row lands in exactly one list");
    }

    // === 6 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    @SuppressWarnings("unchecked")
    void everyoneRowReadsAsEveryoneTrue()
    {
        Map<String, Object> b = rows("{ resources(filter: { idIn: " + ids(resB)
                + " }) { id permissions { level principal { user { id } group { id } everyone } } } }", "resources").get(0);
        List<Map<String, Object>> principals = ((List<Map<String, Object>>) b.get("permissions")).stream()
                .map(p -> (Map<String, Object>) p.get("principal")).toList();
        Map<String, Object> everyone = principals.stream().filter(p -> Boolean.TRUE.equals(p.get("everyone")))
                .findFirst().orElseThrow(() -> new AssertionError("no everyone row: " + principals));
        assertNull(everyone.get("user"));
        assertNull(everyone.get("group"));
        assertEquals(1, principals.stream().filter(p -> Boolean.TRUE.equals(p.get("everyone"))).count());
    }

    // === R1a — window fields pass through ====================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    @SuppressWarnings("unchecked")
    void windowFieldsPassThrough()
    {
        Map<String, Map<String, Object>> byLevel = rows("{ resources(filter: { idIn: " + ids(resD)
                + " }) { permissions { level start end minAdvance maxAdvance } } }", "resources").get(0)
                .entrySet().stream().filter(e -> e.getKey().equals("permissions"))
                .flatMap(e -> ((List<?>) e.getValue()).stream())
                .map(o -> (Map<String, Object>) (Map<?, ?>) o)
                .collect(Collectors.toMap(m -> (String) m.get("level"), m -> m));
        Map<String, Object> abs = byLevel.get("ALLOCATE");
        assertEquals(LocalDateTime.of(2026, 3, 1, 8, 0), LocalDateTime.parse((String) abs.get("start")));
        assertEquals(LocalDateTime.of(2026, 7, 31, 18, 30), LocalDateTime.parse((String) abs.get("end")));
        assertNull(abs.get("minAdvance"));
        assertNull(abs.get("maxAdvance"));
        Map<String, Object> rel = byLevel.get("REQUEST");
        assertEquals(2, rel.get("minAdvance"));
        assertEquals(30, rel.get("maxAdvance"));
        assertNull(rel.get("start"));
        assertNull(rel.get("end"));
    }

    // === 7 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void periodIdAndCategoriesRoundTrip()
    {
        Map<String, Object> p = rows("{ periods { id name categories { id } canAdmin permissions { level } } }", "periods")
                .stream().filter(m -> periodId.equals(m.get("id"))).findFirst()
                .orElseThrow(() -> new AssertionError("seeded period not listed by id"));
        assertEquals("PRD113-PERIOD", p.get("name"));
        Allocatable stored = operator.tryResolve(new org.rapla.entities.storage.ReferenceInfo<>(periodId, Allocatable.class));
        assertNotNull(stored);
        assertEquals(stored.getClassification().getValues(stored.getClassification().getAttribute("category")).size(),
                ((List<?>) p.get("categories")).size());
        assertEquals(true, p.get("canAdmin"));
        assertNotNull(p.get("permissions"));
    }

    @Test
    @WithMockUser(username = "monty")
    void periodPermissionsNullForNonAdmin()
    {
        Map<String, Object> p = rows("{ periods { id canAdmin permissions { level } } }", "periods")
                .stream().filter(m -> periodId.equals(m.get("id"))).findFirst().orElseThrow();
        assertEquals(false, p.get("canAdmin"));
        assertNull(p.get("permissions"));
    }
}
