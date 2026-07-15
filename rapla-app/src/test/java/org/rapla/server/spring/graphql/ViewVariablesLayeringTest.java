package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PRD 097 (2026-07-15) — layered variable defaults: the VIEW's defaultVariables are the baseline,
 * the DOCUMENT's deep-merge on top (nested objects merge per key, they do not replace wholesale),
 * caller variables win over both. Regression for "template rendering does not inherit the
 * variables of the view".
 */
class ViewVariablesLayeringTest
{
    private static final String QUERY =
            "query t($filter: ReservationFilter!) @view(title: \"t\") { reservations(filter: $filter) { name } }";

    @Test
    @SuppressWarnings("unchecked")
    void documentDefaultsInheritTheViewBaselinePerKey()
    {
        String viewDefaults = "{\"filter\":{\"from\":\"2026-07-13T00:00:00\",\"to\":\"2026-07-20T00:00:00\","
                + "\"nameContains\":\"kurs\"},\"limit\":10}";
        String docDefaults = "{\"filter\":{\"weekdays\":[\"MONDAY\"]}}";

        Map<String, Object> vars = ViewVariables.mergeLayeredDefaults(
                null, List.of(viewDefaults, docDefaults), QUERY, null);

        Map<String, Object> filter = (Map<String, Object>) vars.get("filter");
        assertEquals("2026-07-13T00:00:00", filter.get("from"), "view baseline survives the document layer");
        assertEquals("kurs", filter.get("nameContains"), "sibling keys of the view baseline survive");
        assertEquals(List.of("MONDAY"), filter.get("weekdays"), "document layer adds its keys");
        assertEquals(10, vars.get("limit"), "top-level view keys survive");
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentValuesOverrideTheViewPerKeyAndCallerWinsOverBoth()
    {
        String viewDefaults = "{\"filter\":{\"from\":\"2026-01-01T00:00:00\",\"to\":\"2026-02-01T00:00:00\"}}";
        String docDefaults = "{\"filter\":{\"from\":\"2026-07-13T00:00:00\"}}";

        Map<String, Object> vars = ViewVariables.mergeLayeredDefaults(
                Map.of("eventId", "e1"), List.of(viewDefaults, docDefaults), QUERY, null);

        Map<String, Object> filter = (Map<String, Object>) vars.get("filter");
        assertEquals("2026-07-13T00:00:00", filter.get("from"), "document overrides the view per key");
        assertEquals("2026-02-01T00:00:00", filter.get("to"), "untouched view keys stay");
        assertEquals("e1", vars.get("eventId"), "caller variables win");
    }

    /**
     * Caller variables must merge PER KEY like the defaults layers do — a caller pinning
     * {@code filter.allocatableIdsIn} must not wipe the defaults' {@code filter.from/to/weekdays}
     * (observed 2026-07-15: an editor vars-box entry replaced the whole filter, and the window
     * fallback silently re-filled the dates with the current week).
     */
    @Test
    @SuppressWarnings("unchecked")
    void callerVariablesDeepMergeInsteadOfReplacingTopLevelObjects()
    {
        String viewDefaults = "{\"filter\":{\"from\":\"2026-06-15T00:00:00\",\"to\":\"2026-06-22T00:00:00\","
                + "\"weekdays\":[\"MONDAY\",\"FRIDAY\"]}}";

        Map<String, Object> vars = ViewVariables.mergeLayeredDefaults(
                Map.of("filter", Map.of("allocatableIdsIn", List.of("a1"))),
                List.of(viewDefaults), QUERY, null);

        Map<String, Object> filter = (Map<String, Object>) vars.get("filter");
        assertEquals("2026-06-15T00:00:00", filter.get("from"), "defaults' dates survive a caller filter key");
        assertEquals("2026-06-22T00:00:00", filter.get("to"), "defaults' dates survive a caller filter key");
        assertEquals(List.of("MONDAY", "FRIDAY"), filter.get("weekdays"), "pinned weekdays survive ?resource=");
        assertEquals(List.of("a1"), filter.get("allocatableIdsIn"), "the caller's key wins");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anOverrideWindowFillsOnlyTheMissingBounds()
    {
        String docDefaults = "{\"filter\":{\"from\":\"2026-07-13T00:00:00\"}}";
        WindowResolver.Window window = new WindowResolver.Window(
                "2026-07-01T00:00:00", "2026-08-01T00:00:00");

        Map<String, Object> vars = ViewVariables.mergeLayeredDefaults(
                null, List.of(docDefaults), QUERY, window);

        Map<String, Object> filter = (Map<String, Object>) vars.get("filter");
        assertEquals("2026-07-13T00:00:00", filter.get("from"), "explicit defaults beat the window");
        assertEquals("2026-08-01T00:00:00", filter.get("to"), "the window fills the missing bound");
    }
}
