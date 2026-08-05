package org.rapla.server.spring.graphql;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.graphql.ReservationGraphQLController.EventTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 104 Phase 1 — server-computed grouping paths for the SPA template picker.
 * Token-prefix clustering (names follow conventions like "TINF23B4 Mathe 1"),
 * oversized buckets recurse into the next token, alphabetic range chunks where
 * tokens don't split (the Swing {@code BalancedHierarchicalMenu} mechanic,
 * max 25 per node). Output: one path per template id, root first, empty =
 * ungrouped top level (PRD 104 D2/D3).
 */
class TemplatePathBuilderTest
{
    private static final int MAX = 25;

    private static EventTemplate t(String name)
    {
        return new EventTemplate("id-" + name, name, List.of());
    }

    private static Map<String, List<String>> paths(List<EventTemplate> templates)
    {
        return TemplatePathBuilder.paths(templates, Locale.GERMAN, MAX);
    }

    @Test
    void smallListStaysUngrouped()
    {
        Map<String, List<String>> p = paths(List.of(t("Mathe 1"), t("Mathe 2"), t("Physik")));
        assertEquals(3, p.size());
        p.values().forEach(path -> assertEquals(List.of(), path, "≤ maxPerNode needs no groups"));
    }

    @Test
    void firstTokenFormsSemanticGroups()
    {
        List<EventTemplate> many = IntStream.range(0, 30)
                .mapToObj(i -> t((i < 15 ? "TINF23 Fach" : "WWI23 Fach") + i))
                .toList();
        Map<String, List<String>> p = paths(many);
        assertEquals(List.of("TINF23"), p.get("id-TINF23 Fach0"));
        assertEquals(List.of("WWI23"), p.get("id-WWI23 Fach20"));
    }

    @Test
    void oversizedTokenGroupRecursesIntoNextToken()
    {
        // 40 × TINF23 (splits A/B on the second token) + 10 × WWI23
        List<EventTemplate> many = IntStream.range(0, 50)
                .mapToObj(i -> i < 40
                        ? t("TINF23 " + (i % 2 == 0 ? "A" : "B") + " Fach" + i)
                        : t("WWI23 Fach" + i))
                .toList();
        Map<String, List<String>> p = paths(many);
        assertEquals(List.of("TINF23", "A"), p.get("id-TINF23 A Fach0"));
        assertEquals(List.of("TINF23", "B"), p.get("id-TINF23 B Fach1"));
        assertEquals(List.of("WWI23"), p.get("id-WWI23 Fach40"));
    }

    @Test
    void indistinguishableNamesFallBackToAlphabeticRangeChunks()
    {
        // 60 single-token names — tokens cannot split, expect range buckets ≤ maxPerNode
        List<EventTemplate> many = IntStream.range(10, 70)
                .mapToObj(i -> t("Raumplan" + i))
                .toList();
        Map<String, List<String>> p = paths(many);
        assertEquals(60, p.size(), "no template may get lost in the fallback");
        Set<List<String>> buckets = Set.copyOf(p.values());
        assertTrue(buckets.size() >= 3, "60 items need ≥ 3 buckets of ≤ 25, got: " + buckets);
        for (List<String> path : buckets)
        {
            assertEquals(1, path.size(), "range chunks are one level deep");
            long inBucket = p.values().stream().filter(path::equals).count();
            assertTrue(inBucket <= MAX, "bucket " + path + " exceeds maxPerNode: " + inBucket);
        }
    }

    @Test
    void everyTemplateGetsExactlyOnePath()
    {
        List<EventTemplate> many = IntStream.range(0, 500)
                .mapToObj(i -> t("Kurs" + (i % 37) + " Fach " + i))
                .toList();
        Map<String, List<String>> p = paths(many);
        assertEquals(500, p.size());
        Set<String> ids = many.stream().map(EventTemplate::id).collect(Collectors.toSet());
        assertEquals(ids, p.keySet());
    }
}
