package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §12 leak guard for {@code POST /api/storage/queryAppointments} (the Swing wire).
 * <ul>
 *   <li>An owner filter only ever selects users the caller could see in the Swing client
 *       ({@code LocalCache.getVisibleEntities}: self + adminable). Foreign owner ids are dropped
 *       silently — exactly like unknown ids — so the response is empty, never another user's
 *       bookings.</li>
 *   <li>Reservations the caller cannot read are shipped anonymised (block visible, content
 *       stripped), as {@code getEntityDependencies} already does.</li>
 * </ul>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class QueryAppointmentsLeakTest
{
    private static final String HOMER_ID = "3f49044c-1469-4699-8a66-5f46ec6c0a41";
    private static final String ERWIN_ROOM_ID = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";
    private static final String SECRET = "SECRET-EVENT-77";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = QueryAppointmentsLeakTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RaplaFacade facade;

    private String query(String token, String body) throws Exception
    {
        return mockMvc.perform(post("/api/storage/queryAppointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String ownerQuery(String ownerId)
    {
        return "{\"ownerIds\":[\"" + ownerId + "\"],\"resources\":[],"
                + "\"start\":\"2001-01-01T00:00:00\",\"end\":\"2020-12-31T00:00:00\",\"requestsOnly\":false}";
    }

    private static String resourceQuery(String resourceId)
    {
        return "{\"ownerIds\":[],\"resources\":[\"" + resourceId + "\"],"
                + "\"start\":\"2010-01-01T00:00:00\",\"end\":\"2010-01-10T00:00:00\",\"requestsOnly\":false}";
    }

    @Test
    void ownerFilterDropsUsersTheCallerCannotAdmin() throws Exception
    {
        String homer = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        mockMvc.perform(post("/api/storage/queryAppointments")
                        .header("Authorization", "Bearer " + homer)
                        .contentType("application/json")
                        .content(ownerQuery(HOMER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations").isNotEmpty());

        // monty is a group admin but homer (isAdmin) is never adminable → same as an unknown id.
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        mockMvc.perform(post("/api/storage/queryAppointments")
                        .header("Authorization", "Bearer " + monty)
                        .contentType("application/json")
                        .content(ownerQuery(HOMER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations").isEmpty());
    }

    @Test
    void unreadableReservationsAreAnonymised() throws Exception
    {
        String reservationId = storeSecretEventOnErwin();

        String homer = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        String homerBody = query(homer, resourceQuery(ERWIN_ROOM_ID));
        assertTrue(homerBody.contains(reservationId), "owner must get the block");
        assertTrue(homerBody.contains(SECRET), "owner must get the content");

        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        String montyBody = query(monty, resourceQuery(ERWIN_ROOM_ID));
        assertTrue(montyBody.contains(reservationId), "block must stay visible (occupied slot)");
        assertFalse(montyBody.contains(SECRET), "content of an unreadable reservation leaked");
    }

    /** A reservation type with NO permissions: only owner/admin can read its instances. */
    private String storeSecretEventOnErwin() throws Exception
    {
        DynamicType type = facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        type.setKey("secretevent");
        type.getName().setName("en", "secret event");
        for (var p : new ArrayList<>(type.getPermissionList()))
        {
            type.removePermission(p);
        }
        type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
        facade.store(type);

        User homer = facade.tryResolve(new ReferenceInfo<>(HOMER_ID, User.class));
        Allocatable erwin = facade.tryResolve(new ReferenceInfo<>(ERWIN_ROOM_ID, Allocatable.class));
        assertNotNull(homer);
        assertNotNull(erwin);
        Classification classification = facade.getDynamicType("secretevent").newClassification();
        classification.setValue("name", SECRET);
        Reservation reservation = facade.newReservation(classification, homer);
        Appointment appointment = facade.newAppointmentWithUser(
                LocalDateTime.of(2010, 1, 5, 10, 0), LocalDateTime.of(2010, 1, 5, 11, 0), homer);
        reservation.addAppointment(appointment);
        reservation.addAllocatable(erwin);
        facade.store(reservation);
        return reservation.getId();
    }
}
