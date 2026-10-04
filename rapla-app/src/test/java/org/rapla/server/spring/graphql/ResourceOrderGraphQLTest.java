package org.rapla.server.spring.graphql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 127 D5/D6 — {@code Query.resources} without searchText comes back in Swing tree order: type order (the {@code order}
 * annotation before creation order), then {@code SortedClassifiableComparator} within a type. Fixture {@code testdefault.xml}:
 * {@code room} (created 2014) is older than {@code resource3} (2016); resource3 gets {@code order=1}, room's {@code name}
 * attribute gets {@code sorting=descending}.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ResourceOrderGraphQLTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;
    private static String roomA;
    private static String roomB;
    private static String ordered;
    private static String event;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ResourceOrderGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp() throws Exception
    {
        tester = HttpGraphQlTester.builder(MockMvcWebTestClient.bindTo(mockMvc).build().mutate())
                .url("/api/graphql").build();
        seedOnce();
    }

    private void seedOnce() throws Exception
    {
        if (ordered != null) return;
        DynamicType room = facade.edit(operator.getDynamicType("room"));
        room.getAttribute("name").setAnnotation(AttributeAnnotations.KEY_SORTING, AttributeAnnotations.VALUE_SORTING_DESCENDING);
        DynamicType resource3 = facade.edit(operator.getDynamicType("resource3"));
        resource3.setAnnotation(DynamicTypeAnnotations.KEY_ORDER, "1");
        facade.storeObjects(new Entity[] { room, resource3 });

        User homer = operator.getUser("homer");
        Allocatable a = resource("room", "Sort A", homer);
        Allocatable b = resource("room", "Sort B", homer);
        Allocatable c = resource("resource3", "Sort C", homer);
        facade.storeObjects(new Entity[] { a, b, c });
        roomA = a.getId();
        roomB = b.getId();
        ordered = c.getId();

        DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Reservation r = facade.newReservation(eventType.newClassification(), homer);
        Appointment app = facade.newAppointmentWithUser(LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), homer);
        r.addAppointment(app);
        r.addAllocatable(a);
        r.addAllocatable(c);
        r.addAllocatable(b);
        facade.storeObjects(new Entity[] { r });
        event = r.getId();
    }

    private Allocatable resource(String typeKey, String name, User owner) throws Exception
    {
        Classification c = operator.getDynamicType(typeKey).newClassification();
        c.setValue("name", name);
        return facade.newAllocatable(c, owner);
    }

    private List<String> ids(String... idIn)
    {
        String list = List.of(idIn).stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", "));
        return tester.document("{ resources(filter: { idIn: [" + list + "] }) { id } }")
                .execute()
                .path("resources[*].id")
                .entityList(String.class)
                .get();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void aTypeWithOrderComesBeforeAnOlderTypeWithout()
    {
        assertEquals(List.of(ordered, roomA), ids(roomA, ordered));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void theSortingAnnotationBeatsTheNameOrderWithinAType()
    {
        assertEquals(List.of(roomB, roomA), ids(roomA, roomB));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void nestedResourceListsFollowTheSameOrder()
    {
        var response = tester.document("{ reservation(id: \"" + event + "\") { allocations { resource { id } }"
                        + " appointments { resources { id } blocks(from: \"2026-06-01T00:00:00\", to: \"2026-06-02T00:00:00\") { resources { id } } } } }")
                .execute();
        List<String> expected = List.of(ordered, roomB, roomA);
        assertEquals(expected, response.path("reservation.allocations[*].resource.id").entityList(String.class).get());
        assertEquals(expected, response.path("reservation.appointments[0].resources[*].id").entityList(String.class).get());
        assertEquals(expected, response.path("reservation.appointments[0].blocks[0].resources[*].id").entityList(String.class).get());
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void matchedByStartsWithTheFirstSelectedResourceInTreeOrderAlsoAcrossTheUnionOfBothSelectors()
    {
        List<String> matchedBy = tester.document("{ reservations(filter: { from: \"2026-06-01T00:00:00\", to: \"2026-06-02T00:00:00\","
                        + " resourceMatching: { idIn: [\"" + roomA + "\"] }, resourceIdsIn: [\"" + ordered + "\", \"" + roomB + "\"] }) { id appointments { blocks(from: \"2026-06-01T00:00:00\","
                        + " to: \"2026-06-02T00:00:00\") { matchedBy { id } } } } }")
                .execute()
                .path("reservations[?(@.id == '" + event + "')].appointments[0].blocks[0].matchedBy[*].id")
                .entityList(String.class)
                .get();
        assertEquals(List.of(ordered, roomB, roomA), matchedBy);
    }
}
