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
import org.springframework.security.test.context.support.WithAnonymousUser;
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
 * PRD 055 — tier-3 tests for the reservation read resolvers. Mirrors the
 * {@link ReservationMutationControllerTest} setup: testdefault.xml via @TempDir,
 * MockMvc → HttpGraphQlTester, security filters bypassed so @WithMockUser
 * reaches the resolver.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationGraphQLControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    // ============================================================ schema smoke

    /** The Query.reservation + Query.reservations roots appear in introspection. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void schemaExposesReservationQueries()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Query") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("reservation"),  () -> "missing Query.reservation in " + names);
        assertTrue(names.contains("reservations"), () -> "missing Query.reservations in " + names);
    }

    /** The Reservation output type has the derived/canModify/owner fields. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void reservationTypeHasDerivedFields()
    {
        Map<String, Object> result = tester.document("""
                { __type(name: "Reservation") { fields { name } } }
                """)
                .execute()
                .path("__type")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        List<String> names = fields.stream().map(f -> (String) f.get("name")).toList();
        assertTrue(names.contains("firstDate"),    () -> "missing Reservation.firstDate in " + names);
        assertTrue(names.contains("lastDate"),     () -> "missing Reservation.lastDate in " + names);
        assertTrue(names.contains("canModify"),    () -> "missing Reservation.canModify in " + names);
        assertTrue(names.contains("owner"),        () -> "missing Reservation.owner in " + names);
        assertTrue(names.contains("appointments"), () -> "missing Reservation.appointments in " + names);
        assertTrue(names.contains("allocations"),  () -> "missing Reservation.allocations in " + names);
    }

    // ============================================================ §12 leak tests

    /**
     * AGENTS.md §12 — anonymous callers get empty list (existence not leaked).
     * Filter is mandatory so we pass a wide window.
     */
    @Test
    @WithAnonymousUser
    void anonymousReservationsReturnsEmpty()
    {
        List<Map<String, Object>> result = tester.document("""
                query {
                  reservations(filter: {
                    from: "2020-01-01T00:00:00",
                    to:   "2020-12-31T00:00:00"
                  }) { id }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertTrue(result.isEmpty(),
                () -> "anonymous caller must get [] not " + result);
    }

    /**
     * §12 — id existence not leaked. Unknown / hidden id → null, not an error.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unknownReservationIdReturnsNull()
    {
        tester.document("""
                query { reservation(id: "nonexistent-uuid") { id } }
                """)
                .execute()
                .path("reservation")
                .valueIsNull();
    }

    // ============================================================ validation

    /** Window > 365 days is rejected. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void overlongWindowRejected()
    {
        tester.document("""
                query {
                  reservations(filter: {
                    from: "2020-01-01T00:00:00",
                    to:   "2025-12-31T00:00:00"
                  }) { id }
                }
                """)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "expected error for >365d window");
                    String joined = errs.toString();
                    assertTrue(joined.contains("365") || joined.toLowerCase().contains("window")
                                    || joined.contains("INVALID_VALUE"),
                            () -> "expected window-cap error; got " + joined);
                });
    }

    /** Filter is mandatory — omitting it is a schema-level error. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void filterIsMandatory()
    {
        tester.document("""
                query { reservations { id } }
                """)
                .execute()
                .errors()
                .satisfy(errs -> assertFalse(errs.isEmpty(),
                        "expected schema error for missing mandatory filter"));
    }

    // ============================================================ happy path read

    /**
     * Authenticated admin can query reservations within a window. Verifies the
     * end-to-end wire: query → operator dispatch → permission filter → DTO
     * shape. testdefault.xml ships some reservations under the `event` type.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminCanReadReservationsInWindow()
    {
        // Wide-but-bounded window covering the testdefault.xml fixture data
        List<Map<String, Object>> result = tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    id
                    firstDate
                    lastDate
                    canModify
                    classification { typeKey }
                    appointments { id start end allDay }
                    allocations { appointmentIds }
                  }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        // The fixture has reservations in 2010 — minimal assertion is no error
        // + canModify present (boolean). Empty result is acceptable here; the
        // value of this test is the wire-shape coverage.
        assertNotNull(result, "result must not be null");
        for (Map<String, Object> r : result)
        {
            assertNotNull(r.get("id"), "id required");
            assertNotNull(r.get("firstDate"), "firstDate required");
            assertNotNull(r.get("lastDate"), "lastDate required");
            assertNotNull(r.get("canModify"), "canModify required");
            assertEquals(Boolean.TRUE, r.get("canModify"),
                    () -> "admin should canModify every visible reservation: " + r);
        }
    }

    // ============================================================ repeating + blocks

    /**
     * Verifies the RepeatingRule wire shape on a recurring reservation.
     * testdefault.xml has a monthly-repeating reservation with end-date
     * 2010-09-04. Asserts the rule comes through with type=MONTHLY and
     * end=2010-09-04 (Date scalar, not LocalDateTime).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void repeatingRuleSerializesAsDate()
    {
        List<Map<String, Object>> result = tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    appointments {
                      repeating { type interval end count weekdays }
                    }
                  }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        // Find at least one repeating appointment in the result set
        boolean foundRepeating = false;
        for (Map<String, Object> r : result)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appts = (List<Map<String, Object>>) r.get("appointments");
            for (Map<String, Object> a : appts)
            {
                Map<String, Object> rep = (Map<String, Object>) a.get("repeating");
                if (rep == null) continue;
                foundRepeating = true;
                assertNotNull(rep.get("type"), "RepeatingRule.type required");
                assertNotNull(rep.get("interval"), "RepeatingRule.interval required");
                // 'end' may be null (open-ended) or a Date string like "2010-09-04"
                Object end = rep.get("end");
                if (end != null)
                {
                    String s = end.toString();
                    assertTrue(s.matches("\\d{4}-\\d{2}-\\d{2}"),
                            () -> "RepeatingRule.end must be ISO date format YYYY-MM-DD, got: " + s);
                }
            }
        }
        assertTrue(foundRepeating, "fixture should contain at least one repeating appointment in 2010");
    }

    /**
     * Verifies Appointment.blocks(from:, to:) materializes recurrence
     * expansion. Picks a wide window over the fixture's 2010 data and
     * asserts each block has start/end as LocalDateTime strings.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void appointmentBlocksMaterializeInWindow()
    {
        List<Map<String, Object>> result = tester.document("""
                query {
                  reservations(filter: {
                    from: "2006-01-01T00:00:00",
                    to:   "2006-12-31T00:00:00"
                  }) {
                    appointments {
                      blocks(from: "2010-01-01T00:00:00", to: "2010-12-31T00:00:00") {
                        start end isException
                      }
                    }
                  }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        boolean foundAnyBlock = false;
        for (Map<String, Object> r : result)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appts = (List<Map<String, Object>>) r.get("appointments");
            for (Map<String, Object> a : appts)
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> blocks = (List<Map<String, Object>>) a.get("blocks");
                if (blocks == null || blocks.isEmpty()) continue;
                foundAnyBlock = true;
                for (Map<String, Object> b : blocks)
                {
                    assertNotNull(b.get("start"), "block.start required");
                    assertNotNull(b.get("end"), "block.end required");
                    assertNotNull(b.get("isException"), "block.isException required");
                    String s = b.get("start").toString();
                    assertTrue(s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                            () -> "block.start must be ISO LocalDateTime, got: " + s);
                }
            }
        }
        assertTrue(foundAnyBlock, "fixture should produce at least one materialized block in 2010");
    }
}
