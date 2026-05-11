package org.rapla.plugin.calendarview;

import java.time.LocalDate;
import java.util.List;

/**
 * One page of server-rendered calendar tiles. Returned by
 * {@code CalendarViewService.view(...)}.
 * <p>
 * Columns and blocks are pre-laid-out by the server using the requested
 * {@link LayoutStrategyId}. The client only does pixel mapping —
 * {@code (columnIndex, slotIndex, slotCount, start, end)} → screen
 * rectangle.
 */
public record CalendarPage(
        LocalDate from,
        LocalDate to,
        GroupBy groupBy,
        LayoutStrategyId strategy,
        List<Column> columns,
        List<RenderedBlock> blocks)
{
    public CalendarPage
    {
        columns = columns == null ? List.of() : List.copyOf(columns);
        blocks  = blocks  == null ? List.of() : List.copyOf(blocks);
    }
}
