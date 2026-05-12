package org.rapla.plugin.calendarview;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;

import java.util.List;

/**
 * Decorator hook for {@link CalendarLayoutEngine} (PRD 030 Phase 4). The
 * engine produces the positional coordinates of each {@link RenderedBlock}
 * (column, slot, time); the decorator fills the display fields
 * ({@link RenderedBlock#colorsHex()}, {@link RenderedBlock#tooltip()}).
 *
 * <p>Decoration logic stays out of the engine so the engine is tier-1
 * testable with light Proxy stubs — pulling colour rules into the engine
 * would force every test to stub {@code Classification} + {@code Attribute}
 * + {@code Category} graphs. The production decorator
 * ({@code RaplaBlockDecorator}) lives in rapla-server and is tested at
 * tier 2 with {@code FacadeTestSupport}.
 *
 * <p>Implementations must be stateless and safe to call from multiple
 * layout passes in a single request. {@link #NOOP} returns empty colors
 * + null tooltip — preserves the pre-Phase-4 engine output.
 */
public interface BlockDecorator
{
    /**
     * @param reservation         the reservation owning this block
     * @param visibleAllocatables for {@link GroupBy#RESOURCE} layouts, the
     *                            single column allocatable; for {@link GroupBy#DAY},
     *                            all allocatables on the reservation
     * @return ordered list of CSS-hex colour strings; first is primary,
     *         further entries mean a striped tile. Empty list = no colour
     *         (caller / renderer chooses default).
     */
    List<String> colorsFor(Reservation reservation, List<Allocatable> visibleAllocatables);

    /**
     * @return locale-aware tooltip text, or {@code null} to omit the tooltip
     *         field from the wire response
     */
    String tooltipFor(Reservation reservation, Appointment appointment);

    /** Empty colour list + null tooltip — preserves pre-Phase-4 engine output. */
    BlockDecorator NOOP = new BlockDecorator()
    {
        @Override public List<String> colorsFor(Reservation r, List<Allocatable> v) { return List.of(); }
        @Override public String tooltipFor(Reservation r, Appointment a) { return null; }
    };
}
