package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test for the springdoc-openapi integration (added in PRD 026 setup).
 * Verifies that:
 * <ol>
 *   <li>{@code /v3/api-docs} is publicly reachable (no JWT required —
 *       SecurityConfig whitelists it).</li>
 *   <li>The OpenAPI document discovers the {@code @RestController}s we
 *       care about: calendar view, reservation-edit pre-checks, admin
 *       panels.</li>
 * </ol>
 * If springdoc-openapi-starter is ever removed or its discovery breaks
 * (e.g. {@code @SpringBootApplication} stops scanning a package), this
 * test fails loudly — important because the Angular client (PRD 026)
 * generates its TypeScript client from this endpoint.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@Tag("e2e")
class OpenApiSmokeTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OpenApiSmokeTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;

    @Test
    void apiDocsIsPubliclyReachable() throws Exception
    {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocsListsCalendarViewEndpoint() throws Exception
    {
        String body = fetchApiDocs();
        assertContainsPath(body, "/calendar/view");
    }

    @Test
    void apiDocsListsReservationEditEndpoints() throws Exception
    {
        String body = fetchApiDocs();
        assertContainsPath(body, "/edit/validate-recurrence");
        assertContainsPath(body, "/edit/check-conflicts");
        assertContainsPath(body, "/edit/expand-blocks");
    }

    @Test
    void apiDocsListsAdminPanelsEndpoints() throws Exception
    {
        String body = fetchApiDocs();
        // PRD 020 admin panels — established surface, should also be there.
        assertContainsPath(body, "/admin/panels");
    }

    @Test
    void apiDocsListsAuthEndpoint() throws Exception
    {
        String body = fetchApiDocs();
        assertContainsPath(body, "/auth/login");
    }

    private String fetchApiDocs() throws Exception
    {
        MvcResult mvc = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return mvc.getResponse().getContentAsString();
    }

    /** Asserts that the OpenAPI JSON contains the given path. Uses a
     *  simple substring check on the JSON-encoded key (the path
     *  appears as a JSON object key inside {@code paths: { … }}). */
    private static void assertContainsPath(String openApiJson, String path)
    {
        // springdoc emits paths as JSON keys: "/edit/validate-recurrence":
        String needle = "\"" + path + "\"";
        assertTrue(openApiJson.contains(needle),
                "OpenAPI spec must list " + path + " — Angular client (PRD 026) "
                + "depends on this discovery. The spec returned (truncated):\n"
                + openApiJson.substring(0, Math.min(500, openApiJson.length())));
    }
}
