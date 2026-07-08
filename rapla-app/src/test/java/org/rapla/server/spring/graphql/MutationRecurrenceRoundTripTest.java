package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 091 Phase 2.0 — full-state round-trip safety of the reservation
 * mutations. A full-state update must NEVER destroy state the client
 * echoes verbatim: repeating rules and allDay were silently dropped by
 * {@code buildAppointment} (PRD 056 v1 TODO) — an update of a recurring
 * event flattened the series.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class MutationRecurrenceRoundTripTest
{
    static final String ROOM_A66 = "c24ce517-4697-4e52-9917-ec000c84563c";

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = MutationRecurrenceRoundTripTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing from classpath");
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

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate())
                .url("/api/graphql")
                .build();
    }

    private static final String READ_BACK = """
            query ($id: ID!) {
              reservation(id: $id) {
                id
                appointments { id start end allDay repeating { type interval end count exceptions } }
              }
            }
            """;

    @SuppressWarnings("unchecked")
    private Map<String, Object> readAppointment(String reservationId)
    {
        Map<String, Object> reservation = tester.document(READ_BACK)
                .variable("id", reservationId)
                .execute()
                .path("reservation")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertNotNull(reservation, "read-back must find the reservation");
        List<Map<String, Object>> appointments = (List<Map<String, Object>>) reservation.get("appointments");
        assertEquals(1, appointments.size());
        return appointments.get(0);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createMaterializesWeeklyRepeatingAndAllDay()
    {
        String reservationId = "e0000000-2000-4000-8000-000000000001";
        String appointmentId = "a0000000-2000-4000-8000-000000000001";
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: { name: "weekly-roundtrip" } },
                    appointments: [
                      { id: "%s", start: "2031-07-01T10:00:00", end: "2031-07-01T11:30:00",
                        allDay: false,
                        repeating: { type: WEEKLY, interval: 1, end: "2031-09-30", exceptions: ["2031-07-15"] } }
                    ],
                    allocations: [ { allocatableId: "%s" } ]
                  }) { id }
                }
                """.formatted(reservationId, appointmentId, ROOM_A66))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        Map<String, Object> appointment = readAppointment(reservationId);
        @SuppressWarnings("unchecked")
        Map<String, Object> repeating = (Map<String, Object>) appointment.get("repeating");
        assertNotNull(repeating, "repeating rule must survive create (was: silently dropped)");
        assertEquals("WEEKLY", repeating.get("type"));
        assertEquals(1, ((Number) repeating.get("interval")).intValue());
        assertNotNull(repeating.get("end"), "UNTIL bound must survive");
        assertEquals(List.of("2031-07-15"), repeating.get("exceptions"), "exceptions must survive");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void updateEchoingRepeatingKeepsTheSeries()
    {
        String reservationId = "e0000000-2000-4000-8000-000000000002";
        String appointmentId = "a0000000-2000-4000-8000-000000000002";
        String appointmentJson = """
                { id: "%s", start: "2031-08-04T09:00:00", end: "2031-08-04T10:00:00",
                  allDay: false,
                  repeating: { type: WEEKLY, interval: 2, count: 6, exceptions: [] } }
                """.formatted(appointmentId);
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: { name: "update-roundtrip" } },
                    appointments: [ %s ],
                    allocations: [ { allocatableId: "%s" } ]
                  }) { id }
                }
                """.formatted(reservationId, appointmentJson, ROOM_A66))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        // Full-state update echoing the unchanged appointment (the sheet's
        // pass-through) — only the name changes.
        tester.document("""
                mutation {
                  updateReservation(id: "%s", input: {
                    typeKey: "event",
                    classification: { event: { name: "update-roundtrip-renamed" } },
                    appointments: [ %s ],
                    allocations: [ { allocatableId: "%s" } ]
                  }) { id }
                }
                """.formatted(reservationId, appointmentJson, ROOM_A66))
                .execute()
                .path("updateReservation.id")
                .entity(String.class)
                .get();

        Map<String, Object> appointment = readAppointment(reservationId);
        @SuppressWarnings("unchecked")
        Map<String, Object> repeating = (Map<String, Object>) appointment.get("repeating");
        assertNotNull(repeating, "repeating rule must survive a full-state update (was: series flattened)");
        assertEquals("WEEKLY", repeating.get("type"));
        assertEquals(2, ((Number) repeating.get("interval")).intValue());
        assertEquals(6, ((Number) repeating.get("count")).intValue());
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void allDayRoundTrips()
    {
        String reservationId = "e0000000-2000-4000-8000-000000000003";
        tester.document("""
                mutation {
                  createReservation(input: {
                    id: "%s",
                    typeKey: "event",
                    classification: { event: { name: "allday-roundtrip" } },
                    appointments: [
                      { id: "a0000000-2000-4000-8000-000000000003",
                        start: "2031-09-01T00:00:00", end: "2031-09-02T00:00:00", allDay: true }
                    ],
                    allocations: []
                  }) { id }
                }
                """.formatted(reservationId))
                .execute()
                .path("createReservation.id")
                .entity(String.class)
                .get();

        Map<String, Object> appointment = readAppointment(reservationId);
        assertEquals(Boolean.TRUE, appointment.get("allDay"), "allDay must survive create");
        assertNull(appointment.get("repeating"), "no repeating requested");
    }
}
