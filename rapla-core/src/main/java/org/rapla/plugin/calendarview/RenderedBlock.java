package org.rapla.plugin.calendarview;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One server-positioned tile in a {@link CalendarPage}. Deliberately
 * resolution-independent — carries logical column/slot coordinates plus
 * a time range; the client multiplies by its own pixel-per-minute and
 * column-width when rendering.
 *
 * <ul>
 *   <li>{@code columnIndex} — index into {@link CalendarPage#columns}.</li>
 *   <li>{@code slotIndex} — 0-based lane within the column for overlap
 *       resolution; {@code slotCount} is the total number of lanes in
 *       this column (so the client can compute lane width as
 *       {@code columnWidth / slotCount}).</li>
 *   <li>{@code colorsHex} — display colours from
 *       {@code RaplaBlock.getColorsAsHex()}. First is primary; multiple
 *       entries mean a striped tile (resource-coloured with multiple
 *       allocatables).</li>
 *   <li>{@code name} — pre-formatted display name (locale-aware,
 *       permission-aware: anonymous reservations get a placeholder).</li>
 * </ul>
 */
public record RenderedBlock(
        String reservationId,
        String appointmentId,
        int columnIndex,
        int slotIndex,
        int slotCount,
        LocalDateTime start,
        LocalDateTime end,
        List<String> colorsHex,
        boolean isException,
        boolean isRequest,
        String name,
        String tooltip)
{
    public RenderedBlock
    {
        colorsHex = colorsHex == null ? List.of() : List.copyOf(colorsHex);
    }
}
