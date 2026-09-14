package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * PRD 095 Phase 3b (OQ3) — tier-3 tests for the drag-gate facts on the wire:
 * {@code AppointmentBlock.appointment} (the owning appointment, navigable — id +
 * repeating rule) and {@code Reservation.appointmentCount}. The month-grid drag gate
 * reads {@code canModify && appointmentCount == 1 && appointment.repeating == null}
 * client-side; the server re-checks permissions in {@code moveReservations}.
 *
 * <p>Fixture (unpatched testdefault.xml): "Reservation 2" has TWO appointments
 * (2001-10-16 single + 2006 monthly) → count 2, block's own appointment non-repeating;
 * "bowling" repeats weekly in the same window → repeating.type WEEKLY; "Test"
 * (2016-09-18, all-day) is the single-appointment non-repeating case → count 1.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class AppointmentBlockAppointmentGraphQLTest
{
    private static final String RESERVATION2_APPOINTMENT_ID = "aa4b5e33-3b97-4acc-9d21-6c8800518f09";

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AppointmentBlockAppointmentGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

    /** 2001-10-15..17 covers Reservation 2's single occurrence AND a weekly bowling block. */
    private static final String OCTOBER_QUERY = """
            { appointmentBlocks(filter: { from: "2001-10-15T00:00:00", to: "2001-10-17T00:00:00" }) {
                name
                appointment { id repeating { type } }
                reservation { appointmentCount }
            } }
            """;

    /** 2016-09-18..19 covers the all-day single-appointment "Test" reservation. */
    private static final String SINGLE_QUERY = """
            { appointmentBlocks(filter: { from: "2016-09-18T00:00:00", to: "2016-09-19T00:00:00" }) {
                name
                appointment { repeating { type } }
                reservation { appointmentCount }
            } }
            """;

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void blockNavigatesToItsOwningAppointment() throws Exception
    {
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(OCTOBER_QUERY)))
                // the block's own appointment — not just any of the reservation's
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='Reservation 2')].appointment.id")
                        .value(contains(RESERVATION2_APPOINTMENT_ID)))
                // that appointment is non-repeating even though a sibling repeats monthly
                .andExpect(jsonPath(
                        "$.data.appointmentBlocks[?(@.name=='Reservation 2' && @.appointment.repeating != null)]")
                        .isEmpty())
                // a repeating block reports its rule (bowling's second weekly appointment
                // starts 2001-10-20 — outside the window, so exactly one block)
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='bowling')].appointment.repeating.type")
                        .value(contains("WEEKLY")));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentCountDistinguishesSingleFromMulti() throws Exception
    {
        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(OCTOBER_QUERY)))
                // Reservation 2 has a second (monthly) appointment → count 2, drag gate false
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='Reservation 2')].reservation.appointmentCount")
                        .value(contains(2)));

        mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(SINGLE_QUERY)))
                // "Test" is the draggable shape: one appointment, no repeating
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='Test')].reservation.appointmentCount")
                        .value(contains(1)))
                .andExpect(jsonPath("$.data.appointmentBlocks[?(@.name=='Test' && @.appointment.repeating != null)]")
                        .isEmpty());
    }

    private static String gqlBody(String query)
    {
        return "{\"query\":\"" + query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"}";
    }
}
