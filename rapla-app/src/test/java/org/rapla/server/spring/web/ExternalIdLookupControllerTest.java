package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/externalids/resolve} — batch lookup of the {@code externalid}
 * annotation against the operator's in-memory {@code externalIds} index. Backs the
 * Swing template import, which otherwise had to pull every event from today onwards
 * to find out which rows were already imported (and therefore missed past ones).
 *
 * <p>§12 leak guard: "not found" and "found but you may not read it" must be
 * indistinguishable — both are simply absent from the response map.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ExternalIdLookupControllerTest
{
    private static final String HOMER_ID = "3f49044c-1469-4699-8a66-5f46ec6c0a41";
    private static final String ERWIN_ROOM_ID = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ExternalIdLookupControllerTest.class.getResourceAsStream("/testdefault.xml"))
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
    @Autowired RaplaFacade facade;

    /** An event in the PAST carrying an externalid — the case the date-windowed
     *  client-side scan could never find. */
    private String storeEventWithExternalId(String externalId, boolean readableByEveryone) throws Exception
    {
        String typeKey = "importtype" + externalId.replaceAll("[^a-zA-Z0-9]", "");
        DynamicType type = facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        type.setKey(typeKey);
        type.getName().setName("en", typeKey);
        if (!readableByEveryone)
        {
            for (var p : new ArrayList<>(type.getPermissionList())) type.removePermission(p);
        }
        type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
        facade.store(type);
        User homer = facade.tryResolve(new ReferenceInfo<>(HOMER_ID, User.class));
        Allocatable erwin = facade.tryResolve(new ReferenceInfo<>(ERWIN_ROOM_ID, Allocatable.class));
        Classification classification = facade.getDynamicType(typeKey).newClassification();
        classification.setValue("name", "import " + externalId);
        Reservation reservation = facade.newReservation(classification, homer);
        Appointment appointment = facade.newAppointmentWithUser(
                LocalDateTime.of(2010, 1, 5, 10, 0), LocalDateTime.of(2010, 1, 5, 11, 0), homer);
        reservation.addAppointment(appointment);
        reservation.addAllocatable(erwin);
        reservation.setAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID, externalId);
        facade.store(reservation);
        return reservation.getId();
    }

    private static String body(String... ids)
    {
        return "[\"" + String.join("\",\"", ids) + "\"]";
    }

    @Test
    void resolvesAPastEventByItsExternalId() throws Exception
    {
        String id = storeEventWithExternalId("S-1001", true);
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        mockMvc.perform(post("/api/externalids/resolve")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body("S-1001", "S-does-not-exist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['S-1001']").value(org.hamcrest.Matchers.contains(id)))
                .andExpect(jsonPath("$['S-does-not-exist']").doesNotExist());
    }

    @Test
    void unreadableIsIndistinguishableFromUnknown() throws Exception
    {
        storeEventWithExternalId("S-2002", false);
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");

        String unreadable = mockMvc.perform(post("/api/externalids/resolve")
                        .header("Authorization", "Bearer " + monty)
                        .contentType("application/json")
                        .content(body("S-2002")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/externalids/resolve")
                        .header("Authorization", "Bearer " + monty)
                        .contentType("application/json")
                        .content(body("S-never-existed")))
                .andExpect(status().isOk())
                .andExpect(content().string(unreadable));
    }

    /** Real seminar data has the majority of its external ids carrying more than one event
     *  (a template copies N reservations, all stamped with the same id), so an index that
     *  keeps one reference per id would silently hide the rest — and a delete driven by it
     *  would remove only one of them. */
    @Test
    void allEventsSharingAnExternalIdAreReturned() throws Exception
    {
        String first = storeEventWithExternalId("S-5005", true);
        String second = storeEventWithExternalId("S-5005b", true);
        // second event, same external id
        Reservation reservation = facade.tryResolve(new ReferenceInfo<>(second, Reservation.class));
        Reservation editable = facade.edit(reservation);
        editable.setAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID, "S-5005");
        facade.store(editable);

        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        mockMvc.perform(post("/api/externalids/resolve")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body("S-5005")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['S-5005']").value(org.hamcrest.Matchers.containsInAnyOrder(first, second)));
    }

    /** Template reservations live in the same cache and can carry an external id
     *  (a template built by copying an imported event keeps the annotation). They are
     *  NOT events — the old client path never saw them because template reservations
     *  are filtered out of normal queries. Returning one would make the import treat
     *  the seminar as "already there" — or delete the template on a storno row. */
    @Test
    void templateReservationsAreNotReturned() throws Exception
    {
        String id = storeEventWithExternalId("S-6006", true);
        Reservation reservation = facade.tryResolve(new ReferenceInfo<>(id, Reservation.class));
        Reservation editable = facade.edit(reservation);
        editable.setAnnotation(org.rapla.entities.domain.RaplaObjectAnnotations.KEY_TEMPLATE, ERWIN_ROOM_ID);
        facade.store(editable);

        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        mockMvc.perform(post("/api/externalids/resolve")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body("S-6006")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['S-6006']").doesNotExist());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception
    {
        mockMvc.perform(post("/api/externalids/resolve")
                        .contentType("application/json")
                        .content(body("S-1001")))
                .andExpect(status().isUnauthorized());
    }
}
