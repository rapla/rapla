package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 104 Phase 4 (D8) — {@code reservationsFromTemplate(templateId:)}: the template's
 * reservations as plain Reservation entities. §12: an unknown id and a template the caller
 * cannot read answer IDENTICALLY (empty list).
 *
 * <p>Fixture: homer (admin) owns a blanket-READ template carrying one annotated reservation,
 * and a permission-less private template carrying another. monty is a non-admin.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationsFromTemplateGraphQLTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationsFromTemplateGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

    HttpGraphQlTester tester;
    static String sharedTemplateId;
    static String privateTemplateId;
    static String sharedReservationId;

    @BeforeEach
    void setUp() throws Exception
    {
        tester = HttpGraphQlTester.builder(MockMvcWebTestClient.bindTo(mockMvc).build().mutate())
                .url("/api/graphql").build();
        if (sharedTemplateId == null)
        {
            User homer = operator.getUser("homer");
            sharedTemplateId = seedTemplate("SHARED-T", homer, true);
            privateTemplateId = seedTemplate("PRIVATE-T", homer, false);
            sharedReservationId = seedTemplateReservation(sharedTemplateId, homer, "Vorlagen-Event");
            seedTemplateReservation(privateTemplateId, homer, "Geheimes Vorlagen-Event");
        }
    }

    private String seedTemplate(String name, User owner, boolean blanketRead) throws Exception
    {
        DynamicType templateType = operator.getDynamicType(StorageOperator.RAPLA_TEMPLATE);
        Classification c = templateType.newClassification();
        c.setValue("name", name);
        Allocatable template = facade.newAllocatable(c, owner);
        for (Permission p : template.getPermissionList().toArray(new Permission[0]))
        {
            template.removePermission(p);
        }
        if (blanketRead)
        {
            Permission read = template.newPermission();
            read.setAccessLevel(Permission.AccessLevel.READ);
            template.addPermission(read);
        }
        facade.storeObjects(new Entity[] { template });
        return template.getId();
    }

    private String seedTemplateReservation(String templateId, User owner, String name) throws Exception
    {
        DynamicType eventType = facade.getDynamicType("event");
        Classification c = eventType.newClassification();
        c.setValue("name", name);
        Reservation reservation = facade.newReservation(c, owner);
        Appointment appointment = facade.newAppointmentWithUser(
                LocalDateTime.of(2001, 10, 16, 12, 0), LocalDateTime.of(2001, 10, 16, 14, 0), owner);
        reservation.addAppointment(appointment);
        reservation.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, templateId);
        facade.storeObjects(new Entity[] { reservation });
        return reservation.getId();
    }

    private List<Map<String, Object>> query(String templateId)
    {
        return tester.document("""
                { reservationsFromTemplate(templateId: "%s") { id } }""".formatted(templateId))
                .execute()
                .path("reservationsFromTemplate")
                .entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
    }

    @Test
    @WithMockUser(username = "monty")
    void readableTemplateReturnsItsReservations()
    {
        List<Map<String, Object>> result = query(sharedTemplateId);
        assertEquals(1, result.size());
        assertEquals(sharedReservationId, result.get(0).get("id"));
    }

    @Test
    @WithMockUser(username = "monty")
    void hiddenTemplateAnswersLikeNonexistent()
    {
        List<Map<String, Object>> hidden = query(privateTemplateId);
        List<Map<String, Object>> unknown = query("00000000-0000-0000-0000-deadbeef0000");
        assertTrue(unknown.isEmpty(), "unknown template id must yield an empty list");
        assertEquals(unknown, hidden,
                "§12 — an unreadable template must be indistinguishable from a nonexistent one");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminReadsPrivateTemplateReservations()
    {
        assertEquals(1, query(privateTemplateId).size());
    }
}
