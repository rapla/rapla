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

/**
 * PRD 097 Phase 5 — the seed templates prove the contract END-TO-END: stored view + stored
 * Mustache template + pinned window render the four classic calendar shapes through the real
 * document pipeline (GraphQL execution, grouping, sanitizer, shell). The templates here are the
 * {@code docs/templates.md} copy-paste starters — if one drifts, the docs drift.
 *
 * <p>Covers BOTH authoring shapes (decided 2026-07-14): one UNIFIED view ({@code seed_kalender},
 * selecting the union) rendered through a week AND a month template, and SPLIT sibling views
 * (Tagesliste, Wochenprogramm) with trimmed selections.
 *
 * <p>Fixture week 2001-10-15 – 2001-10-21 holds three blocks: weekly Mo 17:00–20:00,
 * Tue 12:00–19:00, weekly Sa 17:00–20:00.
 *
 * <p>Convention pinned here: the DATA field is selected FIRST, {@code strips} after — columns and
 * {@code groupBy} derive from the first root field (pre-existing {@code ViewMetaInstrumentation}
 * behavior).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class CalendarTemplateRenderingTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = CalendarTemplateRenderingTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    private static final String WEEK_WINDOW =
            "{\"filter\":{\"from\":\"2001-10-15T00:00:00\",\"to\":\"2001-10-22T00:00:00\"}}";
    private static final String WEEK_WINDOW_MO_FR =
            "{\"filter\":{\"from\":\"2001-10-15T00:00:00\",\"to\":\"2001-10-22T00:00:00\","
                    + "\"weekdays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"]}}";
    private static final String MONTH_WINDOW =
            "{\"filter\":{\"from\":\"2001-10-01T00:00:00\",\"to\":\"2001-11-01T00:00:00\"}}";

    /** The UNIFIED view: one body, the union of the primitives — templates pick what they consume. */
    private static final String KALENDER_VIEW = """
            query seed_kalender($filter: ReservationFilter!) @view(title: "Kalender") {
              appointmentBlocks(filter: $filter) {
                name  times  banner
                segments { dayIndex startMin endMin lane laneCount clippedStart clippedEnd }
                bars { strip startDay span row }
                bandBars: bars(scope: BANNER) { strip startDay span row }
              }
              strips(filter: $filter) { index days { index label } }
            }""";

    /** Week: header from strips, banner band, minute-proportional columns — pure number substitution. */
    private static final String WEEK_TEMPLATE = """
            <style>
            .week { display: grid; grid-template-columns: repeat(7, 1fr); grid-auto-rows: 1px; position: relative; }
            .hdr  { display: grid; grid-template-columns: repeat(7, 1fr); }
            .block { overflow: hidden; font-size: 11px; border-radius: 3px; background: #cde; }
            </style>
            <div class="hdr">{{#strips}}{{#days}}<div style="grid-column: {{index}}">{{label}}</div>{{/days}}{{/strips}}</div>
            <div class="hdr band">{{#appointmentBlocks}}{{#bandBars}}<div class="bar" style="grid-column: {{startDay}} / span {{span}}; grid-row: {{row}}">{{name}}</div>{{/bandBars}}{{/appointmentBlocks}}</div>
            <div class="week">
              {{#appointmentBlocks}}{{#segments}}
              <div class="block" style="grid-column: {{dayIndex}}; grid-row: {{startMin}} / {{endMin}}; width: calc(100% / {{laneCount}}); margin-left: calc(100% / {{laneCount}} * ({{lane}} - 1))">{{^clippedStart}}<b>{{times}}</b>{{/clippedStart}} {{name}}</div>
              {{/segments}}{{/appointmentBlocks}}
            </div>""";

    /** Month: one grid row per strip, every block paints as a bar (scope ALL, no banner routing). */
    private static final String MONTH_TEMPLATE = """
            {{#strips}}<div class="weekrow">{{#days}}<div class="daycell" style="grid-column: {{index}}">{{label}}</div>{{/days}}</div>{{/strips}}
            {{#appointmentBlocks}}{{#bars}}
            <div class="bar" data-strip="{{strip}}" style="grid-column: {{startDay}} / span {{span}}; grid-row: {{row}}">{{name}}</div>
            {{/bars}}{{/appointmentBlocks}}""";

    /** Tagesliste: needs NO Phase-5 field — Phase-3 groups only (the null case pays nothing). */
    private static final String TAGESLISTE_VIEW = """
            query seed_tagesliste($filter: ReservationFilter!) @view(title: "Tagesliste") {
              appointmentBlocks(filter: $filter) {
                tag: start @column(group: true, format: "EE dd.MM.")
                times  name
              }
            }""";

    private static final String TAGESLISTE_TEMPLATE =
            "{{#groups}}<h2>{{label}}</h2><ul>{{#rows}}<li><b>{{times}}</b> {{name}}</li>{{/rows}}</ul>{{/groups}}";

    /** Wochenprogramm: timeslot × day matrix — bands are DATA ({{#groups}}), never template structure. */
    private static final String WOCHENPROGRAMM_VIEW = """
            query seed_wochenprogramm($filter: ReservationFilter!) @view(title: "Wochenprogramm") {
              appointmentBlocks(filter: $filter) {
                name
                band: timeslot @column(group: true)
                segments { dayIndex }
              }
              strips(filter: $filter) { days { index label } }
            }""";

    private static final String WOCHENPROGRAMM_TEMPLATE = """
            <div class="kopf">{{#strips}}{{#days}}<span>{{label}}</span>{{/days}}{{/strips}}</div>
            {{#groups}}<section class="band"><h3>{{label}}</h3>
            <div style="display: grid; grid-template-columns: repeat(5, 1fr)">
              {{#rows}}{{#segments}}<div class="card" style="grid-column: {{dayIndex}}">{{name}}</div>{{/segments}}{{/rows}}
            </div></section>{{/groups}}""";

    @Autowired MockMvc mockMvc;
    @Autowired ViewCatalogService views;
    @Autowired DocumentCatalogService documents;
    @Autowired StorageOperator operator;

    @BeforeEach
    void seed() throws Exception
    {
        User admin = operator.getUser("homer");
        assertEquals(List.of(), views.saveView("seed_kalender", KALENDER_VIEW, true, List.of(), null, admin));
        assertEquals(List.of(), views.saveView("seed_tagesliste", TAGESLISTE_VIEW, true, List.of(), null, admin));
        assertEquals(List.of(), views.saveView("seed_wochenprogramm", WOCHENPROGRAMM_VIEW, true, List.of(), null, admin));
        // The unified view serves BOTH documents — template + pinned window decide the shape.
        assertEquals(List.of(), documents.save("seed_kalender_woche", "seed_kalender", WEEK_TEMPLATE,
                true, List.of(), WEEK_WINDOW, admin));
        assertEquals(List.of(), documents.save("seed_kalender_monat", "seed_kalender", MONTH_TEMPLATE,
                true, List.of(), MONTH_WINDOW, admin));
        assertEquals(List.of(), documents.save("seed_tagesliste_doc", "seed_tagesliste", TAGESLISTE_TEMPLATE,
                true, List.of(), WEEK_WINDOW, admin));
        assertEquals(List.of(), documents.save("seed_wochenprogramm_doc", "seed_wochenprogramm", WOCHENPROGRAMM_TEMPLATE,
                true, List.of(), WEEK_WINDOW_MO_FR, admin));
    }

    private String render(String document) throws Exception
    {
        var response = mockMvc.perform(get("/api/documents/" + document)).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        return response.getContentAsString();
    }

    @Test
    @WithMockUser(username = "homer")
    void theWeekTemplateRendersMinuteProportionalGeometry() throws Exception
    {
        String html = render("seed_kalender_woche");
        assertTrue(html.contains("Mo 15.10."), html);
        assertTrue(html.contains("So 21.10."), html);
        // The Tuesday 12:00–19:00 block: day column 2, minute rows 720–1140, sole lane.
        assertTrue(html.contains("grid-column: 2; grid-row: 720 / 1140"), html);
        assertTrue(html.contains("calc(100% / 1)"), html);
        // Inline styles survive the sanitizer — the whole technique depends on it.
        assertTrue(html.contains("<style>"), "template CSS must reach the page");
    }

    @Test
    @WithMockUser(username = "homer")
    void theMonthTemplateRendersBarsFromTheSameUnifiedView() throws Exception
    {
        String html = render("seed_kalender_monat");
        // October 2001 snaps to Mo 01.10. – So 04.11. (5 strips).
        assertTrue(html.contains("Mo 01.10."), html);
        assertTrue(html.contains("So 04.11."), html);
        // The Tuesday block sits in strip 3 (week of the 15th), day 2.
        assertTrue(html.contains("data-strip=\"3\" style=\"grid-column: 2 / span 1"), html);
    }

    @Test
    @WithMockUser(username = "homer")
    void theTageslisteNeedsNoPhase5Fields() throws Exception
    {
        String html = render("seed_tagesliste_doc");
        assertTrue(html.contains("<h2>Di 16.10.</h2>"), html);
        assertTrue(html.contains("<li>"), html);
    }

    @Test
    @WithMockUser(username = "homer")
    void theWochenprogrammRendersConfiguredBandsIncludingEmptyOnes() throws Exception
    {
        String html = render("seed_wochenprogramm_doc");
        // Default TimeslotProvider config = 7 bands (06:00 … 18:00) — ALL render, empty or not.
        assertEquals(7, html.split("<section class=\"band\">", -1).length - 1, html);
        // The Mo 17:00 block lands as a card in day column 1; the Tue 12:00 block in column 2.
        assertTrue(html.contains("grid-column: 1\">"), html);
        assertTrue(html.contains("grid-column: 2\">"), html);
        // Mo–Fr day set: the Saturday block is dropped, the header has five days.
        assertFalse(html.contains("Sa 20.10."), html);
        assertTrue(html.contains("Fr 19.10."), html);
    }
}
