package org.rapla.server.spring.document;

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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * PRD 097 Phase 2 — the mandatory §12 leak test for {@code /api/documents}.
 *
 * <p>A document can be hidden from a caller in three ways: it is not public and the caller is in
 * none of its groups; the <i>view</i> it renders is hidden; it does not exist at all. All three
 * must produce the identical response — otherwise probing {@code /api/documents/<guess>} enumerates
 * the catalog, which is exactly the existence leak §12 forbids.
 *
 * <p>Fixture (testdefault.xml): homer is the admin (may author documents), monty is not.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerLeakTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = DocumentControllerLeakTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    private static final String PUBLIC_VIEW = "leaktest_public_view";
    private static final String HIDDEN_VIEW = "leaktest_hidden_view";
    private static final String TEMPLATE = "<p id=\"stamp\">{{serverTime}}</p>";

    @Autowired MockMvc mockMvc;
    @Autowired ViewCatalogService views;
    @Autowired DocumentCatalogService documents;
    @Autowired StorageOperator operator;

    @BeforeEach
    void seed() throws Exception
    {
        User admin = operator.getUser("homer");
        String query = "query %s @view(title: \"Leak test\") { serverTime }";
        assertEquals(List.of(), views.saveView(PUBLIC_VIEW, query.formatted(PUBLIC_VIEW), true, List.of(), null, admin));
        assertEquals(List.of(), views.saveView(HIDDEN_VIEW, query.formatted(HIDDEN_VIEW), false, List.of(), null, admin));

        save("leaktest_visible", PUBLIC_VIEW, true, admin);
        save("leaktest_hidden", PUBLIC_VIEW, false, admin);
        save("leaktest_on_hidden_view", HIDDEN_VIEW, true, admin);
    }

    private void save(String name, String viewName, boolean isPublic, User admin) throws Exception
    {
        assertEquals(List.of(), documents.save(name, viewName, TEMPLATE, isPublic, List.of(), null, admin));
    }

    @Test
    @WithMockUser(username = "monty")
    void aVisibleDocumentRendersAsAStandaloneHtmlPage() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/documents/leaktest_visible")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertTrue(body.startsWith("<!doctype html>"), body);
        assertTrue(body.contains("id=\"stamp\""), body);
    }

    /**
     * PRD 097 Phase 3 — a view whose grouping column carries {@code @column(group: true, format:)}
     * renders day sections server-side, exactly as the SPA's grouped mode does: the server emits
     * {@code extensions.view.groupBy}, {@code RowGrouping} buckets the rows, and the template paints
     * {@code {{#groups}}{{label}}}. Nothing in the template names the grouping column.
     */
    @Test
    @WithMockUser(username = "homer")
    void aGroupedViewRendersDaySectionsWithFormattedLabels() throws Exception
    {
        User admin = operator.getUser("homer");
        String grouped = """
                query leaktest_grouped($filter: ReservationFilter!) @view(title: "Gruppiert") {
                  reservations(filter: $filter) {
                    tag: firstDate @column(header: "Tag", order: 1, group: true, format: "EE dd.MM")
                    titel: name @column(header: "Titel", order: 2)
                  }
                }""";
        String window = "{\"filter\":{\"from\":\"2000-01-01T00:00:00\",\"to\":\"2035-01-01T00:00:00\"}}";
        assertEquals(List.of(), views.saveView("leaktest_grouped", grouped, true, List.of(), window, admin));
        assertEquals(List.of(), documents.save("leaktest_grouped_doc", "leaktest_grouped",
                "{{#groups}}<h2>{{label}}</h2>{{#rows}}<p>{{titel}}</p>{{/rows}}{{/groups}}",
                true, List.of(), window, admin));

        String body = mockMvc.perform(get("/api/documents/leaktest_grouped_doc"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("<h2>"), "expected day sections, got:\n" + body);
        // "EE dd.MM" → a German weekday abbreviation, never a raw ISO timestamp
        assertTrue(body.matches("(?s).*<h2>(Mo|Di|Mi|Do|Fr|Sa|So) \\d\\d\\.\\d\\d</h2>.*"),
                "group label must be formatted by the view's groupFormat, got:\n" + body);
    }

    /**
     * PRD 074 §"Window and inputs directives" — a declared {@code @param} maps its public URL key
     * to the nested GraphQL input, which is what makes a per-resource link
     * ({@code ?resource=<id>}) expressible at all. An id nobody can read must still not leak: the
     * view runs in the caller's scope, so a wrong id yields an empty document, never an error that
     * confirms the id exists. (The undotted/undeclared cases live in {@code DocumentParamGateTest}.)
     */
    @Test
    @WithMockUser(username = "homer")
    void aDeclaredParamReachesANestedFilterInput() throws Exception
    {
        User admin = operator.getUser("homer");
        String byResource = """
                query leaktest_by_resource($filter: ReservationFilter!) @view(title: "Nach Ressource")
                  @window(from: { anchor: TODAY, offset: -9999 }, to: { anchor: TODAY, offset: 9999 })
                  @param(name: "resource", into: "filter.allocatableIdsIn") {
                  reservations(filter: $filter) { titel: name }
                }""";
        assertEquals(List.of(), views.saveView("leaktest_by_resource", byResource, true, List.of(), null, admin));
        assertEquals(List.of(), documents.save("leaktest_by_resource_doc", "leaktest_by_resource",
                "<ul>{{#reservations}}<li>{{titel}}</li>{{/reservations}}</ul>", true, List.of(), null, admin));

        MvcResult result = mockMvc.perform(get("/api/documents/leaktest_by_resource_doc")
                .param("resource", "no-such-resource-id")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        // the filter was applied (not ignored): an unknown resource id matches nothing
        assertTrue(result.getResponse().getContentAsString().contains("<ul></ul>"),
                result.getResponse().getContentAsString());
    }

    /**
     * PRD 097 — the CSV twin of the page. It must carry the view's column headers and the rows the
     * page shows, which for a grouped view means the surviving groups only: a duplicate report that
     * exports the singletons it deliberately dropped is worse than no export.
     */
    @Test
    @WithMockUser(username = "homer")
    void theCsvExportCarriesTheViewColumnsAndTheGroupedRows() throws Exception
    {
        User admin = operator.getUser("homer");
        String duplicates = """
                query leaktest_dupes @view(title: "Dubletten") {
                  allocatables {
                    wert: compute(expr: "name()") @column(header: "Wert", order: 1, group: true, minGroupSize: 999)
                    bez: name @column(header: "Bezeichnung", order: 2)
                  }
                }""";
        assertEquals(List.of(), views.saveView("leaktest_dupes", duplicates, true, List.of(), null, admin));
        assertEquals(List.of(), documents.save("leaktest_dupes_doc", "leaktest_dupes",
                "{{#groups}}<h2>{{label}}</h2>{{/groups}}", true, List.of(), null, admin));

        MockHttpServletResponse response = mockMvc.perform(get("/api/documents/leaktest_dupes_doc/csv"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("text/csv"), response.getContentType());
        assertTrue(response.getHeader("Content-Disposition")
                .contains("attachment; filename=\"leaktest_dupes_doc.csv\""),
                response.getHeader("Content-Disposition"));
        String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("Wert,Bezeichnung\r\n"), body);
        // minGroupSize 999: no name occurs that often, so the header stands alone — the HAVING the
        // page applies is the one the export applies.
        assertEquals("﻿Wert,Bezeichnung\r\n", body);
    }

    /** §12 — the CSV route is a second read surface on the same documents and masks identically. */
    @Test
    @WithMockUser(username = "monty")
    void theCsvExportOfAHiddenDocumentIsIndistinguishableFromANonExistentOne() throws Exception
    {
        MvcResult hidden = mockMvc.perform(get("/api/documents/leaktest_hidden/csv")).andReturn();
        MvcResult onHiddenView = mockMvc.perform(get("/api/documents/leaktest_on_hidden_view/csv")).andReturn();
        MvcResult missing = mockMvc.perform(get("/api/documents/leaktest_no_such_document/csv")).andReturn();

        assertEquals(404, missing.getResponse().getStatus());
        for (MvcResult masked : List.of(hidden, onHiddenView))
        {
            assertEquals(missing.getResponse().getStatus(), masked.getResponse().getStatus());
            assertEquals(missing.getResponse().getContentAsString(), masked.getResponse().getContentAsString());
            assertEquals(missing.getResponse().getContentType(), masked.getResponse().getContentType());
        }
    }

    @Test
    @WithMockUser(username = "monty")
    void hiddenDocumentIsIndistinguishableFromANonExistentOne() throws Exception
    {
        assertIdenticalTo404("/api/documents/leaktest_hidden");
    }

    @Test
    @WithMockUser(username = "monty")
    void aDocumentOverAHiddenViewIsIndistinguishableFromANonExistentOne() throws Exception
    {
        assertIdenticalTo404("/api/documents/leaktest_on_hidden_view");
    }

    /**
     * An unauthenticated caller (no rapla user resolves — e.g. logged out with only a stale
     * remember-me cookie left) is handed back to Spring Security (→ /login for a browser
     * navigation), not the misleading 404. Still leak-safe: a private-but-existing and a
     * never-stored document raise the identical exception, so existence does not leak. (The
     * authenticated-but-unauthorized case above keeps its masked 404.) The security filters are
     * disabled in this class, so the exception surfaces here rather than the 302 —
     * {@code LoginReturnUrlTest} covers the filtered end of it.
     */
    @Test
    void anUnauthenticatedCallerIsSentToLoginIndistinguishablyForHiddenAndMissing() throws Exception
    {
        Throwable hidden = authFailureFor("/api/documents/leaktest_hidden");
        Throwable missing = authFailureFor("/api/documents/leaktest_no_such_document");
        assertEquals(AuthenticationCredentialsNotFoundException.class, hidden.getClass());
        assertEquals(hidden.getClass(), missing.getClass());
        assertEquals(hidden.getMessage(), missing.getMessage());
    }

    /** The unhandled exception MockMvc rethrows out of {@code perform}, unwrapped. */
    private Throwable authFailureFor(String url)
    {
        Throwable t = assertThrows(Exception.class, () -> mockMvc.perform(get(url)));
        while (t.getCause() != null)
        {
            t = t.getCause();
        }
        return t;
    }

    /**
     * The login redirect returns the browser to {@code <document-url>?continue} — Spring Security's
     * saved-request marker, appended by {@code HttpSessionRequestCache}. It is transport, not a
     * document parameter, so the {@code @param} gate must not reject it as undeclared (400).
     */
    @Test
    @WithMockUser(username = "monty")
    void theSavedRequestMarkerOfTheLoginRedirectIsNotAnUndeclaredParam() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/documents/leaktest_visible").param("continue", "")).andReturn();
        assertEquals(200, result.getResponse().getStatus(),
                "?continue comes from the login redirect and must render, not 400");
    }

    @Test
    @WithMockUser(username = "monty")
    void theCatalogListingOnlyContainsWhatTheCallerMaySee() throws Exception
    {
        String body = mockMvc.perform(get("/api/documents")).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("leaktest_visible"), body);
        assertTrue(!body.contains("leaktest_hidden") && !body.contains("leaktest_on_hidden_view"), body);
    }

    /** 401, not 403: {@code RaplaExceptionHandler} maps every {@code RaplaSecurityException} to
     *  UNAUTHORIZED — the codebase has no separate permission-denied exception type. */
    @Test
    @WithMockUser(username = "monty")
    void aNonAdminCannotReadATemplateSource() throws Exception
    {
        assertEquals(401, mockMvc.perform(get("/api/documents/leaktest_visible/source"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @WithMockUser(username = "homer")
    void anAdminCanReadATemplateSource() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/documents/leaktest_visible/source")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("stamp"));
    }

    @Test
    @WithMockUser(username = "monty")
    void aNonAdminCannotPreviewOrInspectAViewShape() throws Exception
    {
        assertEquals(401, post("/api/documents/preview",
                "{\"viewName\":\"" + PUBLIC_VIEW + "\",\"template\":\"<p>x</p>\"}").getStatus());
        assertEquals(401, post("/api/documents/result-shape",
                "{\"viewName\":\"" + PUBLIC_VIEW + "\"}").getStatus());
    }

    @Test
    @WithMockUser(username = "homer")
    void previewRendersAnUnsavedTemplateWithTheRealEngine() throws Exception
    {
        MockHttpServletResponse response = post("/api/documents/preview",
                "{\"viewName\":\"" + PUBLIC_VIEW + "\",\"template\":\"<p>unsaved {{serverTime}}</p>\"}");
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("unsaved"), response.getContentAsString());
    }

    @Test
    @WithMockUser(username = "homer")
    void previewReportsTheParseErrorLineInsteadOfHtml() throws Exception
    {
        MockHttpServletResponse response = post("/api/documents/preview",
                "{\"viewName\":\"" + PUBLIC_VIEW + "\",\"template\":\"<p>\\n{{#a}}{{/b}}\"}");
        assertEquals(200, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"errorLine\":2"), body);
        assertTrue(body.contains("\"html\":null"), body);
    }

    @Test
    @WithMockUser(username = "homer")
    void theResultShapeNamesTheFieldsATemplateMayAddress() throws Exception
    {
        MockHttpServletResponse response = post("/api/documents/result-shape",
                "{\"viewName\":\"" + PUBLIC_VIEW + "\"}");
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"key\":\"serverTime\""),
                response.getContentAsString());
    }

    private MockHttpServletResponse post(String url, String json) throws Exception
    {
        return mockMvc.perform(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
    }

    /** Status, body and content-type must match the response for a name that was never stored. */
    private void assertIdenticalTo404(String url) throws Exception
    {
        MvcResult hidden = mockMvc.perform(get(url)).andReturn();
        MvcResult missing = mockMvc.perform(get("/api/documents/leaktest_no_such_document")).andReturn();

        assertEquals(404, missing.getResponse().getStatus());
        assertEquals(missing.getResponse().getStatus(), hidden.getResponse().getStatus());
        assertEquals(missing.getResponse().getContentAsString(), hidden.getResponse().getContentAsString());
        assertEquals(missing.getResponse().getContentType(), hidden.getResponse().getContentType());
        assertEquals("", hidden.getResponse().getContentAsString());
    }
}
