package org.rapla.server.spring.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 097 Phase 3 / OQ3 — the group-by projection is a thin Java pass, not a new GraphQL field:
 * the view already declares its grouping via {@code @column(group: true)} and the server already
 * emits {@code extensions.view.groupBy} (the column alias) + {@code groupFormat}.
 *
 * <p>These tests pin <b>parity with the TypeScript implementation the SPA uses</b>
 * ({@code rapla-angular/src/app/graphql/weekday-grouping.ts} and {@code views/group-format.ts}),
 * because otherwise a server-rendered grouped document would drift from the same view rendered
 * in the SPA. The format token is documented as "client-interpreted"; this is the second reader.
 */
class RowGroupingTest
{
    private static Map<String, Object> row(String tag, String name)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("tag", tag);
        row.put("name", name);
        return row;
    }

    @Test
    void bucketsPreserveFirstSeenGroupOrderAndInputOrderWithin()
    {
        List<Map<String, Object>> rows = List.of(
                row("2026-07-14", "b1"), row("2026-07-13", "a1"),
                row("2026-07-14", "b2"), row("2026-07-13", "a2"));

        List<RowGrouping.Group> groups = RowGrouping.groupByColumn(rows, "tag", null);

        assertEquals(List.of("2026-07-14", "2026-07-13"), groups.stream().map(RowGrouping.Group::key).toList(),
                "group order is first-seen, not sorted — sorting is the query's job");
        assertEquals(List.of("b1", "b2"), groups.get(0).rows().stream().map(r -> r.get("name")).toList());
    }

    @Test
    void missingAndEmptyValuesCollectIntoATrailingGroup()
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(null, "kein datum"));
        rows.add(row("2026-07-13", "a"));
        rows.add(row("", "leer"));

        List<RowGrouping.Group> groups = RowGrouping.groupByColumn(rows, "tag", null);

        assertEquals(2, groups.size());
        assertEquals("2026-07-13", groups.get(0).key(), "the empty bucket moves to the end");
        assertEquals("", groups.get(1).key());
        assertEquals("—", groups.get(1).label());
        assertEquals(2, groups.get(1).rows().size(), "null and \"\" share one bucket");
    }

    /**
     * PRD 097 Phase 5 — configured-domain grouping (the timeslot band case): an empty
     * "nachmittags" row must still render (the frame argument, band edition), and the group order
     * is the CONFIGURED order, not first-seen.
     */
    @Test
    void aConfiguredDomainSeedsEmptyGroupsInDomainOrder()
    {
        List<Map<String, Object>> rows = List.of(row("abends", "spät"), row("vormittags", "früh"));

        List<RowGrouping.Group> groups = RowGrouping.groupByColumn(rows, "tag", null,
                List.of("vormittags", "nachmittags", "abends"));

        assertEquals(List.of("vormittags", "nachmittags", "abends"),
                groups.stream().map(RowGrouping.Group::key).toList(),
                "configured order wins; the empty nachmittags band still renders");
        assertEquals(0, groups.get(1).rows().size());
        assertEquals(List.of("früh"), groups.get(0).rows().stream().map(r -> r.get("name")).toList());
    }

    @Test
    void valuesOutsideTheDomainTrailInFirstSeenOrder()
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("fremd", "x"));
        rows.add(row("vormittags", "früh"));
        rows.add(row(null, "kein band"));

        List<RowGrouping.Group> groups = RowGrouping.groupByColumn(rows, "tag", null,
                List.of("vormittags"));

        assertEquals(List.of("vormittags", "fremd", ""), groups.stream().map(RowGrouping.Group::key).toList());
    }

    @Test
    void labelIsTheRawValueWhenNoFormatIsDeclared()
    {
        List<RowGrouping.Group> groups = RowGrouping.groupByColumn(List.of(row("Raum A101", "x")), "tag", null);
        assertEquals("Raum A101", groups.get(0).label(), "non-date group columns are already display-ready");
    }

    @Test
    void formatTokensMatchTheTypescriptInterpreter()
    {
        assertEquals("Mo 13.07.", RowGrouping.formatGroupLabel("2026-07-13", "EE dd.MM."));
        assertEquals("Montag", RowGrouping.formatGroupLabel("2026-07-13", "EEEE"));
        assertEquals("13.7.2026", RowGrouping.formatGroupLabel("2026-07-13", "d.M.yyyy"));
        assertEquals("Jul 26", RowGrouping.formatGroupLabel("2026-07-13", "MMM yy"));
        assertEquals("Juli", RowGrouping.formatGroupLabel("2026-07-13", "MMMM"));
    }

    @Test
    void formatIgnoresTheTimePartAndNeverShiftsTheDay()
    {
        // The TS side parses only the date part via Date.UTC so the host timezone cannot move a
        // row into the previous day. The Java side must not use the server's default zone either.
        assertEquals("Mo", RowGrouping.formatGroupLabel("2026-07-13T00:30:00", "EE"));
        assertEquals("Mo", RowGrouping.formatGroupLabel("2026-07-13T23:30:00", "EE"));
    }

    @Test
    void nonDateValuesPassThroughVerbatim()
    {
        assertEquals("Raum A101", RowGrouping.formatGroupLabel("Raum A101", "EE dd.MM"));
        assertEquals("2026-02-31", RowGrouping.formatGroupLabel("2026-02-31", "EE"), "an impossible date is not a date");
    }

    @Test
    void groupsCarryTheFormattedLabelWhenAFormatIsDeclared()
    {
        List<RowGrouping.Group> groups = RowGrouping.groupByColumn(
                List.of(row("2026-07-13", "a"), row("2026-07-14", "b")), "tag", "EE dd.MM.");

        assertEquals(List.of("Mo 13.07.", "Di 14.07."), groups.stream().map(RowGrouping.Group::label).toList());
        assertEquals("2026-07-13", groups.get(0).key(), "the key stays the raw value; only the label is formatted");
    }
}
