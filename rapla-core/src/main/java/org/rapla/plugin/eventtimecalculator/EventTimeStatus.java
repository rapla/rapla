package org.rapla.plugin.eventtimecalculator;

/**
 * Categorisation of the configured "event-time condition" annotation value
 * (PRD 023 carve-out from {@code EventTimeCalculatorStatusWidget.updateStatus}).
 *
 * <p>The annotation evaluates to a numeric diff (minutes between actual
 * and target duration). Three buckets feed the Swing widget's colour
 * choice:
 *
 * <ul>
 *   <li>{@link #WITHIN_TARGET} — diff == 0 (target met exactly).</li>
 *   <li>{@link #OFF_TARGET}    — diff != 0 (over or under).</li>
 *   <li>{@link #UNKNOWN}       — value is null / blank / non-numeric.</li>
 * </ul>
 *
 * <p>The widget maps these to colours; this helper owns the categorisation
 * so a future Angular client can apply the same rule. Tier-1 testable
 * with String inputs; no facade.
 */
public enum EventTimeStatus
{
    WITHIN_TARGET, OFF_TARGET, UNKNOWN;

    /**
     * Classify a raw annotation value string. Accepts surrounding whitespace
     * and a leading {@code +} sign; returns {@link #UNKNOWN} for null,
     * blank, or non-integer inputs.
     */
    public static EventTimeStatus classify(String raw)
    {
        if (raw == null) return UNKNOWN;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return UNKNOWN;
        // Strip a leading + so "+12" parses (Long.parseLong rejects it).
        if (trimmed.startsWith("+") && trimmed.length() > 1) trimmed = trimmed.substring(1);
        try
        {
            long diff = Long.parseLong(trimmed);
            return diff == 0 ? WITHIN_TARGET : OFF_TARGET;
        }
        catch (NumberFormatException ex)
        {
            return UNKNOWN;
        }
    }
}
