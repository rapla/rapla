package org.rapla.server.spring.document;

import java.util.List;

/**
 * PRD 097 Phase 5 — the DEFAULT calendar templates, shipped as BUILTIN documents over the builtin
 * views ({@code ViewCatalogService.BUILTIN_VIEWS}, {@code rapla_*}). A fresh server renders them
 * at {@code /api/documents/<name>} with a current-week/month {@code @window} — no authoring, no
 * seeding, instantly testable. Routing them behind {@code /rapla/calendar} is deliberately NOT
 * here (PRD 097 OQ10 — CalendarModel-vs-document carrier, undecided).
 *
 * <p>Consolidated 2026-07-15: wochenplan/monatsplan/tagesliste all render the ONE
 * {@code rapla_kalender} view — the template + the document's own window anchors decide the
 * shape (monatsplan is the only one carrying a window override; the others inherit the view's
 * week default). Wochenprogramm keeps its own view for the timeslot group column.
 *
 * <p>These are the {@code docs/templates.md} starters in shipped form: number substitution into
 * CSS grid, named grid lines where an axis offsets the columns (never {@code calc()} inside
 * grid-line numbers), no logic. Admins copy them into custom documents to restyle.
 *
 * <p>Mustache note (month): {@code Strip.index} is aliased {@code s} in the view so the day/bar
 * context can reach the STRIP index through the context stack — an unaliased {@code index} would
 * be shadowed by {@code StripDay.index}.
 */
final class BuiltinDocuments
{
    private BuiltinDocuments() {}

    /**
     * PRD 097 (2026-07-15) — the template-placed window navigation, a shared builtin partial
     * ({@link BuiltinPartials}): boilerplate lives once, copies keep the reference. The
     * {@code rapla/print-hint} partial exists in the catalog but is deliberately NOT in the
     * defaults — everyone knows Ctrl+P; hand-out documents (a Leihschein) opt in per template.
     */
    private static final String NAV_BLOCK = "{{> rapla/nav}}\n";

    private static final String WEEK_TEMPLATE = """
            <style>
            .kal-hdr  { display: grid; grid-template-columns: repeat(7, 1fr); gap: 1px; font-weight: bold; }
            .kal-band { display: grid; grid-template-columns: repeat(7, 1fr); gap: 1px; min-height: 1.2em; }
            .kal-band .bar { background: #fda; border-radius: 3px; padding: 0 4px; overflow: hidden; }
            .kal-week { display: grid; grid-template-columns: repeat(7, 1fr); grid-auto-rows: 0.7px;
                        gap: 0 1px; background: #f7f7f7; }
            .kal-week .block { overflow: hidden; font-size: 11px; border-radius: 3px;
                        background: #cde; border: 1px solid #9ab; container-type: inline-size; }
            /* Density degradation: below readable lane width the text hides and the colored
               sliver remains (occupancy view) — a template cannot do Swing's min-width+scroll. */
            @container (max-width: 48px) { .txt { display: none; } }
            </style>
            <div class="kal-hdr">{{#strips}}{{#days}}<div style="grid-column: {{index}}">{{label}}</div>{{/days}}{{/strips}}</div>
            <div class="kal-band">{{#appointmentBlocks}}{{#bandBars}}<div class="bar" style="grid-column: {{startDay}} / span {{span}}; grid-row: {{row}}">{{name}}</div>{{/bandBars}}{{/appointmentBlocks}}</div>
            <div class="kal-week">
              {{#appointmentBlocks}}{{#segments}}
              <div class="block" style="grid-column: {{dayIndex}}; grid-row: {{startMin}} / {{endMin}}; width: calc(100% / {{laneCount}}); margin-left: calc(100% / {{laneCount}} * ({{lane}} - 1))"><span class="txt">{{^clippedStart}}<b>{{times}}</b> {{/clippedStart}}{{name}}</span></div>
              {{/segments}}{{/appointmentBlocks}}
            </div>""";

