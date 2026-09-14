package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 086 window-first — for a FULL ADMIN with no explicit scope, {@code reservations(filter:)} is
 * served from the global interval index (one window lookup) instead of the ~48k-allocatable
 * resource-first scan. This must be behaviour-identical: the flipped (window-first) result MUST equal
 * the legacy (resource-first) result. Drives the real operator over testdefault.xml; flips the
 * read-model flag around the two calls and asserts the reservation-id sets match.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationWindowFirstFlipTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ReservationWindowFirstFlipTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    org.rapla.storage.StorageOperator operator;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate())
                .url("/api/graphql")
                .build();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminUnscopedWindowFirstEqualsLegacyResourceFirst()
    {
        org.rapla.storage.impl.server.LocalAbstractCachableOperator op =
                (org.rapla.storage.impl.server.LocalAbstractCachableOperator) operator;
        boolean original = op.isReadModelAuthoritative();
        try
        {
            // Resource-first (flag off): the legacy ~48k-allocatable path.
            op.setReadModelAuthoritative(false);
            List<String> legacy = reservationIds();
            // Window-first (flag on): the global interval-index path (admin + unscoped).
            op.setReadModelAuthoritative(true);
            List<String> windowFirst = reservationIds();

            assertTrue(!legacy.isEmpty(), "fixture must have reservations in the window for a meaningful test");
            assertEquals(legacy, windowFirst,
                    () -> "window-first must equal resource-first for an unscoped admin; legacy=" + legacy + " windowFirst=" + windowFirst);
        }
        finally
        {
            op.setReadModelAuthoritative(original);
        }
    }

    /** Unscoped, wide window — admin sees every reservation; returns the sorted id set. */
    private List<String> reservationIds()
    {
        return tester.document("""
                { reservations(filter: { from: "2000-01-01T00:00:00", to: "2100-01-01T00:00:00", limit: 5000 }) { id } }
                """)
                .execute()
                .path("reservations[*].id")
                .entityList(String.class)
                .get()
                .stream()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }
}
