package org.rapla.plugin.calendarview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 contract pin for {@link BlockColors} (PRD 030 Phase 4).
 *
 * <p>The helper is intentionally narrow: it takes pre-resolved color
 * strings (from {@code RaplaBuilder.getColorForClassifiable(...)}) plus
 * the two coloring-mode flags, and returns a deduplicated, ordered list.
 * Entity → color extraction stays out of the helper so the tests don't
 * need to stub {@code Reservation} / {@code Allocatable} graphs.
 *
 * <p>Both render paths (Swing {@code RaplaBlock.getColorsAsHex()} and the
 * server-side decorator that feeds {@code RenderedBlock.colorsHex}) call
 * this helper, so the rules below are the single source of truth for
 * "what colors apply to a block".
 */
class BlockColorsTest
{
    @Test
    void eventColoringOnReturnsEventColor()
    {
        List<String> result = BlockColors.resolve(true, "#ff0000", false, List.of("#00ff00"));
        assertEquals(List.of("#ff0000"), result);
    }

    @Test
    void resourceColoringOnReturnsAllocatableColors()
    {
        List<String> result = BlockColors.resolve(false, "#ff0000", true, List.of("#00ff00", "#0000ff"));
        assertEquals(List.of("#00ff00", "#0000ff"), result);
    }

    @Test
    void bothColoringsConcatenateEventFirst()
    {
        List<String> result = BlockColors.resolve(true, "#ff0000", true, List.of("#00ff00", "#0000ff"));
        assertEquals(List.of("#ff0000", "#00ff00", "#0000ff"), result);
    }

    @Test
    void neitherColoringReturnsEmpty()
    {
        List<String> result = BlockColors.resolve(false, "#ff0000", false, List.of("#00ff00"));
        assertTrue(result.isEmpty());
    }

    @Test
    void duplicateColorsAreDeduplicated()
    {
        // Event and resource resolving to the same hex string — one entry only.
        List<String> result = BlockColors.resolve(true, "#ff0000", true, List.of("#ff0000", "#00ff00"));
        assertEquals(List.of("#ff0000", "#00ff00"), result);
    }

    @Test
    void duplicateResourceColorsCollapsedToFirstAppearance()
    {
        List<String> result = BlockColors.resolve(false, null, true,
                List.of("#00ff00", "#0000ff", "#00ff00", "#ff8800"));
        assertEquals(List.of("#00ff00", "#0000ff", "#ff8800"), result);
    }

    @Test
    void nullEventColorIgnored()
    {
        List<String> result = BlockColors.resolve(true, null, true, List.of("#00ff00"));
        assertEquals(List.of("#00ff00"), result);
    }

    @Test
    void blankEventColorIgnored()
    {
        List<String> result = BlockColors.resolve(true, "  ", true, List.of("#00ff00"));
        assertEquals(List.of("#00ff00"), result);
    }

    @Test
    void nullResourceListTreatedAsEmpty()
    {
        List<String> result = BlockColors.resolve(true, "#ff0000", true, null);
        assertEquals(List.of("#ff0000"), result);
    }

    @Test
    void nullResourceColorEntriesIgnored()
    {
        List<String> result = BlockColors.resolve(false, null, true,
                java.util.Arrays.asList("#00ff00", null, "#0000ff", "  "));
        assertEquals(List.of("#00ff00", "#0000ff"), result);
    }

    @Test
    void emptyEverythingReturnsEmpty()
    {
        List<String> result = BlockColors.resolve(true, null, true, List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void resultIsImmutable()
    {
        List<String> result = BlockColors.resolve(true, "#ff0000", false, null);
        try
        {
            result.add("#aaaaaa");
            // If we got here, the returned list is mutable — contract violation.
            throw new AssertionError("BlockColors.resolve must return an immutable list");
        }
        catch (UnsupportedOperationException expected)
        {
            // ok
        }
    }
}
