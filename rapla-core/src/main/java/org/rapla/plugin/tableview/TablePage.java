package org.rapla.plugin.tableview;

import java.util.List;

/**
 * Wire response from {@link TableViewEngine#project}.
 *
 * @param columns     ordered column descriptors as requested
 * @param rows        projected rows (subset of full result if paginated /
 *                    capped)
 * @param totalCount  size of the full result before pagination — always
 *                    the unpaginated count, so the client can say
 *                    "showing N of {totalCount}"
 * @param nextCursor  opaque cursor for the next page, or {@code null}
 *                    when no more rows remain
 * @param incomplete  {@code true} when the server hit a cap on the
 *                    no-{@code pageSize} response and truncated the
 *                    result. Pairs with a non-null {@code nextCursor}.
 */
public record TablePage(
        List<TableColumnDescriptor> columns,
        List<TableRow> rows,
        int totalCount,
        String nextCursor,
        boolean incomplete)
{
    public TablePage
    {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows    = rows    == null ? List.of() : List.copyOf(rows);
    }
}
