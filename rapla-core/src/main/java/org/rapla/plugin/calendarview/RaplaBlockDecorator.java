package org.rapla.plugin.calendarview;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Production {@link BlockDecorator} for {@link CalendarLayoutEngine}
 * (PRD 030 Phase 4).
 *
 * <p>Mirrors the per-block colour resolution that
 * {@code RaplaBlock.getColorsAsHex()} performs on the Swing tier — but
 * without a Swing-built {@code BuildContext}. Constructor flags carry the
 * user's {@code CalendarOptions} (event-coloring on/off, resource-coloring
 * on/off); colour extraction itself goes through the shared
 * {@link BlockColors#resolve} + the static
 * {@link RaplaBuilder#getColorForClassifiable(org.rapla.entities.dynamictype.Classifiable)}
 * helper, so the Swing and server paths produce byte-for-byte identical
 * colour lists for the same inputs.
 *
 * <p>Tooltip is intentionally not server-rendered — locale-aware tooltip
 * formatting lives in {@code AppointmentInfoUI} on the Swing tier and is
 * heavy. Angular clients build tooltips client-side from the wire fields
 * ({@code name}, {@code start}, {@code end}, …). Override {@link #tooltipFor}
 * if a server-rendered tooltip is later desired.
 */
public final class RaplaBlockDecorator implements BlockDecorator
{
    private final boolean eventColoring;
    private final boolean resourceColoring;

    public RaplaBlockDecorator(boolean eventColoring, boolean resourceColoring)
    {
        this.eventColoring = eventColoring;
        this.resourceColoring = resourceColoring;
    }

    @Override
    public List<String> colorsFor(Reservation reservation, List<Allocatable> visibleAllocatables)
    {
        String eventColor = reservation == null ? null : RaplaBuilder.getColorForClassifiable(reservation);
        List<String> resourceColors;
        if (visibleAllocatables == null || visibleAllocatables.isEmpty())
        {
            resourceColors = List.of();
        }
        else
        {
            resourceColors = new ArrayList<>(visibleAllocatables.size());
            for (Allocatable a : visibleAllocatables)
            {
                resourceColors.add(a == null ? null : RaplaBuilder.getColorForClassifiable(a));
            }
        }
        return BlockColors.resolve(eventColoring, eventColor, resourceColoring, resourceColors);
    }

    @Override
    public String tooltipFor(Reservation reservation, Appointment appointment)
    {
        return null;
    }
}
