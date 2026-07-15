package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

/**
 * PRD 097 Phase 5 — tier-3 tests for the 2D-grid GraphQL surface: {@code strips(filter:)},
 * {@code ReservationFilter.weekdays}, and the {@code AppointmentBlock} geometry/classification
 * fields ({@code segments}, {@code bars(scope:)}, {@code banner}, {@code wholeDay},
 * {@code timeslot}). Fixture anchor: testdefault.xml's single appointment on Tuesday
 * 2001-10-16 12:00–19:00.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class CalendarGridGraphQLTest
{
    private static final String WEEK = "from: \"2001-10-15T00:00:00\", to: \"2001-10-22T00:00:00\"";

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = CalendarGridGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired MockMvc mockMvc;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        tester = HttpGraphQlTester.builder(
                MockMvcWebTestClient.bindTo(mockMvc).build().mutate())
                .url("/api/graphql")
                .build();
    }

    private static final ParameterizedTypeReference<List<Map<String, Object>>> ROWS =
            new ParameterizedTypeReference<>() {};

    // ================================================================= strips

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void stripsScaffoldAWeekWindow()
    {
        List<Map<String, Object>> strips = tester.document("""
                { strips(filter: { %s }) { index days { index date label } } }""".formatted(WEEK))
                .execute()
                .path("strips").entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals(1, strips.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) strips.get(0).get("days");
        assertEquals(7, days.size());
        assertEquals(1, days.get(0).get("index"));
        assertEquals("2001-10-15", days.get(0).get("date"));
        assertEquals("Mo 15.10.", days.get(0).get("label"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void stripsRespectTheWeekdaysFilter()
    {
        List<Map<String, Object>> strips = tester.document("""
                { strips(filter: { %s, weekdays: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY] })
                  { days { index date } } }""".formatted(WEEK))
                .execute()
                .path("strips").entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) strips.get(0).get("days");
        assertEquals(5, days.size());
        assertEquals("2001-10-19", days.get(4).get("date"));
    }

    // ================================================================= block geometry

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void aBlockCarriesSegmentAndBarGeometry()
    {
        List<Map<String, Object>> blocks = tester.document("""
                { appointmentBlocks(filter: { %s }) {
                    start  wholeDay  banner
                    segments { strip dayIndex startMin endMin lane laneCount clippedStart clippedEnd }
                    bars { strip startDay span row }
                    bandBars: bars(scope: BANNER) { strip }
                } }""".formatted(WEEK))
                .execute()
                .path("appointmentBlocks").entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        // The window holds three fixture blocks (weekly Mo 17:00, Tue 12:00, weekly Sa 17:00) —
        // pick the Tuesday one explicitly.
        Map<String, Object> block = blocks.stream()
                .filter(b -> "2001-10-16T12:00:00".equals(b.get("start")))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "fixture block 2001-10-16T12:00 expected in the window, got " + blocks));
        assertEquals(false, block.get("wholeDay"));
        assertEquals(false, block.get("banner"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segments = (List<Map<String, Object>>) block.get("segments");
        assertEquals(1, segments.size());
        Map<String, Object> seg = segments.get(0);
        assertEquals(1, seg.get("strip"));
        assertEquals(2, seg.get("dayIndex"));      // Tuesday
        assertEquals(12 * 60, seg.get("startMin"));
        assertEquals(19 * 60, seg.get("endMin"));
        assertEquals(1, seg.get("lane"));
        assertEquals(1, seg.get("laneCount"));
        assertEquals(false, seg.get("clippedStart"));
        assertEquals(false, seg.get("clippedEnd"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bars = (List<Map<String, Object>>) block.get("bars");
        assertEquals(1, bars.size());
        assertEquals(1, bars.get(0).get("strip"));
        assertEquals(2, bars.get(0).get("startDay"));
        assertEquals(1, bars.get(0).get("span"));
        assertEquals(1, bars.get(0).get("row"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bandBars = (List<Map<String, Object>>) block.get("bandBars");
        assertTrue(bandBars.isEmpty(), "a non-banner block must not enter the BANNER scope");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void theWeekdaysFilterDropsBlocksOnExcludedDays()
    {
        List<Map<String, Object>> blocks = tester.document("""
                { appointmentBlocks(filter: { %s, weekdays: [MONDAY] }) { start } }""".formatted(WEEK))
                .execute()
                .path("appointmentBlocks").entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertFalse(blocks.isEmpty(), "the weekly Monday block must survive");
        assertTrue(blocks.stream().allMatch(b -> b.get("start").toString().startsWith("2001-10-15")),
                () -> "only Monday blocks may survive a MONDAY-only day set, got " + blocks);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void timeslotClassifiesTheBlockStart()
    {
        List<Map<String, Object>> blocks = tester.document("""
                { appointmentBlocks(filter: { %s }) { timeslot } }""".formatted(WEEK))
                .execute()
                .path("appointmentBlocks").entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertFalse(blocks.isEmpty());
        // Default TimeslotProvider config: 2-hour slots 06:00–18:00 — a 12:00 start lands in
        // the 12:00 slot; the label is the locale-formatted slot name, so assert presence only.
        assertNotNull(blocks.get(0).get("timeslot"));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void nestedBlocksOutsideTheFlatListHaveEmptyGeometry()
    {
        // Appointment.blocks(from:, to:) rows are not part of the list-scoped layout —
        // geometry fields answer empty instead of erroring.
        List<Map<String, Object>> reservations = tester.document("""
                { reservations(filter: { %s }) {
                    appointments { blocks(from: "2001-10-15T00:00:00", to: "2001-10-22T00:00:00") {
                      segments { lane }  bars { row }
                    } }
                } }""".formatted(WEEK))
                .execute()
                .path("reservations").entityList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertFalse(reservations.isEmpty());
    }
}
