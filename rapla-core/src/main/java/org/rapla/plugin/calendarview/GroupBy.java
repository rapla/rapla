package org.rapla.plugin.calendarview;

/**
 * What the columns of the returned {@link CalendarPage} represent.
 * <ul>
 *   <li>{@code DAY} — one column per day in the requested range (week view).</li>
 *   <li>{@code RESOURCE} — one column per allocatable (resource view).</li>
 * </ul>
 */
public enum GroupBy
{
    DAY,
    RESOURCE
}
