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
        // Activate the OpenApiFieldDiscoveryFixture controller (gated behind
        // this property so it's invisible in production).
        registry.add("rapla.test.swagger-fixture-enabled", () -> "true");
    }

    @Autowired MockMvc mockMvc;

    @Test
    void apiDocsIsPubliclyReachable() throws Exception
    {
        mockMvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocsListsCalendarViewEndpoint() throws Exception
    {
        String body = fetchApiDocs();
        assertContainsPath(body, "/api/calendar/view");
    }

    @Test
    void apiDocsListsReservationEditEndpoints() throws Exception
    {
        String body = fetchApiDocs();
        assertContainsPath(body, "/api/edit/validate-recurrence");
        assertContainsPath(body, "/api/edit/check-conflicts");
        assertContainsPath(body, "/api/edit/expand-blocks");
    }

    @Test
    void apiDocsListsAdminPanelsEndpoints() throws Exception
    {
        String body = fetchApiDocs();
        // PRD 020 admin panels — established surface, should also be there.
        assertContainsPath(body, "/api/admin/panels");
    }

    @Test
    void apiDocsListsAuthEndpoint() throws Exception
    {
        String body = fetchApiDocs();
        // PRD 041: rapla-custom /api/auth/login deleted; login now via OAuth2-standard
        // /oauth2/token grant_type=password. /api/auth/oauth/config (discovery) remains.
        assertContainsPath(body, "/api/auth/oauth/config");
    }

    @Test
    void schemaUsesFieldBasedIntrospection_strictFixture() throws Exception
    {
        // The cleanest possible test of the SwaggerJacksonConfig wiring:
        // a hand-rolled fixture POJO whose field set DIFFERS from its getter
        // and setter set. With field-based discovery the schema must equal
        // the field set exactly; with getter/setter-based discovery (the
        // default) it would equal a different set. If this assertion fails,
        // SwaggerJacksonConfig's ModelResolver registration regressed — see
        // OpenApiFieldDiscoveryFixture for the fixture's documented contract.
        com.fasterxml.jackson.databind.JsonNode doc = parseApiDocs();
        com.fasterxml.jackson.databind.JsonNode fixtureSchema = doc
                .path("components").path("schemas").path("OpenApiFieldDiscoveryFixture");
        assertTrue(!fixtureSchema.isMissingNode(),
                "OpenApiFieldDiscoveryFixture schema missing from /api/v3/api-docs — "
                        + "either the test fixture controller didn't register "
                        + "(check rapla.test.swagger-fixture-enabled) or SpringDoc "
                        + "didn't discover it. Schemas present: "
                        + doc.path("components").path("schemas").fieldNames());

        com.fasterxml.jackson.databind.JsonNode props = fixtureSchema.path("properties");
        java.util.Set<String> propNames = new java.util.TreeSet<>();
        props.fieldNames().forEachRemaining(propNames::add);

        java.util.Set<String> expected = new java.util.TreeSet<>(java.util.List.of(
                "aField", "primitive", "mismatchedField"));
        org.junit.jupiter.api.Assertions.assertEquals(expected, propNames,
                "OpenApiFieldDiscoveryFixture schema property set must equal {aField, "
                        + "primitive, mismatchedField} — exactly the private fields, "
                        + "excluding transient + getter-only + setter-only properties. "
                        + "Actual: " + propNames + ". If a getter-only or setter-only "
                        + "name appeared, SwaggerJacksonConfig's visibility settings "
                        + "drifted (GETTER/SETTER must be NONE). If transient leaked, "
                        + "PROPAGATE_TRANSIENT_MARKER isn't set. See "
                        + "rapla-app/.../SwaggerJacksonConfig.java.");

        // Property TYPES — field-based discovery must use the FIELD's type,
        // not the getter's. swagger-core #1611 territory.
        String mismatchedType = props.path("mismatchedField").path("type").asText();
        org.junit.jupiter.api.Assertions.assertEquals("string", mismatchedType,
                "mismatchedField's schema type must be 'string' (the field type), "
                        + "not 'integer' (the getter return type). If integer appeared, "
                        + "swagger-core is consulting getters despite GETTER=NONE — "
                        + "this is the swagger-core #1611 regression and the SPA's "
                        + "generated TypeScript client will silently mis-parse the wire.");
    }

    @Test
    void schemaUsesFieldBasedIntrospection() throws Exception
    {
        // PRD 031 follow-up: SwaggerJacksonConfig registers a swagger-core
        // ModelResolver that mirrors JacksonObjectMapperFactory's visibility
        // rules (field-based, getters/setters NONE, transient excluded).
        // Without it the generated TypeScript client lies about the wire
        // shape (see docs/architecture/rest-api.md §"OpenAPI / Swagger spec
        // caveat"). Regression markers below pin canonical entity schemas
        // so a future change that breaks the resolver wiring fails fast.
        com.fasterxml.jackson.databind.JsonNode doc = parseApiDocs();
        com.fasterxml.jackson.databind.JsonNode schemas = doc.path("components").path("schemas");

        // ReservationImpl serializes the appointment list + classification +
        // links etc. as private fields. With field-based discovery it must
        // have >= 5 properties. With swagger-core defaults (getter-based)
        // the count would differ — typically lower for impl types because
        // many getters live on the parent interface, not the impl.
        int reservationProps = schemas.path("ReservationImpl").path("properties").size();
        assertTrue(reservationProps >= 5,
                "ReservationImpl schema must expose >=5 properties. Found: "
                        + reservationProps + ". If 0, SwaggerJacksonConfig's "
                        + "ModelResolver isn't registered or addConverter() didn't run.");

        // DefaultConfiguration: three overloaded setValue(...) methods.
        // Field-based discovery sees the private `value` field once —
        // a single `value` property in the schema, no setter-conflict path.
        com.fasterxml.jackson.databind.JsonNode dcProps = schemas.path("DefaultConfiguration").path("properties");
        assertTrue(dcProps.has("value"),
                "DefaultConfiguration must expose the `value` field via field-based "
                        + "discovery. Found properties: " + dcProps.fieldNames());

        // AllocatableImpl: same pattern as ReservationImpl. >=5 fields
        // (classification, permissions, annotations, lastChanged, createDate, …).
        int allocProps = schemas.path("AllocatableImpl").path("properties").size();
        assertTrue(allocProps >= 5,
                "AllocatableImpl schema must expose >=5 properties. Found: "
                        + allocProps);
    }

    private String fetchApiDocs() throws Exception
    {
        MvcResult mvc = mockMvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return mvc.getResponse().getContentAsString();
    }

    private com.fasterxml.jackson.databind.JsonNode parseApiDocs() throws Exception
    {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(fetchApiDocs());
    }

    /** Asserts that the OpenAPI JSON contains the given path. Uses a
     *  simple substring check on the JSON-encoded key (the path
     *  appears as a JSON object key inside {@code paths: { … }}). */
    private static void assertContainsPath(String openApiJson, String path)
    {
        // springdoc emits paths as JSON keys: "/api/edit/validate-recurrence":
        String needle = "\"" + path + "\"";
        assertTrue(openApiJson.contains(needle),
                "OpenAPI spec must list " + path + " — Angular client (PRD 026) "
                + "depends on this discovery. The spec returned (truncated):\n"
                + openApiJson.substring(0, Math.min(500, openApiJson.length())));
    }
}
