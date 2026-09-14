package org.rapla.plugin.tableview;

import java.util.List;

/**
 * Wire response for {@code /table/config} and {@code /table/columns/catalog}
 * (PRD 030 Phase 3).
 *
 * <p>{@code /config} returns the user's <b>visible</b> column set for a given
 * table view (events / appointments). Ordered — the {@code columns} list
 * defines display order. Reflects user preferences (the same store Swing's
 * {@code TableviewOption} writes to), so two devices for the same user see
 * identical configurations.
 *
 * <p>{@code /columns/catalog} returns the <b>universe</b> of available
 * columns (built-ins + plugin contributions). Lets the Angular UI offer
 * "add this column to my view" without renegotiating the column universe
 * per request.
 *
 * @param tableName {@code "events"} or {@code "appointments"} —
 *                  echoes the request parameter so the response is
 *                  self-describing
 * @param columns   ordered list of column descriptors
 */
public record TableColumnsResponse(String tableName, List<TableColumnDescriptor> columns)
{
    public TableColumnsResponse
    {
        if (tableName == null) throw new IllegalArgumentException("tableName must not be null");
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
