package org.rapla.storage.dbsql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit test for the startup-defrag decision {@link DBOperator#shouldDefrag(long, long, double)}:
 * defrag only when unreclaimable dead space ({@code FILE_LOST_BYTES}) exceeds the configured
 * ratio of the used file, and never when disabled or on a healthy DB.
 */
public class DbOperatorShouldDefragTest
{
    @Test
    void defragsWhenLostRatioExceedsThreshold()
    {
        // 300MB lost of 1000MB used = 30% > 25%
        assertTrue(DBOperator.shouldDefrag(300_000_000L, 1_000_000_000L, 0.25));
    }

    @Test
    void skipsWhenBelowThreshold()
    {
        // 100MB lost of 1000MB used = 10% < 25%
        assertFalse(DBOperator.shouldDefrag(100_000_000L, 1_000_000_000L, 0.25));
    }

    @Test
    void skipsOnHealthyDbWithZeroLostBytes()
    {
        // freshly imported: 0 lost — never defrag (matches the empirical FILE_LOST_BYTES=0)
        assertFalse(DBOperator.shouldDefrag(0L, 684_042_560L, 0.25));
    }

    @Test
    void disabledWhenThresholdNonPositive()
    {
        assertFalse(DBOperator.shouldDefrag(900_000_000L, 1_000_000_000L, 0.0));
        assertFalse(DBOperator.shouldDefrag(900_000_000L, 1_000_000_000L, -1.0));
    }

    @Test
    void safeOnEmptyOrUnknownFile()
    {
        assertFalse(DBOperator.shouldDefrag(0L, 0L, 0.25));
        assertFalse(DBOperator.shouldDefrag(-1L, -1L, 0.25));
    }

    @Test
    void exactThresholdDoesNotDefrag()
    {
        // strictly greater-than: exactly 25% should not trigger
        assertFalse(DBOperator.shouldDefrag(250L, 1000L, 0.25));
        assertTrue(DBOperator.shouldDefrag(251L, 1000L, 0.25));
    }
}
