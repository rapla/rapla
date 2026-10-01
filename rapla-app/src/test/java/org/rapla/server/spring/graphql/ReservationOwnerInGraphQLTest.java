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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 123 D10 — {@code ReservationFilter.ownerIn} is UNIONed with the resource scope
 * ({@code resourceIdsIn} / {@code resourceMatching}), Swing's
 * {@code queryAppointmentsSync(allocatables, owners)}; owners the caller cannot see are dropped
 * silently (§12). {@code ResourceFilter.ownerIn} narrows resources by their owner (AND).
 *
 * <p>Seeded once on 2026-06-01: room R (homer), event H1 (homer, on R), event H2 (homer, no
 * resource), event M1 (monty, no resource). homer is admin, monty cannot administer homer.
 *
 * <p>{@code addFilters = false}: the security filter chain is bypassed so {@code @WithMockUser}
 * reaches the resolver; the resolvers still enforce §12 themselves.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Tag("e2e")
class ReservationOwnerInGraphQLTest
{
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ROWS = new ParameterizedTypeReference<>() {};
    private static final String WINDOW = "from: \"2026-06-01T00:00:00\", to: \"2026-06-02T00:00:00\"";

    @TempDir
    static Path tempDir;
    static Path dataFile;
    static boolean seeded;
    static String room, h1, h2, m1, homerId, montyId;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationOwnerInGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
        if (seeded) return;
        User homer = operator.getUser("homer");
        User monty = operator.getUser("monty");
        homerId = homer.getId();
        montyId = monty.getId();
        DynamicType roomType = operator.getDynamicType("room");
        Classification c = roomType.newClassification();
        c.setValue("name", "OWNERIN-R");
        Allocatable r = facade.newAllocatable(c, homer);
        room = store(r);
        Reservation e1 = event(homer);
        e1.addAllocatable(r);
        h1 = store(e1);
        h2 = store(event(homer));
        m1 = store(event(monty));
        seeded = true;
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

    private String store(Entity<?> entity) throws Exception
    {
        facade.storeObjects(new Entity[] { entity });
        return entity.getId();
    }

    private Set<String> ids(String filterBody)
    {
        List<Map<String, Object>> rows = tester.document("{ reservations(filter: { " + WINDOW + filterBody + " }) { id } }")
                .execute().path("reservations").entity(ROWS).get();
        return rows.stream().map(m -> (String) m.get("id")).collect(Collectors.toSet());
    }

    private static String list(String... ids)
    {
        return "[" + java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").collect(Collectors.joining(", ")) + "]";
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
    void resourceScopeAloneKeepsOtherOwnersOut()
    {
        assertEquals(Set.of(h1), ids(", resourceIdsIn: " + list(room)));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void ownerAloneAdmitsTheOwnersEventsWithoutAResource()
    {
        assertEquals(Set.of(m1), ids(", ownerIn: " + list(montyId)));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void resourceAndOwnerAreUnioned()
    {
        assertEquals(Set.of(h1, m1), ids(", resourceIdsIn: " + list(room) + ", ownerIn: " + list(montyId)));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void resourceMatchingAndOwnerAreUnioned()
    {
        assertEquals(Set.of(h1, m1), ids(", resourceMatching: { idIn: " + list(room) + " }, ownerIn: " + list(montyId)));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void twoOwnersAreUnioned()
    {
        Set<String> got = ids(", ownerIn: " + list(homerId, montyId));
        assertTrue(got.containsAll(Set.of(h1, h2, m1)), () -> "expected h1, h2, m1 in " + got);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void emptyOwnerInIsNoFilter()
    {
        assertEquals(ids(""), ids(", ownerIn: []"));
    }

    /** §12 — monty cannot administer homer: the id is dropped like an unknown one, byte-identical. */
    @Test
    @WithMockUser(username = "monty")
    void hiddenOwnerIsDroppedLikeAnUnknownId()
    {
        String hidden = raw("{ reservations(filter: { " + WINDOW + ", ownerIn: " + list(homerId) + " }) { id } }");
        String unknown = raw("{ reservations(filter: { " + WINDOW + ", ownerIn: " + list("no-such-user") + " }) { id } }");
        assertEquals(unknown, hidden);
        assertEquals(Set.of(m1), ids(", ownerIn: " + list(homerId, montyId)));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void resourceFilterOwnerInNarrowsByResourceOwner()
    {
        List<Map<String, Object>> homers = tester.document("{ resources(filter: { nameContains: \"OWNERIN\", ownerIn: "
                + list(homerId) + " }) { id } }").execute().path("resources").entity(ROWS).get();
        List<Map<String, Object>> montys = tester.document("{ resources(filter: { nameContains: \"OWNERIN\", ownerIn: "
                + list(montyId) + " }) { id } }").execute().path("resources").entity(ROWS).get();
        assertEquals(List.of(room), homers.stream().map(m -> m.get("id")).toList());
        assertEquals(List.of(), montys);
    }

    /** §12 — ResourceFilter.ownerIn drops owners the caller cannot see, like ReservationFilter.ownerIn. */
    @Test
    @WithMockUser(username = "monty")
    void resourceFilterHiddenOwnerIsDroppedLikeAnUnknownId()
    {
        String hidden = raw("{ resources(filter: { nameContains: \"OWNERIN\", ownerIn: " + list(homerId) + " }) { id } }");
        String unknown = raw("{ resources(filter: { nameContains: \"OWNERIN\", ownerIn: " + list("no-such-user") + " }) { id } }");
        assertEquals(unknown, hidden);
    }
}
