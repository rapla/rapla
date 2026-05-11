package org.rapla.plugin.calendarview;

/**
 * One column in a {@link CalendarPage}. The meaning of a column depends
 * on the {@link GroupBy} requested:
 * <ul>
 *   <li>{@code DAY} — {@code id} is the ISO date (e.g. {@code "2026-06-10"}),
 *       {@code label} is a locale-formatted day name + date.</li>
 *   <li>{@code RESOURCE} — {@code id} is the allocatable's reference id,
 *       {@code label} is the resource name.</li>
 * </ul>
 * {@code index} is the column's zero-based position; {@link RenderedBlock#columnIndex}
 * matches this.
 */
public record Column(String id, String label, int index)
{
}
