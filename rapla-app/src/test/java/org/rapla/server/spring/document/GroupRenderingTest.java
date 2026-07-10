package org.rapla.server.spring.document;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 097 Phase 3 — the grouped rows reach the template. {@link RowGrouping.Group} is a
 * package-private record; JMustache resolves {@code {{label}}} through its accessor, so grouping
 * needs no Map conversion. A regression here would silently render empty sections.
 */
class GroupRenderingTest
{
    @Test
    void groupsRenderThroughMustache()
    {
        List<Map<String, Object>> rows = List.of(
                Map.of("date", "2026-06-15", "name", "A"),
                Map.of("date", "2026-06-15", "name", "B"),
                Map.of("date", "2026-06-16", "name", "C"));
        Object groups = RowGrouping.groupByColumn(rows, "date", "EE dd.MM");
        String out = new DocumentRenderer().render(
                "{{#groups}}[{{label}}:{{#rows}}{{name}}{{/rows}}]{{/groups}}", Map.of("groups", groups));
        assertEquals("[Mo 15.06:AB][Di 16.06:C]", out);
    }
}
