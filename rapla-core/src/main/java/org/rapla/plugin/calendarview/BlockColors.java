package org.rapla.plugin.calendarview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Shared block color resolver used by both render paths (PRD 030 Phase 4):
 *
 * <ul>
 *   <li>Swing — {@code RaplaBlock.getColorsAsHex()} delegates here after
 *       resolving event/allocatable colors via
 *       {@code RaplaBuilder.getColorForClassifiable(...)} and the
 *       {@code BuildContext}'s per-allocatable colour map.</li>
 *   <li>Server — {@code RaplaBlockDecorator.colorsFor(...)} (the
 *       implementation of {@link BlockDecorator}) calls the same static
 *       helpers and threads the result into {@link RenderedBlock#colorsHex()}.</li>
 * </ul>
 *
 * <p>This class deliberately holds only the <b>list-merging</b> logic — it
 * takes already-resolved hex strings plus the two coloring-mode flags
 * and produces an ordered, deduplicated list. Entity-to-color resolution
 * lives in {@code RaplaBuilder.getColorForClassifiable(Classifiable)};
 * keeping it out of the helper means the helper is tier-1 testable with
 * pure {@code String} inputs (no entity stubs).
 *
 * <p>Ordering: event color first (when enabled), then per-allocatable
 * colors in input order. Duplicates collapse to first appearance. Empty
 * input → empty output — callers decide whether to substitute a
 * fallback (the Swing path does; the server path lets the Angular client
 * decide).
 */
public final class BlockColors
{
    private BlockColors() {}

    /**
     * Resolve the hex colour list for a calendar block.
     *
     * @param eventColoringEnabled    {@code true} → consider the event colour
     * @param eventColor              already-resolved hex string, or {@code null} / blank → no event colour
     * @param resourceColoringEnabled {@code true} → consider per-resource colours
     * @param resourceColors          ordered per-allocatable hex strings; {@code null} entries / blank values are dropped; {@code null} list is treated as empty
     * @return immutable, deduplicated list in display order
     */
    public static List<String> resolve(boolean eventColoringEnabled,
                                       String eventColor,
                                       boolean resourceColoringEnabled,
                                       List<String> resourceColors)
    {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (eventColoringEnabled && nonBlank(eventColor))
        {
            out.add(eventColor);
        }
        if (resourceColoringEnabled && resourceColors != null)
        {
            for (String c : resourceColors)
            {
                if (nonBlank(c)) out.add(c);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    private static boolean nonBlank(String s)
    {
        return s != null && !s.isBlank();
    }
}
