package org.rapla.server.spring.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * PRD 074 §"Window and inputs directives" / PRD 097 OQ9 — the {@code @param} gate on the document
 * URL surface. A document accepts exactly the public inputs its view declares:
 *
 * <ul>
 *   <li>an undeclared query parameter → 400 (reject-undeclared),</li>
 *   <li>a declared {@code @param} maps public {@code name} → private {@code into} path,</li>
 *   <li>{@code ?from=/?to=} are accepted only when the view declares {@code @window},</li>
 *   <li>a {@code required} param must be filled from SOMEWHERE (URL, document pins; preview also
 *       derives from the view's example defaultVariables) — else a hint page, not a query,</li>
 *   <li>a view's stored defaultVariables are authoring example data and never scope a render,</li>
 *   <li>§12: an unreadable/unresolvable id in a declared param yields an EMPTY render, never an
 *       error that confirms the id exists.</li>
 * </ul>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentParamGateTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = DocumentParamGateTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    private static final String WIDE_WINDOW =
            "{\"filter\":{\"from\":\"2000-01-01T00:00:00\",\"to\":\"2035-01-01T00:00:00\"}}";
    private static final String LIST_TEMPLATE =
            "<ul>{{#reservations}}<li>{{titel}}</li>{{/reservations}}</ul>";

    @Autowired MockMvc mockMvc;
    @Autowired ViewCatalogService views;
    @Autowired DocumentCatalogService documents;
    @Autowired DocumentRenderService renderService;
    @Autowired StorageOperator operator;

    @BeforeEach
    void seed() throws Exception
    {
        User admin = operator.getUser("homer");

        // A scoped view: public `resource` fills the private filter.allocatableIdsIn.
        String scoped = """
                query pg_scoped($filter: ReservationFilter!) @view(title: "Scoped")
                  @param(name: "resource", into: "filter.allocatableIdsIn")
                { reservations(filter: $filter) { titel: name } }""";
        assertEquals(List.of(), views.saveView("pg_scoped", scoped, true, List.of(), WIDE_WINDOW, admin));
        assertEquals(List.of(), documents.save("pg_scoped_doc", "pg_scoped", LIST_TEMPLATE,
                true, List.of(), WIDE_WINDOW, admin));

        // A required-param view (the Leihschein shape).
        String required = """
                query pg_required($filter: ReservationFilter!) @view(title: "Required")
                  @param(name: "resource", into: "filter.allocatableIdsIn", required: true)
                { reservations(filter: $filter) { titel: name } }""";
        assertEquals(List.of(), views.saveView("pg_required", required, true, List.of(), WIDE_WINDOW, admin));
        assertEquals(List.of(), documents.save("pg_required_doc", "pg_required", LIST_TEMPLATE,
                true, List.of(), WIDE_WINDOW, admin));

        // A @window view: from/to become URL-overridable (default = current day, i.e. no 2006 rows).
        String windowed = """
                query pg_windowed($filter: ReservationFilter!) @view(title: "Windowed")
                  @window(from: { anchor: TODAY, offset: 0 }, to: { anchor: TODAY, offset: 1 })
                { reservations(filter: $filter) { titel: name } }""";
        assertEquals(List.of(), views.saveView("pg_windowed", windowed, true, List.of(), null, admin));
        assertEquals(List.of(), documents.save("pg_windowed_doc", "pg_windowed", LIST_TEMPLATE,
                true, List.of(), null, admin));
    }

    @Test
    @WithMockUser(username = "homer")
    void anUndeclaredQueryParameterIsRejectedWith400() throws Exception
    {
        assertEquals(400, mockMvc.perform(get("/api/documents/pg_scoped_doc").param("foo", "bar"))
                .andReturn().getResponse().getStatus());
    }

    /** The interim dotted-path convention is gone: a private variable path is not a public name. */
    @Test
    @WithMockUser(username = "homer")
    void aPrivateDottedVariablePathIsRejectedWith400() throws Exception
    {
        assertEquals(400, mockMvc.perform(get("/api/documents/pg_scoped_doc")
                        .param("filter.allocatableIdsIn", "x"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @WithMockUser(username = "homer")
    void aDeclaredParamMapsThePublicNameToTheNestedInput() throws Exception
    {
        // Unscoped: the wide default window has rows.
        String unscoped = mockMvc.perform(get("/api/documents/pg_scoped_doc"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(unscoped.contains("<li>"), unscoped);

        // §12: an id nobody can resolve scopes the query to nothing — an empty document, no error.
        MvcResult result = mockMvc.perform(get("/api/documents/pg_scoped_doc")
                .param("resource", "no-such-resource-id")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("<ul></ul>"),
                result.getResponse().getContentAsString());
    }

    /**
     * 2026-08-11 — a missing {@code required} param is NOT a 404 anymore: the caller already
     * passed the visibility gate (§12 masking fires before the param check), so the document
     * renders a hint page naming the missing public param instead of a query over everything.
     */
    @Test
    @WithMockUser(username = "homer")
    void aMissingRequiredParamRendersAHintPageNamingTheParam() throws Exception
    {
        MvcResult missingParam = mockMvc.perform(get("/api/documents/pg_required_doc")).andReturn();
        assertEquals(200, missingParam.getResponse().getStatus());
        String html = missingParam.getResponse().getContentAsString();
        assertTrue(html.contains("resource"), html);
        assertTrue(html.contains("rapla-missing-param"), html);

        // The CSV twin stays machine-shaped: no hint body, the masked 404.
        assertEquals(404, mockMvc.perform(get("/api/documents/pg_required_doc/csv"))
                .andReturn().getResponse().getStatus());
    }

    /**
     * 2026-08-11 — {@code required} means "this scope must come from SOMEWHERE", not "the URL
     * must carry it": a document pinning the param's target path in its own defaultVariables
     * satisfies the requirement.
     */
    @Test
    @WithMockUser(username = "homer")
    void aRequiredParamIsSatisfiedByDocumentPins() throws Exception
    {
        User admin = operator.getUser("homer");
        String pins = "{\"filter\":{\"from\":\"2000-01-01T00:00:00\",\"to\":\"2035-01-01T00:00:00\","
                + "\"allocatableIdsIn\":[\"no-such-resource-id\"]}}";
        assertEquals(List.of(), documents.save("pg_pinned_doc", "pg_required", LIST_TEMPLATE,
                true, List.of(), pins, admin));

        MvcResult result = mockMvc.perform(get("/api/documents/pg_pinned_doc")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("<ul></ul>"), html);
        assertTrue(!html.contains("rapla-missing-param"), html);
    }

    /**
     * 2026-08-11 — a view's stored defaultVariables are GraphiQL EXAMPLE data, not runtime pins:
     * an example {@code allocatableIdsIn} saved from the variables pane must not scope the live
     * document render (it previously deep-merged underneath and silently filtered everything).
     */
    @Test
    @WithMockUser(username = "homer")
    void viewDefaultVariablesAreExampleDataAndDoNotScopeTheRender() throws Exception
    {
        User admin = operator.getUser("homer");
        String exampleDefaults = "{\"filter\":{\"from\":\"2000-01-01T00:00:00\","
                + "\"to\":\"2035-01-01T00:00:00\",\"allocatableIdsIn\":[\"no-such-resource-id\"]}}";
        String scoped = """
                query pg_example($filter: ReservationFilter!) @view(title: "Example")
                  @param(name: "resource", into: "filter.allocatableIdsIn")
                { reservations(filter: $filter) { titel: name } }""";
        assertEquals(List.of(), views.saveView("pg_example", scoped, true, List.of(), exampleDefaults, admin));
        assertEquals(List.of(), documents.save("pg_example_doc", "pg_example", LIST_TEMPLATE,
                true, List.of(), WIDE_WINDOW, admin));

        String html = mockMvc.perform(get("/api/documents/pg_example_doc"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("<li>"), "the example scope must not filter the live render: " + html);
    }

    /**
     * 2026-08-11 — the authoring preview derives param defaults from the view's example
     * defaultVariables THROUGH the declared {@code @param} holes only: the example fills the
     * required param (preview renders), but an example key that corresponds to no declared
     * param does not leak into the resolved variables.
     */
    @Test
    @WithMockUser(username = "homer")
    void previewDerivesParamDefaultsFromExampleDataThroughDeclaredParamsOnly() throws Exception
    {
        User admin = operator.getUser("homer");
        String exampleDefaults = "{\"filter\":{\"allocatableIdsIn\":[\"no-such-resource-id\"],"
                + "\"nameContains\":\"not-a-param\"}}";
        assertEquals(List.of(), views.saveView("pg_required", """
                query pg_required($filter: ReservationFilter!) @view(title: "Required")
                  @param(name: "resource", into: "filter.allocatableIdsIn", required: true)
                { reservations(filter: $filter) { titel: name } }""",
                true, List.of(), exampleDefaults, admin));

        DocumentRenderService.Preview preview = renderService
                .preview("pg_required", LIST_TEMPLATE, null, null, Map.of(), admin).orElseThrow();
        Map<String, Object> filter = filterOf(preview.variables());
        assertEquals(List.of("no-such-resource-id"), filter.get("allocatableIdsIn"),
                "the example fills the declared param: " + preview.variables());
        assertEquals(null, filter.get("nameContains"),
                "example keys without a declared param must not apply: " + preview.variables());
        assertTrue(!preview.html().contains("rapla-missing-param"), preview.html());
    }

    /** 2026-08-11 — no source at all for a required param: the preview shows the same hint page. */
    @Test
    @WithMockUser(username = "homer")
    void previewWithoutAnySourceForARequiredParamShowsTheHint() throws Exception
    {
        User admin = operator.getUser("homer");
        DocumentRenderService.Preview preview = renderService
                .preview("pg_required", LIST_TEMPLATE, null, null, Map.of(), admin).orElseThrow();
        assertTrue(preview.html().contains("rapla-missing-param"), preview.html());
        assertTrue(preview.html().contains("resource"), preview.html());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> filterOf(Map<String, Object> variables)
    {
        return variables.get("filter") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
    }

    @Test
    @WithMockUser(username = "homer")
    void aProvidedRequiredParamRenders() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/documents/pg_required_doc")
                .param("resource", "no-such-resource-id")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
    }

    @Test
    @WithMockUser(username = "homer")
    void fromToOverrideTheWindowWhenTheViewDeclaresWindow() throws Exception
    {
        // Default window = today (no fixture rows) → empty.
        String today = mockMvc.perform(get("/api/documents/pg_windowed_doc"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(today.contains("<ul></ul>"), today);

        // ?from/?to widen to the fixture era → rows appear.
        String widened = mockMvc.perform(get("/api/documents/pg_windowed_doc")
                        .param("from", "2000-01-01T00:00:00")
                        .param("to", "2035-01-01T00:00:00"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(widened.contains("<li>"), widened);
    }

    @Test
    @WithMockUser(username = "homer")
    void fromToAreRejectedWithoutAWindowDirective() throws Exception
    {
        assertEquals(400, mockMvc.perform(get("/api/documents/pg_scoped_doc")
                        .param("from", "2000-01-01T00:00:00"))
                .andReturn().getResponse().getStatus());
    }
}
