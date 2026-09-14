package org.rapla.server.spring.graphql;

/** PRD 074 — render modes advertised per view via {@code @view(renderModes: [...])}. */
public enum ViewRenderMode
{
    // table = flat rows; grouped = rows in sections keyed by the @column(group:true)
    // column (the group is configurable, not day-specific); day/week = time grids
    // (1 / 7 columns); month = month grid; program = future.
    table, grouped, week, day, month, program
}
