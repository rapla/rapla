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
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.SyncStorageOperator;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 128 D5/D6 — tier-3 tests for {@code conflicts(filter:)} and {@code conflictStats}, incl. the §12 leak checks.
 *
 * <p>Fixture: monty (non-admin, my-group). Room R1 readable by my-group, room R2 without rows (hidden from monty).
 * Three events at the same time: MINE (owner monty, R1+R2), HIDDEN (owner homer, no rows, R1+R2) and READ
 * (owner homer, READ for my-group, R1). Conflicts: R1 MINE×HIDDEN, R1 MINE×READ, R1 HIDDEN×READ, R2 MINE×HIDDEN.
 * monty may modify the first two (R2 is unreadable, HIDDEN×READ has no side he may modify); R1 MINE×READ is
 * disabled by homer.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ConflictAndRequestGraphQLTest
{
    private static final ParameterizedTypeReference<Map<String, Object>> ROW = new ParameterizedTypeReference<>() {};
    private static final String HIDDEN_NAME = "PRD128-HIDDEN-EVENT";
    private static final String FIELDS = "id resource { id } reservation1Id appointment1Id reservation2Id appointment2Id "
            + "reservation1 { id } reservation2 { id } appointment2 { id } description disabled";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    static boolean seeded;
    static String r1, r2, evMine, evHidden, evRead, mineHiddenR1, mineReadR1, hiddenReadR1, mineHiddenR2;
    static String ra, rb, evReq, evReq2, evReqApp;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ConflictAndRequestGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

        Allocatable room1 = room("PRD128-R1", homer, myGroup);
        Allocatable room2 = room("PRD128-R2", homer, null);
        facade.storeObjects(new Entity[] { room1, room2 });
        r1 = room1.getId();
        r2 = room2.getId();

        Reservation mine = event("PRD128-MINE", monty, null, room1, room2);
        Reservation hidden = event(HIDDEN_NAME, homer, null, room1, room2);
        Reservation read = event("PRD128-READ", homer, myGroup, room1);
        facade.storeObjects(new Entity[] { mine, hidden, read });
        evMine = mine.getId();
        evHidden = hidden.getId();
        evRead = read.getId();

        Appointment aMine = mine.getAppointments()[0];
        Appointment aHidden = hidden.getAppointments()[0];
        Appointment aRead = read.getAppointments()[0];
        mineHiddenR1 = ConflictImpl.createId(room1.getReference(), aMine.getReference(), aHidden.getReference());
        mineReadR1 = ConflictImpl.createId(room1.getReference(), aMine.getReference(), aRead.getReference());
        hiddenReadR1 = ConflictImpl.createId(room1.getReference(), aHidden.getReference(), aRead.getReference());
        mineHiddenR2 = ConflictImpl.createId(room2.getReference(), aMine.getReference(), aHidden.getReference());

        for (Conflict c : ((SyncStorageOperator) operator).getConflictsSync(homer))
        {
            if (c.getId().equals(mineReadR1))
            {
                ConflictImpl disabled = (ConflictImpl) c;
                disabled.setAppointment1Enabled(false);
                disabled.setAppointment2Enabled(false);
                facade.storeObjects(new Entity[] { disabled });
            }
        }
        seedRequests(homer, monty, myGroup);
        seeded = true;
    }

    /**
     * Requests: lenny (non-admin) may edit RA (which has a REQUEST row for my-group) and cannot see RB. REQ (owner
     * monty) requests RA and RB, REQ2 requests RA, PLAIN is on RA without a request. One week apart, no conflicts.
     */
    private void seedRequests(User homer, User monty, Category myGroup) throws Exception
    {
        User lenny = facade.newUser();
        lenny.setUsername("lenny");
        lenny.setName("Lenny");
        facade.store(lenny);
        lenny = operator.getUser("lenny");

        Allocatable roomA = room("PRD128-RA", homer, null);
        Permission edit = roomA.newPermission();
        edit.setAccessLevel(AccessLevel.EDIT);
        edit.setUser(lenny);
        roomA.addPermission(edit);
        Permission request = roomA.newPermission();
        request.setAccessLevel(AccessLevel.REQUEST);
        request.setGroup(myGroup);
        roomA.addPermission(request);
        Allocatable roomB = room("PRD128-RB", homer, null);
        facade.storeObjects(new Entity[] { roomA, roomB });
        ra = roomA.getId();
        rb = roomB.getId();

        Reservation req = eventAt(LocalDateTime.of(2027, 7, 1, 10, 0), "PRD128-REQ", monty, null, roomA, roomB);
        req.setRequestStatus(roomA, org.rapla.entities.domain.RequestStatus.REQUESTED);
        req.setRequestStatus(roomB, org.rapla.entities.domain.RequestStatus.REQUESTED);
        Reservation req2 = eventAt(LocalDateTime.of(2027, 7, 8, 10, 0), "PRD128-REQ2", monty, null, roomA);
        req2.setRequestStatus(roomA, org.rapla.entities.domain.RequestStatus.REQUESTED);
        Reservation plain = eventAt(LocalDateTime.of(2027, 7, 15, 10, 0), "PRD128-PLAIN", monty, null, roomA);
        facade.storeObjects(new Entity[] { req, req2, plain });
        evReq = req.getId();
        evReq2 = req2.getId();
        evReqApp = req.getAppointments()[0].getId();
    }

    private Allocatable room(String name, User owner, Category readers) throws Exception
    {
        Classification c = operator.getDynamicType("room").newClassification();
        c.setValue("name", name);
        Allocatable a = facade.newAllocatable(c, owner);
        replacePermissions(a, readers);
        return a;
    }

    private Reservation event(String name, User owner, Category readers, Allocatable... resources) throws Exception
    {
        return eventAt(LocalDateTime.of(2027, 6, 1, 10, 0), name, owner, readers, resources);
    }

    private Reservation eventAt(LocalDateTime start, String name, User owner, Category readers, Allocatable... resources) throws Exception
    {
        DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Classification c = eventType.newClassification();
        c.setValue("name", name);
        Reservation r = facade.newReservation(c, owner);
        r.addAppointment(facade.newAppointmentWithUser(start, start.plusHours(1), owner));
        for (Allocatable a : resources) r.addAllocatable(a);
        replacePermissions(r, readers);
        return r;
    }

    private static void replacePermissions(PermissionContainer container, Category readers)
    {
        for (Permission p : container.getPermissionList().toArray(new Permission[0])) container.removePermission(p);
        if (readers != null)
        {
            Permission p = container.newPermission();
            p.setAccessLevel(AccessLevel.READ);
            p.setGroup(readers);
            container.addPermission(p);
        }
    }

    private String raw(String query)
    {
        return client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    private List<Map<String, Object>> conflicts(String filter)
    {
        String arg = filter == null ? "" : "(filter: " + filter + ")";
        return tester.document("{ conflicts" + arg + " { " + FIELDS + " } }").execute()
                .path("conflicts").entityList(ROW).get();
    }

    private static Set<String> ids(List<Map<String, Object>> rows)
    {
        return rows.stream().map(m -> (String) m.get("id")).collect(Collectors.toSet());
    }

    private static Map<String, Object> byId(List<Map<String, Object>> rows, String id)
    {
        return rows.stream().filter(m -> id.equals(m.get("id"))).findFirst().orElseThrow();
    }

    private static String q(String... ids)
    {
        return "[" + java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").collect(Collectors.joining(",")) + "]";
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminWithoutFilterSeesEveryConflictWithItsDisableState()
    {
        List<Map<String, Object>> rows = conflicts(null);
        assertEquals(Set.of(mineHiddenR1, mineReadR1, hiddenReadR1, mineHiddenR2), ids(rows));
        assertEquals(Boolean.TRUE, byId(rows, mineReadR1).get("disabled"));
        assertEquals(Boolean.FALSE, byId(rows, mineHiddenR1).get("disabled"));
        assertEquals(Set.of(mineReadR1), ids(conflicts("{ disabledEq: true }")));
        assertEquals(Set.of(mineHiddenR1, hiddenReadR1, mineHiddenR2), ids(conflicts("{ disabledEq: false }")));
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void nonAdminSeesOnlyModifiableConflictsOnReadableResources()
    {
        assertEquals(Set.of(mineHiddenR1, mineReadR1), ids(conflicts(null)));
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void unreadableOtherSideIsMaskedButKeepsItsIds()
    {
        Map<String, Object> masked = byId(conflicts(null), mineHiddenR1);
        assertEquals(evMine, masked.get("reservation1Id"));
        assertEquals(evHidden, masked.get("reservation2Id"));
        assertTrue(masked.get("appointment2Id") != null, "D6: the other side's ids stay");
        assertNull(masked.get("reservation2"));
        assertNull(masked.get("appointment2"));
        assertNotEquals(HIDDEN_NAME, masked.get("description"));
        assertFalse(((String) masked.get("description")).isBlank());

        Map<String, Object> full = byId(conflicts(null), mineReadR1);
        assertEquals(Map.of("id", evRead), full.get("reservation2"));
        assertEquals("PRD128-READ", full.get("description"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void filtersByReservationResourceAndWindow()
    {
        assertEquals(Set.of(mineReadR1, hiddenReadR1), ids(conflicts("{ reservationIdsIn: " + q(evRead) + " }")));
        Map<String, Object> flipped = byId(conflicts("{ reservationIdsIn: " + q(evRead) + " }"), hiddenReadR1);
        assertEquals(evRead, flipped.get("reservation1Id"), "side 1 = the queried reservation");

        assertEquals(Set.of(mineHiddenR2), ids(conflicts("{ resourceIdsIn: " + q(r2) + " }")));
        assertEquals(Set.of(mineHiddenR2), ids(conflicts("{ resourceMatching: { idIn: " + q(r2) + " } }")));
        assertEquals(Set.of(mineHiddenR2), ids(conflicts("{ resourceIdsIn: " + q(r2) + ", reservationIdsIn: " + q(evMine) + " }")));

        assertEquals(4, conflicts("{ from: \"2027-06-01T00:00\", to: \"2027-06-02T00:00\" }").size());
        assertEquals(0, conflicts("{ from: \"2027-06-02T00:00\" }").size());
        assertEquals(0, conflicts("{ to: \"2027-06-01T09:00\" }").size());
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void hiddenResourceIdsAnswerLikeUnknownIds()
    {
        String sel = " { " + FIELDS + " } }";
        assertEquals(raw("{ conflicts(filter: { resourceIdsIn: " + q(r1) + " })" + sel),
                raw("{ conflicts(filter: { resourceIdsIn: " + q(r1, r2) + " })" + sel));
        assertEquals(raw("{ conflicts(filter: { resourceIdsIn: " + q("no-such-id") + " })" + sel),
                raw("{ conflicts(filter: { resourceIdsIn: " + q(r2) + " })" + sel));
        String all = raw("{ conflicts" + sel);
        assertFalse(all.contains(r2), "hidden resource id must not appear");
        assertFalse(all.contains(HIDDEN_NAME), "hidden event name must not appear");
    }

    private Map<String, Integer> stats(String args)
    {
        List<Map<String, Object>> buckets = tester.document("{ conflictStats" + args + " { keys { key value } values { key number } count } }")
                .execute().path("conflictStats").entityList(ROW).get();
        Map<String, Integer> out = new java.util.TreeMap<>();
        for (Map<String, Object> b : buckets)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) b.get("keys");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> values = (List<Map<String, Object>>) b.get("values");
            assertEquals(List.of(Map.of("key", "COUNT", "number", ((Number) b.get("count")).doubleValue())), values);
            String label = keys.stream().map(k -> k.get("key") + "=" + k.get("value")).collect(Collectors.joining(","));
            out.put(label, ((Number) b.get("count")).intValue());
        }
        return out;
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void conflictStatsCountsPerResourceTypeAndDisableState()
    {
        assertEquals(Map.of("", 4), stats(""));
        assertEquals(Map.of("DISABLED=false", 3, "DISABLED=true", 1), stats("(groupBy: [DISABLED])"));
        assertEquals(Map.of("RESOURCE=" + r1 + ",DISABLED=false", 2, "RESOURCE=" + r1 + ",DISABLED=true", 1,
                "RESOURCE=" + r2 + ",DISABLED=false", 1), stats("(groupBy: [RESOURCE, DISABLED], aggregate: [COUNT])"));
        assertEquals(Map.of("TYPE=room", 4), stats("(groupBy: [TYPE])"));
        assertEquals(Map.of("DISABLED=true", 1), stats("(filter: { disabledEq: true }, groupBy: [DISABLED])"));
        Map<String, Object> entity = tester.document("{ conflictStats(filter: { resourceIdsIn: " + q(r2) + " }, groupBy: [RESOURCE]) { keys { entity { ... on Resource { id } } } } }")
                .execute().path("conflictStats[0].keys[0].entity").entity(ROW).get();
        assertEquals(Map.of("id", r2), entity);
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void conflictStatsSeeOnlyTheCallersConflictsAndHideHiddenResources()
    {
        assertEquals(Map.of("RESOURCE=" + r1, 2), stats("(groupBy: [RESOURCE])"));
        String sel = " { keys { key value } values { key number } count } }";
        assertEquals(raw("{ conflictStats(filter: { resourceIdsIn: " + q(r1) + " }, groupBy: [RESOURCE])" + sel),
                raw("{ conflictStats(filter: { resourceIdsIn: " + q(r1, r2) + " }, groupBy: [RESOURCE])" + sel));
        assertEquals(raw("{ conflictStats(filter: { resourceIdsIn: " + q("no-such-id") + " }, groupBy: [RESOURCE])" + sel),
                raw("{ conflictStats(filter: { resourceIdsIn: " + q(r2) + " }, groupBy: [RESOURCE])" + sel));
        assertFalse(raw("{ conflictStats(groupBy: [RESOURCE, TYPE, DISABLED])" + sel).contains(r2));
    }

    private Set<String> requests(String filter)
    {
        String arg = filter == null ? "" : "(filter: " + filter + ")";
        return tester.document("{ resourceRequests" + arg + " { resource { id } reservationId } }").execute()
                .path("resourceRequests").entityList(ROW).get().stream()
                .map(m -> ((Map<?, ?>) m.get("resource")).get("id") + "/" + m.get("reservationId"))
                .collect(Collectors.toSet());
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void resourceRequestsOneRowPerReservationAndRequestedResource()
    {
        assertEquals(Set.of(ra + "/" + evReq, ra + "/" + evReq2, rb + "/" + evReq), requests(null));
        assertEquals(Set.of(rb + "/" + evReq), requests("{ resourceIdsIn: " + q(rb) + " }"));
        assertEquals(Set.of(ra + "/" + evReq, ra + "/" + evReq2), requests("{ resourceMatching: { idIn: " + q(ra) + " } }"));
        assertEquals(Set.of(ra + "/" + evReq2), requests("{ reservationIdsIn: " + q(evReq2) + " }"));
        List<Map<String, Object>> apps = tester.document("{ resourceRequests(filter: { resourceIdsIn: " + q(rb) + " }) { appointments { id } } }")
                .execute().path("resourceRequests[0].appointments").entityList(ROW).get();
        assertEquals(List.of(Map.of("id", evReqApp)), apps);
        Map<String, Object> row = tester.document("{ resourceRequests(filter: { resourceIdsIn: " + q(rb) + " }) { reservation { id } } }")
                .execute().path("resourceRequests[0]").entity(ROW).get();
        assertEquals(Map.of("id", evReq), row.get("reservation"));
    }

    @Test
    @WithMockUser(username = "lenny", roles = "USER")
    void resourceRequestsOnlyOnResourcesTheCallerMayModify()
    {
        assertEquals(Set.of(ra + "/" + evReq, ra + "/" + evReq2), requests(null));
        Map<String, Object> masked = tester.document("{ resourceRequests(filter: { reservationIdsIn: " + q(evReq) + " }) { reservationId reservation { id } "
                        + "appointments { id } } }")
                .execute().path("resourceRequests[0]").entity(ROW).get();
        assertEquals(evReq, masked.get("reservationId"));
        assertNull(masked.get("reservation"), "the approver cannot read the requesting reservation — masked");
        assertEquals(List.of(), masked.get("appointments"));
        List<Map<String, Object>> calendar = tester.document("{ appointmentBlocks(filter: { from: \"2027-07-01T00:00\", to: \"2027-07-02T00:00\", "
                        + "resourceIdsIn: " + q(ra) + " }) { start reservationId reservation { id } } }")
                .execute().path("appointmentBlocks").entityList(ROW).get();
        assertTrue(calendar.stream().anyMatch(b ->
                evReq.equals(b.get("reservationId")) && b.get("reservation") == null && "2027-07-01T10:00:00".equals(b.get("start"))),
                "the masked request's time shows as an anonymous block on the resource: " + calendar);

        String sel = " { resource { id } reservationId reservation { id } appointments { id name } } }";
        assertFalse(raw("{ resourceRequests" + sel).contains("PRD128-REQ"), "the masked reservation's name must not appear");
        assertEquals(raw("{ resourceRequests(filter: { resourceIdsIn: " + q(ra) + " })" + sel),
                raw("{ resourceRequests(filter: { resourceIdsIn: " + q(ra, rb) + " })" + sel));
        assertEquals(raw("{ resourceRequests(filter: { resourceIdsIn: " + q("no-such-id") + " })" + sel),
                raw("{ resourceRequests(filter: { resourceIdsIn: " + q(rb) + " })" + sel));
        assertFalse(raw("{ resourceRequests" + sel).contains(rb), "hidden resource id must not appear");
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void theRequesterDoesNotSeeTheirOwnRequests()
    {
        assertEquals(Set.of(), requests(null));
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void potentialConflictsMaskTheOtherSideButKeepItsIds()
    {
        List<Map<String, Object>> rows = tester.document("""
                { potentialConflicts(input: {
                    reservationId: "e28c0de2-2222-4222-8222-222222222222",
                    appointments: [{ id: "a28c0de2-2222-4222-8222-222222222223", start: "2027-06-01T10:30", end: "2027-06-01T10:45", allDay: false }],
                    resourceIds: ["%s"], ignoreReservationIds: ["%s"] })
                  { reservation2Id appointment2Id reservation2 { id } appointment2 { id } description disabled } }
                """.formatted(r1, evMine)).execute().path("potentialConflicts").entityList(ROW).get();
        Map<String, Object> masked = rows.stream().filter(m -> m.get("reservation2") == null).findFirst().orElseThrow();
        assertEquals(evHidden, masked.get("reservation2Id"), "D6: the masked side keeps its ids");
        assertTrue(masked.get("appointment2Id") != null);
        assertNull(masked.get("appointment2"));
        assertNotEquals(HIDDEN_NAME, masked.get("description"));
        assertEquals(Boolean.FALSE, masked.get("disabled"));
    }

    @Test
    @WithMockUser(username = "lenny", roles = "USER")
    void hiddenReservationIdsAnswerLikeUnknownIds()
    {
        String unknown = q("e0000000-0000-4000-8000-000000000000");
        String conflictSel = " { " + FIELDS + " } }";
        assertEquals(raw("{ conflicts(filter: { reservationIdsIn: " + unknown + " })" + conflictSel),
                raw("{ conflicts(filter: { reservationIdsIn: " + q(evHidden) + " })" + conflictSel));
        String statSel = " { keys { key value } count } }";
        assertEquals(raw("{ conflictStats(filter: { reservationIdsIn: " + unknown + " }, groupBy: [RESOURCE])" + statSel),
                raw("{ conflictStats(filter: { reservationIdsIn: " + q(evHidden) + " }, groupBy: [RESOURCE])" + statSel));
        String requestSel = " { resource { id } reservationId } }";
        assertEquals(raw("{ resourceRequests(filter: { reservationIdsIn: " + unknown + " })" + requestSel),
                raw("{ resourceRequests(filter: { reservationIdsIn: " + q(evHidden) + " })" + requestSel));
    }

    private static final String WINDOW = "from: \"2027-06-01T00:00\", to: \"2027-06-02T00:00\"";
    private static final Set<String> TIME_FIELDS = Set.of("start", "end", "isException", "appointmentId", "reservationId",
            "duration", "times", "durationMinutes", "wholeDay", "banner", "timeslot");

    private List<Map<String, Object>> blocks(String filter, String fields)
    {
        return tester.document("{ appointmentBlocks(filter: { " + WINDOW + filter + " }) { " + fields + " } }")
                .execute().path("appointmentBlocks").entityList(ROW).get();
    }

    /** Every generated / no-argument scalar field of AppointmentBlock that is not pure time information. */
    @SuppressWarnings("unchecked")
    private List<String> contentScalarFields()
    {
        List<Map<String, Object>> fields = tester.document("{ __type(name: \"AppointmentBlock\") { fields { name args { name } type { kind ofType { kind } } } } }")
                .execute().path("__type.fields").entityList(ROW).get();
        List<String> out = new java.util.ArrayList<>();
        for (Map<String, Object> f : fields)
        {
            Map<String, Object> type = (Map<String, Object>) f.get("type");
            Object kind = "NON_NULL".equals(type.get("kind")) ? ((Map<String, Object>) type.get("ofType")).get("kind") : type.get("kind");
            if ("SCALAR".equals(kind) && ((List<?>) f.get("args")).isEmpty() && !TIME_FIELDS.contains(f.get("name"))) out.add((String) f.get("name"));
        }
        return out;
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void scopedCalendarShowsHiddenBookingsAsAnonymousBlocks()
    {
        List<Map<String, Object>> rows = blocks(", resourceIdsIn: " + q(r1),
                "start end appointmentId reservationId reservation { id } appointment { id } name color compute(expr: \"name\") resources { id } matchedBy { id }");
        Map<String, Object> masked = rows.stream().filter(m -> evHidden.equals(m.get("reservationId"))).findFirst().orElseThrow();
        assertNull(masked.get("reservation"));
        assertNull(masked.get("appointment"));
        assertTrue(masked.get("appointmentId") != null);
        assertEquals("2027-06-01T10:00:00", masked.get("start"));
        assertFalse(((String) masked.get("name")).isBlank());
        assertNotEquals(HIDDEN_NAME, masked.get("name"));
        assertNull(masked.get("color"));
        assertNull(masked.get("compute"));
        assertEquals(List.of(Map.of("id", r1)), masked.get("resources"), "only the scoped readable resource, never R2");
        assertEquals(List.of(Map.of("id", r1)), masked.get("matchedBy"));
        assertEquals(Set.of(evMine, evRead, evHidden), rows.stream().map(m -> (String) m.get("reservationId")).collect(Collectors.toSet()));

        List<String> content = contentScalarFields();
        Map<String, Object> generated = blocks(", resourceIdsIn: " + q(r1), "reservationId " + String.join(" ", content)).stream()
                .filter(m -> evHidden.equals(m.get("reservationId"))).findFirst().orElseThrow();
        for (String field : content)
        {
            if (!field.equals("name")) assertNull(generated.get(field), "masked block must not expose " + field);
        }
        String all = raw("{ appointmentBlocks(filter: { " + WINDOW + ", resourceIdsIn: " + q(r1, r2) + " }) { reservationId name "
                + String.join(" ", content) + " resources { id name } matchedBy { id } } }");
        assertFalse(all.contains(HIDDEN_NAME), "the hidden booking's name must not appear");
        assertFalse(all.contains(r2), "the hidden resource must not appear");
    }

    @Test
    @WithMockUser(username = "monty", roles = "USER")
    void anonymousBlocksOnlyInScopedCalendarQueries()
    {
        assertFalse(blocks("", "reservationId").stream().anyMatch(m -> evHidden.equals(m.get("reservationId"))), "unscoped: none");
        assertFalse(blocks(", resourceIdsIn: " + q(r1) + ", nameContains: \"PRD128\"", "reservationId").stream()
                .anyMatch(m -> evHidden.equals(m.get("reservationId"))), "a content filter must not be evaluated on hidden bookings");
        List<Map<String, Object>> reservations = tester.document("{ reservations(filter: { " + WINDOW + ", resourceIdsIn: " + q(r1) + " }) { id } }")
                .execute().path("reservations").entityList(ROW).get();
        assertEquals(Set.of(evMine, evRead), reservations.stream().map(m -> (String) m.get("id")).collect(Collectors.toSet()));
        Map<String, Object> total = tester.document("{ appointmentBlockStats(filter: { " + WINDOW + ", resourceIdsIn: " + q(r1) + " }, aggregate: [{ key: \"n\", fn: COUNT }]) { count } }")
                .execute().path("appointmentBlockStats[0]").entity(ROW).get();
        assertEquals(2, ((Number) total.get("count")).intValue(), "stats never count anonymous blocks");
    }
}