    /**
     * One grid for the whole month: named row lines {@code s<strip>-hdr} / {@code s<strip>-r<row>}
     * (6 strips × header + 10 bar rows, statically declared) — bars compose their line name by
     * string concatenation, the documented portable alternative to calc() in grid-line numbers.
     * ⚠ Rows beyond r10 hit CSS's undefined-named-line rule and land in implicit tracks at the
     * grid END (detached bottom bars) — r10 is the pragmatic cap; empty auto tracks collapse.
     */
    private static final String MONTH_TEMPLATE = """
            <style>
            .kal-monat { display: grid; grid-template-columns: repeat(7, 1fr); gap: 1px;
              align-items: start;   /* bars size to content, they do not stretch to the track */
              grid-template-rows:
                [s1-hdr] auto [s1-r1] auto [s1-r2] auto [s1-r3] auto [s1-r4] auto [s1-r5] auto [s1-r6] auto [s1-r7] auto [s1-r8] auto [s1-r9] auto [s1-r10] auto [s1-pad] 1.25em
                [s2-hdr] auto [s2-r1] auto [s2-r2] auto [s2-r3] auto [s2-r4] auto [s2-r5] auto [s2-r6] auto [s2-r7] auto [s2-r8] auto [s2-r9] auto [s2-r10] auto [s2-pad] 1.25em
                [s3-hdr] auto [s3-r1] auto [s3-r2] auto [s3-r3] auto [s3-r4] auto [s3-r5] auto [s3-r6] auto [s3-r7] auto [s3-r8] auto [s3-r9] auto [s3-r10] auto [s3-pad] 1.25em
                [s4-hdr] auto [s4-r1] auto [s4-r2] auto [s4-r3] auto [s4-r4] auto [s4-r5] auto [s4-r6] auto [s4-r7] auto [s4-r8] auto [s4-r9] auto [s4-r10] auto [s4-pad] 1.25em
                [s5-hdr] auto [s5-r1] auto [s5-r2] auto [s5-r3] auto [s5-r4] auto [s5-r5] auto [s5-r6] auto [s5-r7] auto [s5-r8] auto [s5-r9] auto [s5-r10] auto [s5-pad] 1.25em
                [s6-hdr] auto [s6-r1] auto [s6-r2] auto [s6-r3] auto [s6-r4] auto [s6-r5] auto [s6-r6] auto [s6-r7] auto [s6-r8] auto [s6-r9] auto [s6-r10] auto [s6-pad] 1.25em; }
            .kal-monat .daycell { font-weight: bold; border-top: 1px solid #ccc; align-self: stretch; }
            .kal-monat .bar { border-radius: 3px; background: #cde; overflow: hidden;
                        font-size: 11px; padding: 0 4px; margin-bottom: 1px; }
            </style>
            <div class="kal-monat">
              {{#strips}}{{#days}}<div class="daycell" style="grid-column: {{index}}; grid-row: s{{s}}-hdr">{{label}}</div>{{/days}}{{/strips}}
              {{#appointmentBlocks}}{{#bars}}
              <div class="bar" style="grid-column: {{startDay}} / span {{span}}; grid-row: s{{strip}}-r{{row}}">{{name}}</div>
              {{/bars}}{{/appointmentBlocks}}
            </div>""";

    private static final String TAGESLISTE_TEMPLATE = """
            {{#groups}}<h2>{{label}}</h2>
            <ul>{{#rows}}<li><b>{{times}}</b> {{name}}</li>{{/rows}}</ul>
            {{/groups}}""";

