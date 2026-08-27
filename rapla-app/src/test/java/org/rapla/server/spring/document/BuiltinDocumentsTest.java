package org.rapla.server.spring.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * PRD 097 Phase 5/6 — the DEFAULT calendar templates ship as BUILTIN views + documents: a fresh
 * server renders them at {@code /api/documents/...} with a current-week/month {@code @window},
 * no admin authoring needed ("just the default templates so we can test — routing later",
 * 2026-07-14; the /rapla/calendar routing itself is OQ10, undecided).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class BuiltinDocumentsTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = BuiltinDocumentsTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired ViewCatalogService views;
    @Autowired DocumentCatalogService documents;
    @Autowired StorageOperator operator;

    private String render(String document) throws Exception
    {
        var response = mockMvc.perform(get("/api/documents/" + document)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), document);
        return response.getContentAsString();
    }

    /** Builtin view bodies must stay valid against the live schema — this is the drift alarm. */
    @Test
    void everyBuiltinViewValidatesAgainstTheLiveSchema() throws Exception
    {
        User admin = operator.getUser("homer");
        // 2 SPA contracts + rapla_kalender (unified, 2026-07-15); rapla_wochenprogramm is
        // listed:false (2026-08-12) and validated via its by-name lookup below.
        List<org.rapla.server.spring.graphql.ViewEntry> builtins = new java.util.ArrayList<>(
                views.listViewsForCaller(admin)
                        .stream().filter(org.rapla.server.spring.graphql.ViewEntry::builtin).toList());
        assertEquals(3, builtins.size(), "expected 3 listed builtin views, got " + builtins);
        builtins.add(views.findView("rapla_wochenprogramm").orElseThrow());
        for (var view : builtins)
        {
            assertEquals(List.of(), views.validateQuery(view.queryText()).stream()
                            .map(i -> view.name() + ": " + i.message()).toList(),
                    "builtin view " + view.name() + " must validate");
        }
    }

    @Test
    @WithMockUser(username = "homer")
    void theBuiltinDocumentsRenderOutOfTheBoxWithAResourceScope() throws Exception
    {
        // 2026-08-11 — the builtin views declare @param(resource, required: true): a bare URL
        // renders the hint page, a scoped URL the frame (current-week/month windows — the
        // fixture's 2001 blocks are absent, but the frame renders; §12: an unresolvable id
        // scopes to nothing, indistinguishable from an empty week).
        String bare = render("wochenplan");
        assertTrue(bare.contains("rapla-missing-param"), bare);
        assertTrue(bare.contains("resource"), bare);

        String woche = render("wochenplan?resource=a1");
        assertTrue(woche.contains("<style>"), woche);
        assertTrue(woche.contains("Mo "), "week header expected: " + woche);
        // The print hint is an opt-in partial ({{> rapla/print-hint}}), not shell chrome and
        // deliberately NOT in the defaults — everyone knows Ctrl+P.
        assertFalse(woche.contains("class=\"rapla-print-hint\""), woche);

        // monatsplan is the ONE document with a window override (PRD 097 2026-07-15): the shared
        // rapla_kalender view defaults to a week; the document's MONTH_START anchors must win —
        // a month grid has at least 28 day cells, a week only 7.
        String monat = render("monatsplan?resource=a1");
        int daycells = monat.split("class=\"daycell\"", -1).length - 1;
        assertTrue(daycells >= 28, "document window override must yield a month grid, got "
                + daycells + " day cells");

        render("tagesliste?resource=a1");

        String programm = render("wochenprogramm?resource=a1");
        // Default TimeslotProvider config = 7 bands; all render (empty groups included),
        // and the Mo–Fr day set pinned in defaultVariables shapes the header.
        assertEquals(7, programm.split("<section class=\"band\">", -1).length - 1, programm);
        assertFalse(programm.contains("Sa "), "Mo–Fr day set expected: " + programm);
    }

    @Test
    @WithMockUser(username = "homer")
    void builtinDocumentsAppearInTheListing() throws Exception
    {
        User admin = operator.getUser("homer");
        List<String> names = documents.list(admin).stream().map(DocumentEntry::name).toList();
        assertTrue(names.containsAll(List.of("wochenplan", "monatsplan", "tagesliste", "wochenprogramm")),
                names.toString());
    }

    /**
     * The editor preview must not diverge from the real render (PRD 097 2026-07-15): it carries
     * the document's window anchors and defaultVariables. A monatsplan preview without the window
     * would silently show a WEEK — the exact lie this pins against.
     */
    @Test
    @WithMockUser(username = "homer")
    void previewHonorsTheDocumentWindowAndDefaultVariables() throws Exception
    {
        DocumentEntry monatsplan = documents.find("monatsplan").orElseThrow();
        String body = new tools.jackson.databind.json.JsonMapper().writeValueAsString(java.util.Map.of(
                "viewName", monatsplan.viewName(),
                "template", monatsplan.template(),
                "window", monatsplan.window(),
                "variables", java.util.Map.of("resource", List.of("does-not-exist"))));
        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/documents/preview")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        var json = new tools.jackson.databind.json.JsonMapper().readTree(response.getContentAsString());
        String html = json.get("html").asString();
        int daycells = html.split("class=\"daycell\"", -1).length - 1;
        assertTrue(daycells >= 28, "preview must apply the document window (month grid), got "
                + daycells + " day cells");
        assertTrue(html.contains("<nav class=\"rapla-nav\""),
                "the template-placed nav renders in the preview too: " + html);
        // Preview links must never navigate the sandboxed srcdoc iframe (ANY href — even # —
        // resolves against the parent URL and dead-ends in a browser error page). The preview
        // shell script prevents every click and postMessages nav targets to the editor, which
        // rewrites the vars box ("full transparency") — driven by the data-nav-* contract with
        // SERVER-resolved neighbor windows.
        assertFalse(html.contains("href=\"?date="),
                "preview must not carry live nav links: " + html);
        assertTrue(html.contains("rapla-preview-nav"),
                "preview shell must carry the nav script: " + html);
        assertTrue(html.contains("data-nav-from=\"2"),
                "preview nav must carry server-resolved neighbor windows: " + html);
        // The editor's variables pane feeds off this: the RESOLVED variables of the render.
        String resolved = json.get("resolvedVariables").asString();
        assertTrue(resolved.contains("\"from\"") && resolved.contains("\"to\""),
                "resolved variables must carry the filled window: " + resolved);
    }

    /**
     * PRD 097 § params (2026-07-15) — the preview speaks the document URL's parameter language,
     * through the SAME gate: public {@code @param} names work, undeclared keys (including the
     * old private-variable-path style) come back as an editor message.
     */
    @Test
    @WithMockUser(username = "homer")
    void previewSpeaksTheDocumentUrlParamLanguage() throws Exception
    {
        var json = postPreview(java.util.Map.of("resource", List.of("does-not-exist")));
        assertTrue(json.get("errorMessage").isNull(), "declared public @param name must pass the gate: " + json);
        assertFalse(json.get("html").isNull(), json.toString());

        var rejected = postPreview(java.util.Map.of("filter.allocatableIdsIn", List.of("x")));
        assertFalse(rejected.get("errorMessage").isNull(),
                "private variable paths are not URL params — the gate must reject them: " + rejected);

        var navigated = postPreview(java.util.Map.of("date", List.of("2026-03-04")));
        assertTrue(navigated.get("resolvedVariables").asString().contains("2026-03-02"),
                "?date= must navigate the preview window like the live URL: " + navigated);
    }

    private tools.jackson.databind.JsonNode postPreview(java.util.Map<String, List<String>> params) throws Exception
    {
        String body = new tools.jackson.databind.json.JsonMapper().writeValueAsString(java.util.Map.of(
                "viewName", "rapla_kalender", "template", "x", "variables", params));
        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/documents/preview")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        return new tools.jackson.databind.json.JsonMapper().readTree(response.getContentAsString());
    }

    @Test
    void builtinNamesCannotBeOverwrittenOrDeleted() throws Exception
    {
        User admin = operator.getUser("homer");
        List<String> errors = documents.save("wochenplan", "rapla_wochenplan", "x", true, List.of(), null, admin);
        assertFalse(errors.isEmpty(), "builtin document name must be reserved");
        assertFalse(documents.delete("wochenplan", admin), "builtin document must not be deletable");
    }
}
