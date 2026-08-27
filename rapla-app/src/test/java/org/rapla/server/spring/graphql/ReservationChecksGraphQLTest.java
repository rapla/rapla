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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 105 Phase 2 — tier-3 tests for {@code reservationChecks(input:)}: the pre-save checks the
 * Swing client has always run, now callable by any client over one field.
 *
 * <p>Fixture (testdefault.xml): homer is admin, monty is not; allocatables "Room A66" / "erwin".
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationChecksGraphQLTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationChecksGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
    @Autowired org.rapla.facade.RaplaFacade facade;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
    }

    private static final String QUERY = """
            query ($input: ReservationCheckInput!) {
              reservationChecks(input: $input) {
                code args severity
                conflicts { allocatable { name } reservation2 { name } startDate }
              }
            }
            """;

    private List<Map<String, Object>> check(Map<String, Object> input)
    {
        return tester.document(QUERY).variable("input", input).execute()
                .path("reservationChecks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
    }

    private static Map<String, Object> draft(String name, List<Map<String, Object>> allocations,
            List<Map<String, Object>> appointments)
    {
        return Map.of(
                "id", "e5555555-5555-4555-8555-555555555555",
                "typeKey", "event",
                "classification", Map.of("event", Map.of("name", name)),
                "appointments", appointments,
                "allocations", allocations);
    }

    private static Map<String, Object> appointment(String id, String day)
    {
        return Map.of("id", id, "start", day + "T10:00:00", "end", day + "T11:00:00", "allDay", false);
    }

    private static List<String> codes(List<Map<String, Object>> warnings)
    {
        return warnings.stream().map(w -> (String) w.get("code")).toList();
    }

    /** The three draft-only rules, from the same rapla-core functions the Swing dialog uses. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void anEmptyDraftReportsTheMissingNameAndTheMissingResources()
    {
        List<Map<String, Object>> warnings = check(Map.of("draft",
                draft("", List.of(), List.of(appointment("a5555555-5555-4555-8555-555555555551", "2026-09-10")))));

        assertTrue(codes(warnings).contains("NO_RESERVATION_NAME"), () -> "got " + codes(warnings));
        assertTrue(codes(warnings).contains("NO_ALLOCATABLES_SELECTED"), () -> "got " + codes(warnings));
    }

    /** D7 — severity rides with the code, so both clients gate the save identically. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void theMissingNameBlocksWhileTheMissingResourceIsConfirmable()
    {
        List<Map<String, Object>> warnings = check(Map.of("draft",
                draft("", List.of(), List.of(appointment("a5555555-5555-4555-8555-555555555552", "2026-09-11")))));

        for (Map<String, Object> w : warnings)
        {
            String expected = "NO_RESERVATION_NAME".equals(w.get("code")) ? "BLOCKING" : "CONFIRMABLE";
            assertEquals(expected, w.get("severity"), () -> "severity of " + w.get("code"));
        }
    }

    /** A complete draft is silent — the check must not manufacture findings. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void aCompleteDraftInAFreeSlotReportsNothing() throws Exception
    {
        String allocId = facade.getAllocatables()[0].getId();
        List<Map<String, Object>> warnings = check(Map.of("draft",
                draft("Vollständig", List.of(Map.of("allocatableId", allocId)),
                        List.of(appointment("a5555555-5555-4555-8555-555555555553", "2031-09-12")))));

        assertEquals(List.of(), codes(warnings), () -> "expected no findings, got " + warnings);
    }

    /**
     * D3 — with a scope stated, a draft that touches none of the scoped resources reports
     * NOT_IN_CALENDAR; without a scope the check is skipped (an absent scope is not an empty one).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void aDraftOutsideTheStatedScopeIsReportedOnlyWhenAScopeIsGiven() throws Exception
    {
        var allocatables = facade.getAllocatables();
        String used = allocatables[0].getId();
        String scoped = allocatables[1].getId();
        Map<String, Object> theDraft = draft("Außerhalb", List.of(Map.of("allocatableId", used)),
                List.of(appointment("a5555555-5555-4555-8555-555555555554", "2031-09-13")));

        List<String> scopedCodes = codes(check(Map.of("draft", theDraft, "scopeAllocatableIds", List.of(scoped))));
        assertTrue(scopedCodes.contains("NOT_IN_CALENDAR"),
                () -> "a draft using none of the scoped resources is not in that calendar; got " + scopedCodes);
        assertFalse(codes(check(Map.of("draft", theDraft))).contains("NOT_IN_CALENDAR"),
                "no scope stated → the check is skipped, not failed");
        assertFalse(codes(check(Map.of("draft", theDraft, "scopeAllocatableIds", List.of(used))))
                        .contains("NOT_IN_CALENDAR"),
                "the scoped resource IS allocated — that draft is in the calendar");
    }

    /**
     * §12 — the draft is built by the mutation mapper, so a check cannot confirm the existence of
     * an allocatable the caller may not touch: an unreadable id and a never-existing id both fail
     * the same way, with no warning list revealing which.
     */
    @Test
    @WithMockUser(username = "monty")
    void anUnreadableAllocatableIsIndistinguishableFromANonExistentOne() throws Exception
    {
        org.rapla.entities.domain.Allocatable hidden = facade.edit(facade.getAllocatables()[0]);
        for (org.rapla.entities.domain.Permission p : hidden.getPermissionList()
                .toArray(new org.rapla.entities.domain.Permission[0]))
        {
            hidden.removePermission(p);
        }
        org.rapla.entities.domain.Permission denied = hidden.newPermission();
        denied.setAccessLevel(org.rapla.entities.domain.Permission.AccessLevel.DENIED);
        hidden.addPermission(denied);
        facade.storeObjects(new org.rapla.entities.Entity[] { hidden });

        // The id the caller sent is echoed back — that is their own input, not a disclosure; the
        // rest of the response must be byte-identical.
        String hiddenErrors = errorsFor(hidden.getId()).replace(hidden.getId(), "<id>");
        String missingErrors = errorsFor("r0000000-0000-4000-8000-000000000000")
                .replace("r0000000-0000-4000-8000-000000000000", "<id>");
        assertEquals(missingErrors, hiddenErrors,
                "a hidden allocatable must answer exactly like a nonexistent one");
    }

    /** The response reduced to what a prober could observe: error classifications + messages. */
    private String errorsFor(String allocatableId)
    {
        StringBuilder observable = new StringBuilder();
        tester.document(QUERY)
                .variable("input", Map.of("draft", draft("Sonde",
                        List.of(Map.of("allocatableId", allocatableId)),
                        List.of(appointment("a5555555-5555-4555-8555-555555555555", "2031-09-14")))))
                .execute()
                .errors()
                .satisfy(errs ->
                {
                    assertFalse(errs.isEmpty(), "expected an error for " + allocatableId);
                    errs.forEach(e -> observable.append(e.getMessage())
                            .append('|').append(e.getExtensions()).append('\n'));
                });
        return observable.toString();
    }

    private static final String MOVE_QUERY = """
            query ($input: MoveCheckInput!) {
              moveChecks(input: $input) { code args severity }
            }
            """;

    private List<String> moveCodes(Map<String, Object> input)
    {
        return codes(tester.document(MOVE_QUERY).variable("input", input).execute()
                .path("moveChecks")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get());
    }

    /**
     * PRD 105 — the drag path. A move the user has only dragged is checked against the state the
     * move verb WOULD store: dropping an event onto a slot where its own resource is already busy
     * reports CONFLICT, and the client never had to rebuild the move payload to ask.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void draggingOntoAnOccupiedSlotReportsTheConflictBeforeTheMove() throws Exception
    {
        org.rapla.entities.domain.Allocatable room = facade.getAllocatables()[0];
        String blocker = "e8888888-8888-4888-8888-888888888881";
        tester.document("""
                mutation ($input: CreateReservationInput!) { createReservation(input: $input) { id } }
                """)
                .variable("input", Map.of("id", blocker, "typeKey", "event",
                        "classification", Map.of("event", Map.of("name", "Blockierer")),
                        "appointments", List.of(Map.of("id", "a8888888-8888-4888-8888-888888888881",
                                "start", "2031-10-05T10:00:00", "end", "2031-10-05T11:00:00",
                                "allDay", false)),
                        "allocations", List.of(Map.of("allocatableId", room.getId()))))
                .execute().path("createReservation.id").entity(String.class).isEqualTo(blocker);

        String mover = "e8888888-8888-4888-8888-888888888882";
        String moverAppointment = "a8888888-8888-4888-8888-888888888882";
        tester.document("""
                mutation ($input: CreateReservationInput!) { createReservation(input: $input) { id } }
                """)
                .variable("input", Map.of("id", mover, "typeKey", "event",
                        "classification", Map.of("event", Map.of("name", "Verschieber")),
                        "appointments", List.of(Map.of("id", moverAppointment,
                                "start", "2031-10-06T10:00:00", "end", "2031-10-06T11:00:00",
                                "allDay", false)),
                        "allocations", List.of(Map.of("allocatableId", room.getId()))))
                .execute().path("createReservation.id").entity(String.class).isEqualTo(mover);

        // onto the blocked day → CONFLICT; a day further → nothing
        assertTrue(moveCodes(Map.of("appointmentId", moverAppointment,
                        "target", Map.of("day", "2031-10-05"))).contains("CONFLICT"),
                "dragging onto the occupied day must report the clash");
        assertFalse(moveCodes(Map.of("appointmentId", moverAppointment,
                        "target", Map.of("day", "2031-10-07"))).contains("CONFLICT"),
                "a free day must not");
    }

    /** The gesture is exclusive: appointment OR reservations, never both, never neither. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void aMoveCheckWithoutAGestureIsRejected()
    {
        tester.document(MOVE_QUERY)
                .variable("input", Map.of("target", Map.of("day", "2031-10-05")))
                .execute()
                .errors()
                .satisfy(errs -> assertTrue(errs.toString().contains("exactly one"), errs.toString()));
    }

    /**
     * PRD 105 D10 — a CONFLICT must name WHAT it clashes with, and it must do so ON the finding:
     * the sheet path and the drag path render the same dialog, so evidence fetched separately by
     * one of them (the first attempt) leaves the other showing a bare "erzeugt Konflikte".
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void aConflictFindingCarriesTheClashingBooking() throws Exception
    {
        org.rapla.entities.domain.Allocatable room = facade.getAllocatables()[0];
        String blocker = "e6666666-6666-4666-8666-666666666661";
        tester.document("""
                mutation ($input: CreateReservationInput!) { createReservation(input: $input) { id } }
                """)
                .variable("input", Map.of("id", blocker, "typeKey", "event",
                        "classification", Map.of("event", Map.of("name", "Belegung Alpha")),
                        "appointments", List.of(Map.of("id", "a6666666-6666-4666-8666-666666666661",
                                "start", "2031-11-03T10:00:00", "end", "2031-11-03T11:00:00",
                                "allDay", false)),
                        "allocations", List.of(Map.of("allocatableId", room.getId()))))
                .execute().path("createReservation.id").entity(String.class).isEqualTo(blocker);

        List<Map<String, Object>> warnings = check(Map.of("draft",
                draft("Kollidierer", List.of(Map.of("allocatableId", room.getId())),
                        List.of(appointment("a6666666-6666-4666-8666-666666666662", "2031-11-03")))));

        Map<String, Object> conflict = warnings.stream()
                .filter(w -> "CONFLICT".equals(w.get("code"))).findFirst()
                .orElseThrow(() -> new AssertionError("expected a CONFLICT, got " + codes(warnings)));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) conflict.get("conflicts");
        assertFalse(evidence.isEmpty(), "the finding must carry its clashing bookings");
        assertEquals("Belegung Alpha",
                ((Map<?, ?>) evidence.get(0).get("reservation2")).get("name"));
        assertNotNull(((Map<?, ?>) evidence.get(0).get("allocatable")).get("name"));

        // a finding that is not about conflicts carries no evidence — the list is not a dumping ground
        Map<String, Object> other = warnings.stream()
                .filter(w -> !"CONFLICT".equals(w.get("code"))).findFirst().orElse(null);
        if (other != null) assertEquals(List.of(), other.get("conflicts"));
    }
}
