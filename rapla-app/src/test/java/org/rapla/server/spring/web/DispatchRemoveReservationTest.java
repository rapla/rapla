package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterises the wire path the Swing template import uses to delete events
 * ({@code facade.removeObjects} → {@code POST /api/storage/dispatch} with a
 * {@code removeSet}).
 *
 * <p>Written while chasing a 2026-08-29 field observation: one dispatch
 * carrying 20 reservation removes logged "Reservation thats is scheduled to
 * delete not found" for every one of them, and none of the events was actually
 * deleted (rows still in EVENT, no row in the history). If this test is green,
 * the generic path is sound and the live failure is state-specific — the WARN
 * added in {@code RemoteStorageController.dispatch_} names the ids next time.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class DispatchRemoveReservationTest
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
        try (InputStream in = DispatchRemoveReservationTest.class.getResourceAsStream("/testdefault.xml"))
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

    private String storeImportedEvent(String externalId) throws Exception
    {
        User homer = facade.tryResolve(new ReferenceInfo<>(HOMER_ID, User.class));
        Allocatable erwin = facade.tryResolve(new ReferenceInfo<>(ERWIN_ROOM_ID, Allocatable.class));
        Classification classification = facade.getDynamicType("event").newClassification();
        classification.setValue("name", "import " + externalId);
        Reservation reservation = facade.newReservation(classification, homer);
        reservation.addAppointment(facade.newAppointmentWithUser(
                LocalDateTime.of(2030, 10, 25, 16, 0), LocalDateTime.of(2030, 10, 25, 17, 0), homer));
        reservation.addAllocatable(erwin);
        reservation.setAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID, externalId);
        facade.store(reservation);
        return reservation.getId();
    }

    private int dispatchRemove(String token, String id) throws Exception
    {
        String lastValidated = SerializableDateTimeFormat.INSTANCE.formatTimestamp(LocalDateTime.of(2020, 1, 1, 0, 0));
        String body = "{\"removeSet\":[{\"id\":\"" + id + "\",\"localname\":\"reservation\"}],"
                + "\"lastValidated\":\"" + lastValidated + "\"}";
        return mockMvc.perform(post("/api/storage/dispatch")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void removeSetDeletesTheReservationAndItsExternalIdBinding() throws Exception
    {
        String id = storeImportedEvent("S-3003");
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        org.junit.jupiter.api.Assertions.assertEquals(200, dispatchRemove(token, id));

        assertNull(facade.tryResolve(new ReferenceInfo<>(id, Reservation.class)), "reservation still in the cache");

        mockMvc.perform(post("/api/externalids/resolve")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("[\"S-3003\"]"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$['S-3003']").doesNotExist());
    }

    @Test
    void removingAnUnknownIdIsRejectedAndChangesNothing() throws Exception
    {
        String id = storeImportedEvent("S-4004");
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        // An id the server never knew is rejected outright ("History not available for id ..."),
        // so a client CANNOT get a silent no-op delete this way — which rules that out as the
        // explanation for that field observation, where the dispatch returned 200.
        org.junit.jupiter.api.Assertions.assertEquals(500, dispatchRemove(token, "00000000-0000-0000-0000-000000000000"));

        assertNotNull(facade.tryResolve(new ReferenceInfo<>(id, Reservation.class)), "unrelated event was removed");
    }
}
