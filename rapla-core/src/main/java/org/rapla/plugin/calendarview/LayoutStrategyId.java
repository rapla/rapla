package org.rapla.plugin.calendarview;

/**
 * Wire-format identifier for the layout strategy to run on the server.
 * Maps to a concrete {@code BuildStrategy} implementation.
 */
public enum LayoutStrategyId
{
    /** Maps to {@code GroupStartTimesStrategy} — groups overlapping blocks by start time. */
    GROUP_START_TIMES,
    /** Maps to {@code BestFitStrategy} — packs blocks into the fewest slots. */
    BEST_FIT
}