    /**
     * Timeslot × day matrix; named column lines offset the band-label column. Bands come from
     * `{{#groups}}` — data, not structure. Cards carry the server-decided block color as an
     * accent border ({@code {{color}}} resolves from the parent block context inside segments);
     * whole-day events (Feiertage) paint as a spanning banner row above the bands.
     */
    private static final String WOCHENPROGRAMM_TEMPLATE = """
            <style>
            .wp { max-width: 1100px; margin: 0 auto; font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
                  color: #22303c; }
            .wp-kopf, .wp-feiertage, .wp .zeile { display: grid; gap: 6px;
              grid-template-columns: [label] 7.5em [d1] 1fr [d2] 1fr [d3] 1fr [d4] 1fr [d5] 1fr; }
            .wp-kopf { position: sticky; top: 0; background: #fff; padding: 8px 0 6px;
                       border-bottom: 2px solid #22303c; z-index: 1; }
            .wp-kopf .tag { grid-row: 1; text-align: center; font-weight: 600; font-size: 13px;
                       padding: 5px 0; background: #22303c; color: #fff; border-radius: 6px; }
            .wp-feiertage .feiertag { background: #fde8b0; border: 1px solid #e8c86a; border-radius: 6px;
                       margin-top: 6px; padding: 3px 10px; font-size: 12px; font-weight: 600; }
            .wp .zeile { grid-auto-flow: row dense; padding: 8px 0; align-items: start;
                       border-bottom: 1px solid #e3e6ea; min-height: 2.6em; }
            .wp section.band:nth-of-type(even) .zeile { background: #f6f8fa; }
            .wp .zeile h3 { grid-column: label; grid-row: 1; align-self: center; margin: 0 0 0 4px;
                       font-size: 11px; font-weight: 600; text-transform: uppercase;
                       letter-spacing: .07em; color: #5b6b7b; }
            .wp .card { background: #fff; border-left: 4px solid #8fa3b8; border-radius: 6px;
                       box-shadow: 0 1px 3px rgba(20, 30, 40, .14); padding: 5px 9px; font-size: 13px;
                       overflow: hidden; }
            .wp .card .zeit { font-size: 11px; color: #5b6b7b; }
            .wp .card .name { font-weight: 600; line-height: 1.25; }
            .wp .card .raum { font-size: 11px; color: #5b6b7b; }
            .wp .card .raum::before { content: "📍 "; font-size: 10px; }
            @media print {
              .wp-kopf { position: static; }
              .wp .card { box-shadow: none; border: 1px solid #cfd6dd; border-left-width: 4px; }
            }
            </style>
            <div class="wp">
            <div class="wp-kopf"><span></span>{{#strips}}{{#days}}<span class="tag" style="grid-column: d{{index}}">{{label}}</span>{{/days}}{{/strips}}</div>
            <div class="wp-feiertage">{{#appointmentBlocks}}{{#bandBars}}<div class="feiertag" style="grid-column: d{{startDay}} / span {{span}}">{{name}}</div>{{/bandBars}}{{/appointmentBlocks}}</div>
            {{#groups}}<section class="band"><div class="zeile"><h3>{{label}}</h3>
              {{#rows}}{{#segments}}<div class="card"{{#color}} style="grid-column: d{{dayIndex}}; border-left-color: {{color}}"{{/color}}{{^color}} style="grid-column: d{{dayIndex}}"{{/color}}>
                <div class="zeit">{{times}}</div>
                <div class="name">{{name}}</div>
                {{#raum}}<div class="raum">{{name}}</div>{{/raum}}
              </div>{{/segments}}{{/rows}}
            </div></section>{{/groups}}
            </div>""";

    private static final String MO_FR =
            "{\"filter\":{\"weekdays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"]}}";

    /** Month anchors — the ONE deviant document; wochenplan/tagesliste inherit the view's week default. */
    private static final String MONTH_WINDOW =
            "{\"from\":{\"anchor\":\"MONTH_START\"},\"to\":{\"anchor\":\"MONTH_START\",\"offset\":1,\"unit\":\"MONTHS\"}}";

    private static DocumentEntry builtinWithNav(String name, String viewName, String template,
            String defaultVariables, String window)
    {
        return builtin(name, viewName, NAV_BLOCK + template, defaultVariables, window);
    }

    static final List<DocumentEntry> ENTRIES = List.of(
            builtinWithNav("wochenplan", "rapla_kalender", WEEK_TEMPLATE, null, null),
            builtinWithNav("monatsplan", "rapla_kalender", MONTH_TEMPLATE, null, MONTH_WINDOW),
            builtinWithNav("tagesliste", "rapla_kalender", TAGESLISTE_TEMPLATE, null, null),
            builtinWithNav("wochenprogramm", "rapla_wochenprogramm", WOCHENPROGRAMM_TEMPLATE, MO_FR, null));

    private static DocumentEntry builtin(String name, String viewName, String template,
            String defaultVariables, String window)
    {
        return new DocumentEntry(name, viewName, template, true, true, List.of(), defaultVariables,
                window, true, List.of());
    }
}
