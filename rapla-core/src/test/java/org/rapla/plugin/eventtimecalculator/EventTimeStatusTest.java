package org.rapla.plugin.eventtimecalculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier-1 contract pin for {@link EventTimeStatus} — the diff → status
 * decision behind {@code EventTimeCalculatorStatusWidget.updateStatus}.
 *
 * <p>The widget colours a label based on whether the configured event-time
 * condition annotation evaluates to {@code 0} (target met) or any other
 * number (target missed). The Swing layer keeps the colour selection;
 * this helper pins the categorisation.
 */
class EventTimeStatusTest
{
    @Test
    void diffZeroIsWithinTarget()
    {
        assertEquals(EventTimeStatus.WITHIN_TARGET, EventTimeStatus.classify("0"));
    }

    @Test
    void diffNegativeIsOffTarget()
    {
        assertEquals(EventTimeStatus.OFF_TARGET, EventTimeStatus.classify("-5"));
    }

    @Test
    void diffPositiveIsOffTarget()
    {
        assertEquals(EventTimeStatus.OFF_TARGET, EventTimeStatus.classify("17"));
    }

    @Test
    void nullValueIsUnknown()
    {
        assertEquals(EventTimeStatus.UNKNOWN, EventTimeStatus.classify(null));
    }

    @Test
    void blankValueIsUnknown()
    {
        assertEquals(EventTimeStatus.UNKNOWN, EventTimeStatus.classify("  "));
    }

    @Test
    void emptyValueIsUnknown()
    {
        assertEquals(EventTimeStatus.UNKNOWN, EventTimeStatus.classify(""));
    }

    @Test
    void nonNumericValueIsUnknown()
    {
        assertEquals(EventTimeStatus.UNKNOWN, EventTimeStatus.classify("not a number"));
        assertEquals(EventTimeStatus.UNKNOWN, EventTimeStatus.classify("0x10"));
        assertEquals(EventTimeStatus.UNKNOWN, EventTimeStatus.classify("3.14"));
    }

    @Test
    void leadingPositiveSignAccepted()
    {
        assertEquals(EventTimeStatus.OFF_TARGET, EventTimeStatus.classify("+12"));
    }

    @Test
    void surroundingWhitespaceTrimmed()
    {
        assertEquals(EventTimeStatus.WITHIN_TARGET, EventTimeStatus.classify("  0  "));
        assertEquals(EventTimeStatus.OFF_TARGET, EventTimeStatus.classify("\t-3\n"));
    }
}
