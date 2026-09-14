package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
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
 * PRD 113 § 5c (W1) — tier-3 for permission lists on {@code AllocatableInput} / {@code ReservationInput},
 * the {@code ownerId} removal and the two admin-only owner verbs. The gate itself is
 * {@code WriteGate → SecurityManager.checkModifyPermissions}; these tests pin it end to end over the wire.
 *
 * <p>Fixture (testdefault.xml): homer (admin), monty (non-admin, member of my-group). Every test seeds its
 * own entities so the shared per-class store never couples test order.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class PermissionWriteGraphQLTest
{
    /** Appointments carry client-minted ids (PRD 056 § 9) — fresh per call. */
    private static String appointment()
    {
        return "appointments: [{ id: \"" + UUID.randomUUID() + "\", start: \"2026-06-01T10:00:00\","
                + " end: \"2026-06-01T11:00:00\", allDay: false }], allocations: []";
    }

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PermissionWriteGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
    User homer;
    User monty;
    Category myGroup;

    @BeforeEach
    void setUp() throws Exception
    {
        client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
        homer = operator.getUser("homer");
        monty = operator.getUser("monty");
        myGroup = operator.getSuperCategory().getCategory("user-groups").getCategory("my-group");
    }

    // === seeding =============================================================

    private record RowSpec(AccessLevel level, User user, Category group) {}

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
                Permission p = container.newPermission();
                p.setAccessLevel(r.level());
                if (r.user() != null) p.setUser(r.user());
                if (r.group() != null) p.setGroup(r.group());
                container.addPermission(p);
            }
        }
        facade.storeObjects(new Entity[] { entity });
        return entity.getId();
    }

    private Allocatable named(DynamicType type, String name) throws Exception
    {
        return named(type, name, homer);
    }

    private Allocatable named(DynamicType type, String name, User owner) throws Exception
    {
        Classification c = type.newClassification();
        c.setValue("name", name);
        return facade.newAllocatable(c, owner);
    }

    /** O1 — users around monty's group-admin scope (powerplant): smithers/carl inside, lenny outside. */
    private User seededUser(String username, String powerplantChild) throws Exception
    {
        User existing = operator.getUser(username);
        if (existing != null) return existing;
        User u = facade.newUser();
        u.setUsername(username);
        if (powerplantChild != null)
        {
            u.addGroup(operator.getSuperCategory().getCategory("user-groups").getCategory("powerplant").getCategory(powerplantChild));
        }
        facade.store(u);
        return operator.getUser(username);
    }

    private String room(List<RowSpec> rows) throws Exception
    {
        return store(named(operator.getDynamicType("room"), "W1-" + UUID.randomUUID()), rows);
    }

    private String event(List<RowSpec> rows) throws Exception
    {
        DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Reservation r = facade.newReservation(eventType.newClassification(), homer);
        Appointment app = facade.newAppointmentWithUser(LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), homer);
        r.addAppointment(app);
        return store(r, rows);
    }

    private List<String> levels(String id, Class<? extends Entity> type)
    {
        PermissionContainer c = (PermissionContainer) operator.tryResolve(new ReferenceInfo<>(id, type));
        List<String> out = new ArrayList<>();
        c.getPermissionList().forEach(p -> out.add(p.getAccessLevel().name()));
        return out;
    }

    private String ownerOf(String id, Class<? extends Entity> type)
    {
        PermissionContainer c = (PermissionContainer) operator.tryResolve(new ReferenceInfo<>(id, type));
        return c.getOwnerRef() == null ? null : c.getOwnerRef().getId();
    }

    private static void runAs(String username, boolean admin)
    {
        List<SimpleGrantedAuthority> roles = admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of();
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new org.springframework.security.core.userdetails.User(username, "x", roles), null, roles));
    }

    // === wire helpers ========================================================

    private String updateRoom(String id, String permissions)
    {
        return "mutation { updateResource(id: \"" + id + "\", input: { typeKey: \"room\", classification: { room: {} }"
                + (permissions == null ? "" : ", permissions: " + permissions) + " }) { id } }";
    }

    private String updateEvent(String id, String permissions)
    {
        return "mutation { updateReservation(id: \"" + id + "\", input: { typeKey: \"event\", classification: { event: {} }, "
                + appointment() + (permissions == null ? "" : ", permissions: " + permissions) + " }) { id } }";
    }

    private void ok(String document)
    {
        tester.document(document).execute().errors().verify();
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

    private String raw(String document, Map<String, Object> variables)
    {
        return client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", document, "variables", variables))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    private String group(String level)
    {
        return "[{ principal: { groupId: \"" + myGroup.getId() + "\" }, level: " + level + " }]";
    }

    // === 1 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void editOnlyNullLeavesListUntouched() throws Exception
    {
        String a = room(List.of(row(AccessLevel.EDIT, null, myGroup)));
        ok(updateRoom(a, null));
        assertEquals(List.of("EDIT"), levels(a, Allocatable.class));
    }

    // === 2 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void editOnlyEmptyListDenied() throws Exception
    {
        String a = room(List.of(row(AccessLevel.EDIT, null, myGroup)));
        fails(updateRoom(a, "[]"), "PERMISSION_DENIED", null);
        assertEquals(List.of("EDIT"), levels(a, Allocatable.class));
    }

    // === 3 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    void editOnlySelfGrantAdminDenied() throws Exception
    {
        String a = room(List.of(row(AccessLevel.EDIT, null, myGroup)));
        fails(updateRoom(a, group("ADMIN")), "PERMISSION_DENIED", null);
        assertEquals(List.of("EDIT"), levels(a, Allocatable.class));

        String ev = event(List.of(row(AccessLevel.EDIT, null, myGroup)));
        fails(updateEvent(ev, group("ADMIN")), "PERMISSION_DENIED", null);
        assertEquals(List.of("EDIT"), levels(ev, Reservation.class));
    }

    // === 4 ===================================================================

    @Test
    @WithMockUser(username = "monty")
    @SuppressWarnings("unchecked")
    void entityAdminReplacesWholeList() throws Exception
    {
        String b = room(List.of(row(AccessLevel.ADMIN, monty, null), row(AccessLevel.EDIT, null, myGroup)));
        ok(updateRoom(b, "[{ principal: { userId: \"" + monty.getId() + "\" }, level: ADMIN },"
                + " { principal: { everyone: true }, level: READ }]"));
        assertEquals(List.of("ADMIN", "READ"), levels(b, Allocatable.class));

        List<Map<String, Object>> read = (List<Map<String, Object>>) tester.document(
                "{ resources(filter: { idIn: [\"" + b + "\"] }) { permissions { level principal { user { id } everyone } } } }")
                .execute().path("resources[0].permissions").entity(List.class).get();
        assertEquals("ADMIN", read.get(0).get("level"));
        assertEquals(monty.getId(), ((Map<String, Object>) ((Map<String, Object>) read.get(0).get("principal")).get("user")).get("id"));
        assertEquals("READ", read.get(1).get("level"));
        assertEquals(true, ((Map<String, Object>) read.get(1).get("principal")).get("everyone"));
    }

    // === 5 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void globalAdminBypass() throws Exception
    {
        String a = room(List.of(row(AccessLevel.EDIT, null, myGroup)));
        ok(updateRoom(a, group("READ")));
        assertEquals(List.of("READ"), levels(a, Allocatable.class));
    }

    // === 6 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createWithPermissionsOverridesTypeDefaults()
    {
        String id = UUID.randomUUID().toString();
        ok("mutation { createResource(input: { id: \"" + id + "\", typeKey: \"room\", classification: { room: {} },"
                + " permissions: " + group("READ") + " }) { id } }");
        assertEquals(List.of("READ"), levels(id, Allocatable.class));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createWithNullKeepsTypeDefaults()
    {
        String id = UUID.randomUUID().toString();
        ok("mutation { createResource(input: { id: \"" + id + "\", typeKey: \"room\", classification: { room: {} } }) { id } }");
        List<String> expected = new ArrayList<>();
        operator.getDynamicType("room").getPermissionList().forEach(p -> {
            if (p.getAccessLevel() != AccessLevel.READ_TYPE && p.getAccessLevel() != AccessLevel.CREATE)
                expected.add(p.getAccessLevel().name());
        });
        assertEquals(expected, levels(id, Allocatable.class));
    }

    // === 7 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void windowOnReadRowRejected() throws Exception
    {
        fails(updateRoom(room(List.of()), "[{ principal: { everyone: true }, level: READ, start: \"2026-01-01T00:00:00\" }]"),
                "INVALID_VALUE", "input.permissions[0].level");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void windowOnRequestRowAccepted() throws Exception
    {
        String a = room(List.of());
        ok(updateRoom(a, "[{ principal: { everyone: true }, level: REQUEST, minAdvance: 1, maxAdvance: 14 }]"));
        Permission p = ((PermissionContainer) operator.tryResolve(new ReferenceInfo<>(a, Allocatable.class)))
                .getPermissionList().iterator().next();
        assertEquals(1, p.getMinAdvance());
        assertEquals(14, p.getMaxAdvance());
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void absoluteAndRelativeRejected() throws Exception
    {
        fails(updateRoom(room(List.of()), "[{ principal: { everyone: true }, level: ALLOCATE,"
                + " start: \"2026-01-01T00:00:00\", minAdvance: 1 }]"), "INVALID_VALUE", "input.permissions[0].start");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void startAfterEndRejected() throws Exception
    {
        fails(updateRoom(room(List.of()), "[{ principal: { everyone: true }, level: EDIT,"
                + " start: \"2026-02-01T00:00:00\", end: \"2026-01-01T00:00:00\" }]"), "INVALID_VALUE", "input.permissions[0].end");
    }

    // === 8 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void everyoneFalseRejected() throws Exception
    {
        fails(updateRoom(room(List.of()), "[{ principal: { everyone: false }, level: READ }]"),
                "INVALID_VALUE", "input.permissions[0].principal.everyone");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void groupOutsideUserGroupsAnswersLikeUnknown() throws Exception
    {
        String a = room(List.of());
        String outside = null;
        for (Category c : operator.getSuperCategory().getCategories())
        {
            if (!"user-groups".equals(c.getKey())) { outside = c.getId(); break; }
        }
        assertTrue(outside != null, "fixture must have a category outside user-groups");
        String doc = "mutation($g: ID!) { updateResource(id: \"" + a + "\", input: { typeKey: \"room\","
                + " classification: { room: {} }, permissions: [{ principal: { groupId: $g }, level: READ }] }) { id } }";
        String unknown = raw(doc, Map.of("g", UUID.randomUUID().toString()));
        assertTrue(unknown.contains("REFERENCE_NOT_FOUND"), unknown);
        assertEquals(unknown, raw(doc, Map.of("g", outside)));
    }

    // === 9 ===================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unrepresentableLevelFailsValidation() throws Exception
    {
        failsValidation(updateRoom(room(List.of()), "[{ principal: { everyone: true }, level: READ_TYPE }]"));
        failsValidation(updateEvent(event(List.of()), "[{ principal: { everyone: true }, level: REQUEST }]"));
    }

    // === 10 ==================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void ownerIdNoLongerAField()
    {
        failsValidation("mutation { createResource(input: { id: \"" + UUID.randomUUID() + "\", typeKey: \"room\","
                + " classification: { room: {} }, ownerId: \"" + monty.getId() + "\" }) { id } }");
        failsValidation("mutation { createReservation(input: { id: \"" + UUID.randomUUID() + "\", typeKey: \"event\","
                + " classification: { event: {} }, " + appointment() + ", ownerId: \"" + monty.getId() + "\" }) { id } }");
    }

    // === 11 ==================================================================

    @Test
    @WithMockUser(username = "monty")
    void changeAllocatableOwnerDeniedForNonAdmin() throws Exception
    {
        String b = room(List.of(row(AccessLevel.ADMIN, monty, null)));
        fails("mutation { changeResourceOwner(ids: [\"" + b + "\"], newOwnerId: \"" + monty.getId() + "\") { overallStatus } }",
                "PERMISSION_DENIED", "ids[0]");
        assertEquals(homer.getId(), ownerOf(b, Allocatable.class));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void changeAllocatableOwnerForResourceTemplateAndPeriod() throws Exception
    {
        String resource = room(List.of());
        String template = store(named(operator.getDynamicType(StorageOperator.RAPLA_TEMPLATE), "W1-TPL"), null);
        DynamicType periodType = operator.getDynamicType(StorageOperator.PERIOD_TYPE);
        Classification pc = periodType.newClassification();
        pc.setValue("name", "W1-PERIOD");
        pc.setValue("start", LocalDateTime.of(2026, 3, 1, 0, 0));
        pc.setValue("end", LocalDateTime.of(2026, 7, 31, 0, 0));
        String period = store(facade.newAllocatable(pc, homer), null);

        ok("mutation { changeResourceOwner(ids: [\"" + resource + "\", \"" + template + "\", \"" + period
                + "\"], newOwnerId: \"" + monty.getId() + "\") { overallStatus } }");
        assertEquals(monty.getId(), ownerOf(resource, Allocatable.class));
        assertEquals(monty.getId(), ownerOf(template, Allocatable.class));
        assertEquals(monty.getId(), ownerOf(period, Allocatable.class));

        fails("mutation { changeResourceOwner(ids: [\"" + resource + "\", \"" + UUID.randomUUID()
                + "\"], newOwnerId: \"" + homer.getId() + "\") { overallStatus } }", "REFERENCE_NOT_FOUND", "ids[1]");
        fails("mutation { changeResourceOwner(ids: [\"" + resource + "\"], newOwnerId: \"" + UUID.randomUUID()
                + "\") { overallStatus } }", "REFERENCE_NOT_FOUND", "newOwnerId");
        assertEquals(monty.getId(), ownerOf(resource, Allocatable.class), "a failed call changes nothing");
    }

    // === 11a =================================================================

    @Test
    @WithMockUser(username = "monty")
    void changeReservationOwnerAdminOnly() throws Exception
    {
        String ev = event(List.of(row(AccessLevel.EDIT, null, myGroup)));
        assertTrue(operator.getPermissionController().canModify(
                operator.tryResolve(new ReferenceInfo<>(ev, Reservation.class)), monty), "precondition: monty can modify");
        String change = "mutation { changeReservationOwner(ids: [\"" + ev + "\"], newOwnerId: \"" + monty.getId()
                + "\") { overallStatus } }";
        fails(change, "PERMISSION_DENIED", "ids[0]");
        assertEquals(homer.getId(), ownerOf(ev, Reservation.class));

        runAs("homer", true);
        ok(change);
        assertEquals(monty.getId(), ownerOf(ev, Reservation.class));
    }

    // === O1 — owner change by group admins ===================================

    private String ownedRoom(User owner, boolean readableByMonty) throws Exception
    {
        return store(named(operator.getDynamicType("room"), "O1-" + UUID.randomUUID(), owner),
                readableByMonty ? List.of(row(AccessLevel.READ, null, myGroup)) : List.of());
    }

    private String ownedEvent(User owner) throws Exception
    {
        DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Reservation r = facade.newReservation(eventType.newClassification(), owner);
        r.addAppointment(facade.newAppointmentWithUser(LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), owner));
        return store(r, List.of(row(AccessLevel.READ, null, myGroup)));
    }

    private static String changeOwner(String verb, String id, String newOwnerId)
    {
        return "mutation { " + verb + "(ids: [\"" + id + "\"], newOwnerId: \"" + newOwnerId + "\") { overallStatus } }";
    }

    @Test
    @WithMockUser(username = "monty")
    void groupAdminChangesOwnerWithinScope() throws Exception
    {
        User smithers = seededUser("smithers", "powerplant-staff");
        User carl = seededUser("carl", "powerplant-staff");
        String room = ownedRoom(smithers, true);
        ok(changeOwner("changeResourceOwner", room, carl.getId()));
        assertEquals(carl.getId(), ownerOf(room, Allocatable.class));

        String ev = ownedEvent(smithers);
        ok(changeOwner("changeReservationOwner", ev, carl.getId()));
        assertEquals(carl.getId(), ownerOf(ev, Reservation.class));
    }

    @Test
    @WithMockUser(username = "monty")
    void newOwnerOutsideScopeAnswersLikeUnknownUser() throws Exception
    {
        User smithers = seededUser("smithers", "powerplant-staff");
        User lenny = seededUser("lenny", null);
        for (String verb : List.of("changeResourceOwner", "changeReservationOwner"))
        {
            String id = verb.equals("changeResourceOwner") ? ownedRoom(smithers, true) : ownedEvent(smithers);
            String doc = "mutation($o: ID!) { " + verb + "(ids: [\"" + id + "\"], newOwnerId: $o) { overallStatus } }";
            String unknown = raw(doc, Map.of("o", UUID.randomUUID().toString()));
            assertTrue(unknown.contains("REFERENCE_NOT_FOUND") && unknown.contains("newOwnerId"), unknown);
            assertEquals(unknown, raw(doc, Map.of("o", lenny.getId())), "§12 — an out-of-scope user must not be distinguishable");
        }
        assertEquals(smithers.getId(), "" + smithers.getId());
    }

    @Test
    @WithMockUser(username = "monty")
    void oldOwnerOutsideScopeDenied() throws Exception
    {
        User carl = seededUser("carl", "powerplant-staff");
        User lenny = seededUser("lenny", null);
        String room = ownedRoom(lenny, true);
        fails(changeOwner("changeResourceOwner", room, carl.getId()), "PERMISSION_DENIED", "ids[0]");
        assertEquals(lenny.getId(), ownerOf(room, Allocatable.class));
    }

    @Test
    @WithMockUser(username = "carl")
    void callerWithoutAdminGroupDenied() throws Exception
    {
        User smithers = seededUser("smithers", "powerplant-staff");
        seededUser("carl", "powerplant-staff");
        String room = ownedRoom(smithers, true);
        fails(changeOwner("changeResourceOwner", room, smithers.getId()), "PERMISSION_DENIED", "ids[0]");
    }

    // === 12 ==================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void applyChangesCarriesPermissions() throws Exception
    {
        String ev = event(List.of());
        String batch = "mutation { applyChanges(operations: [{ updateReservation: { id: \"" + ev + "\", input: {"
                + " typeKey: \"event\", classification: { event: {} }, " + appointment() + ", permissions: %s } } }]) { overallStatus } }";
        fails(batch.formatted("[{ principal: { everyone: false }, level: READ }]"),
                "INVALID_VALUE", "operations[0].updateReservation.permissions[0].principal.everyone");
        assertEquals(List.of(), levels(ev, Reservation.class));

        ok(batch.formatted(group("EDIT")));
        assertEquals(List.of("EDIT"), levels(ev, Reservation.class));
    }

    // === 13 ==================================================================

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void concurrentModification() throws Exception
    {
        String a = room(List.of(row(AccessLevel.EDIT, null, myGroup)));
        fails("mutation { updateResource(id: \"" + a + "\", expectedLastChanged: \"2000-01-01T00:00:00\", input: {"
                + " typeKey: \"room\", classification: { room: {} }, permissions: " + group("READ") + " }) { id } }",
                "CONCURRENT_MODIFICATION", null);
        assertEquals(List.of("EDIT"), levels(a, Allocatable.class));
    }

    // === W2 7 — updateEventTemplate ==========================================

    private String template(List<RowSpec> rows) throws Exception
    {
        return store(named(operator.getDynamicType(StorageOperator.RAPLA_TEMPLATE), "W2-TPL-" + UUID.randomUUID()), rows);
    }

    private static String updateTemplate(String id, String extra)
    {
        return "mutation { updateEventTemplate(id: \"" + id + "\", input: { name: \"W2-RENAMED\", fixedTimeAndDuration: true"
                + (extra == null ? "" : ", " + extra) + " }) { id name fixedTimeAndDuration permissions { level } } }";
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateEventTemplateChangesDataAndPermissions() throws Exception
    {
        String t = template(List.of());
        Map<String, Object> out = tester.document(updateTemplate(t, "permissions: " + group("READ"))).execute()
                .path("updateEventTemplate").entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}).get();
        assertEquals("W2-RENAMED", out.get("name"));
        assertEquals(true, out.get("fixedTimeAndDuration"));
        assertEquals(List.of("READ"), levels(t, Allocatable.class));

        fails(updateTemplate(t, "id: \"" + UUID.randomUUID() + "\""), "INVALID_VALUE", "input.id");
    }

    @Test
    @WithMockUser(username = "monty")
    void updateEventTemplateGates() throws Exception
    {
        String readable = template(List.of(row(AccessLevel.READ, null, myGroup)));
        fails(updateTemplate(readable, null), "PERMISSION_DENIED", "id");

        String hidden = template(List.of());
        String doc = "mutation($id: ID!) { updateEventTemplate(id: $id, input: { name: \"x\", fixedTimeAndDuration: false }) { id } }";
        String unknown = raw(doc, Map.of("id", UUID.randomUUID().toString()));
        assertTrue(unknown.contains("REFERENCE_NOT_FOUND"), unknown);
        assertEquals(unknown, raw(doc, Map.of("id", hidden)), "unreadable answers like unknown (§12)");
        assertEquals(unknown, raw(doc, Map.of("id", room(List.of(row(AccessLevel.ADMIN, monty, null))))),
                "a non-template resource answers like unknown");
    }

    // === W2 8 — updatePeriod =================================================

    private String period() throws Exception
    {
        DynamicType periodType = operator.getDynamicType(StorageOperator.PERIOD_TYPE);
        Classification pc = periodType.newClassification();
        pc.setValue("name", "W2-PERIOD");
        pc.setValue("start", LocalDateTime.of(2026, 3, 1, 0, 0));
        pc.setValue("end", LocalDateTime.of(2026, 7, 31, 0, 0));
        return store(facade.newAllocatable(pc, homer), List.of());
    }

    private String someCategory()
    {
        for (Category c : operator.getSuperCategory().getCategories())
        {
            if (!"user-groups".equals(c.getKey())) return c.getId();
        }
        throw new AssertionError("fixture must have a category outside user-groups");
    }

    private static String updatePeriod(String id, String start, String end, String categoryId, String extra)
    {
        return "mutation { updatePeriod(id: \"" + id + "\"" + (extra == null ? "" : ", " + extra) + ", input: { name: \"W2-SEMESTER\","
                + " start: \"" + start + "\", end: \"" + end + "\", categoryIds: [\"" + categoryId + "\"],"
                + " permissions: [{ principal: { everyone: true }, level: READ }] }) { id name start end categories { id } permissions { level } } }";
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    @SuppressWarnings("unchecked")
    void updatePeriodRoundTrip() throws Exception
    {
        String p = period();
        String category = someCategory();
        Map<String, Object> out = tester.document(updatePeriod(p, "2026-10-01T00:00:00", "2027-03-31T00:00:00", category, null))
                .execute().path("updatePeriod").entity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}).get();
        assertEquals(p, out.get("id"));
        assertEquals("W2-SEMESTER", out.get("name"));
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.parse((String) out.get("start")));
        assertEquals(category, ((List<Map<String, Object>>) out.get("categories")).get(0).get("id"));
        assertEquals(List.of("READ"), levels(p, Allocatable.class));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updatePeriodValidation() throws Exception
    {
        String p = period();
        fails(updatePeriod(p, "2026-10-01T00:00:00", "2027-03-31T00:00:00", UUID.randomUUID().toString(), null),
                "REFERENCE_NOT_FOUND", "input.categoryIds[0]");
        fails(updatePeriod(p, "2026-10-01T00:00:00", "2026-10-01T00:00:00", someCategory(), null),
                "INVALID_VALUE", "input.end");
        fails(updatePeriod(p, "2026-10-01T00:00:00", "2027-03-31T00:00:00", someCategory(), "expectedLastChanged: \"2000-01-01T00:00:00\""),
                "CONCURRENT_MODIFICATION", null);
    }

    // === W1a — reservationChecks never applies a draft's permissions (§ 1d) ===

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationChecksIgnoresDraftPermissions()
    {
        String id = UUID.randomUUID().toString();
        ok("{ reservationChecks(input: { draft: { id: \"" + id + "\", typeKey: \"event\", classification: { event: {} }, "
                + appointment() + ", permissions: [{ principal: { everyone: false }, level: READ }] } }) { code } }");
        assertNull(operator.tryResolve(new ReferenceInfo<>(id, Reservation.class)), "a check never stores its draft");
    }

    // === 14 ==================================================================

    @Test
    @WithMockUser(username = "monty")
    void permissionIndexFresh() throws Exception
    {
        String c = room(List.of());
        String query = "{ resources(filter: { idIn: [\"" + c + "\"] }) { id } }";
        assertFalse(raw(query, Map.of()).contains(c), "precondition: monty cannot read C");

        runAs("homer", true);
        ok(updateRoom(c, "[{ principal: { userId: \"" + monty.getId() + "\" }, level: READ }]"));

        runAs("monty", false);
        assertTrue(raw(query, Map.of()).contains(c), "the grant must be visible on the next read");
    }
}
