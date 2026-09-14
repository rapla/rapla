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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code compute(expr:)} contract: an expression the entity cannot satisfy yields {@code null}, never
 * a GraphQL error. Referencing an attribute the type does not have ({@code attribute(p,"loan")} on a
 * type without {@code loan}) threw {@code NullPointerException: Attribute can't be null} out of the
 * expression engine and surfaced as one INTERNAL_ERROR per row (Siegen data, 2026-09-02) — the same
 * seam that already maps an invalid expr to null must map this to null too.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ComputeMissingAttributeGraphQLTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ComputeMissingAttributeGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        tester = HttpGraphQlTester.builder(MockMvcWebTestClient.bindTo(mockMvc).build().mutate())
                .url("/api/graphql").build();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void computeOverAMissingAttributeIsNullWithoutErrors()
    {
        List<Map<String, Object>> rows = tester.document("""
                query {
                  reservations(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
                    appointments {
                      missing: compute(expr: "{p->name(attribute(p,\\"nosuchattribute\\"))}")
                    }
                  }
                }
                """)
                .execute()
                .errors().verify()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {}).get();
        assertFalse(rows.isEmpty(), "fixture should have reservations in 2006");
        for (Map<String, Object> r : rows)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appointments = (List<Map<String, Object>>) r.get("appointments");
            for (Map<String, Object> a : appointments)
            {
                assertNull(a.get("missing"), () -> "missing attribute must compute to null: " + a);
            }
        }
    }
}
