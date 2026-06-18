package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Verifies that the hard-constraint URL paths (PRD URL Path Preservation)
 * actually resolve to a Spring controller — they must not return 404.
 *
 * <p>The endpoints may return 4xx (auth/not-found) or 5xx (server config),
 * but anything other than 404 proves Spring routed the path to the controller
 * correctly. The PRD constraint is "the path must continue to resolve" —
 * not "every request must succeed".
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
// In test scope SpringDoc is on the classpath and owns the api-docs URL (its
// OpenApiWebMvcResource, not StaticOpenApiController). Pin it to the prod path
// /api/v3/api-docs so apiDocsRoutes() probes the same URL the production runtime
// serves — same property the OpenApi spec-capture tests set.
@TestPropertySource(properties = "springdoc.api-docs.path=/api/v3/api-docs")
class UrlPreservationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = UrlPreservationTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    MockMvc mockMvc;

    @Test
    void icalPathRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/rapla/ical").param("file", "x").param("user", "homer"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, status, "/rapla/ical must route — got 404");
    }

    @Test
    void internalIcalPathRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/rapla/internal_ical").param("file", "x").param("user", "homer"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, status, "/rapla/internal_ical must route — got 404");
    }

    @Test
    void calendarPathRoutes() throws Exception
    {
        Object handler = mockMvc.perform(get("/rapla/calendar").param("user", "homer").param("file", "x"))
                .andReturn().getHandler();
        assertNotNull(handler, "/rapla/calendar must route to a controller — got null handler");
    }

    @Test
    void calendarCsvPathRoutes() throws Exception
    {
        Object handler = mockMvc.perform(get("/rapla/calendar.csv").param("user", "homer").param("file", "x"))
                .andReturn().getHandler();
        assertNotNull(handler, "/rapla/calendar.csv must route to a controller — got null handler");
    }

    @Test
    void raplaclientJnlpPathRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/raplaclient.jnlp"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, status, "/raplaclient.jnlp must route — got 404");
    }

    // ---- PRD 031 namespace coverage ----

    @Test
    void indexPathRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, status, "/ (chooser landing) must route — got 404");
    }

    @Test
    void indexAliasRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/index"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, status, "/index must route — got 404");
    }

    @Test
    void appShellRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/app/"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, status, "/app/ (SPA shell) must route — got 404 (verify SpaResourceConfig + ng build artifacts)");
    }

    // PRD 041: removed /api/auth/login URL-preservation test; /oauth2/token is
    // wired via Spring AS's filter chain, not as a @RestController, so MockMvc's
    // simple GET-probe pattern doesn't apply. UnifiedRefreshIntegrationTest +
    // RemoteStorageErrorMappingIntegrationTest cover the actual POST flow.

    @Test
    void apiStorageResourcesRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/api/storage/resources"))
                .andReturn().getResponse().getStatus();
        // 401 expected (no Bearer); 404 means route doesn't exist.
        assertNotEquals(404, status, "/api/storage/resources must route — got 404");
    }

    @Test
    void apiDocsRoutes() throws Exception
    {
        int status = mockMvc.perform(get("/api/v3/api-docs"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, status, "/api/v3/api-docs must route (SpringDoc honours /api/ prefix) — got 404");
    }
}
