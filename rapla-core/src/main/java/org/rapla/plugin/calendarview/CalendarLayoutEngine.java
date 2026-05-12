package org.rapla.plugin.calendarview;

import org.rapla.components.calendarview.BestFitStrategy;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.BlockContainer;
import org.rapla.components.calendarview.BuildStrategy;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java calendar-layout core. Given a date range, a layout strategy,
 * a {@link GroupBy} mode and a flat list of reservations, runs the
 * existing {@link BuildStrategy} pipeline headlessly and emits a
 * {@link CalendarPage} with pre-positioned {@link RenderedBlock}s.
 * <p>
 * No facade access — caller supplies the reservation list. This keeps the
 * engine tier-1 testable. The rapla-server side provides a thin glue
 * layer that fetches reservations via {@code RaplaFacade}.
 * <p>
 * Strategy → enum mapping:
 * <ul>
 *   <li>{@link LayoutStrategyId#GROUP_START_TIMES} → {@link GroupStartTimesStrategy}</li>
 *   <li>{@link LayoutStrategyId#BEST_FIT} → {@link BestFitStrategy}</li>
 * </ul>
 * <p>
 * GroupBy → column semantics:
 * <ul>
 *   <li>{@code DAY} — one column per day in {@code [from, to)}; the
 *       strategy decides slots within each day-column.</li>
 *   <li>{@code RESOURCE} — one column per allocatable; the strategy is
 *       run once per allocatable, so slots are per-resource.</li>
 * </ul>
 */
public final class CalendarLayoutEngine
{
    private CalendarLayoutEngine() {}

    /**
     * Run the layout. Reservations whose appointments don't intersect
     * {@code [from, to)} are silently skipped.
     *
     * @param from     inclusive start of the visible range
     * @param to       exclusive end of the visible range
     * @param strategy which {@link BuildStrategy} to run server-side
     * @param groupBy  column semantics
     * @param reservations reservations to lay out (already permission-filtered by caller)
     * @param resourceFilter optional — when groupBy=RESOURCE, the columns. Order preserved.
     *                       When {@code null}, all distinct allocatables from {@code reservations}
     *                       are used (sorted by name not enforced here — caller decides order).
     */
    public static CalendarPage layout(LocalDate from,
                                      LocalDate to,
                                      LayoutStrategyId strategy,
                                      GroupBy groupBy,
                                      Collection<Reservation> reservations,
                                      List<Allocatable> resourceFilter)
    {
        return layout(from, to, strategy, groupBy, reservations, resourceFilter,
                null, BlockDecorator.NOOP);
    }

    /**
     * Decorated variant (PRD 030 Phase 4). Same contract as the six-arg
     * overload plus a {@link Locale} used for {@code Reservation.getName(locale)}
     * and a {@link BlockDecorator} consulted per block for
     * {@link RenderedBlock#colorsHex()} / {@link RenderedBlock#tooltip()}.
     */
    public static CalendarPage layout(LocalDate from,
                                      LocalDate to,
                                      LayoutStrategyId strategy,
                                      GroupBy groupBy,
                                      Collection<Reservation> reservations,
                                      List<Allocatable> resourceFilter,
                                      Locale locale,
                                      BlockDecorator decorator)
    {
        if (from == null) throw new IllegalArgumentException("from must not be null");
        if (to == null) throw new IllegalArgumentException("to must not be null");
        if (!to.isAfter(from)) throw new IllegalArgumentException("to must be after from");
        if (strategy == null) throw new IllegalArgumentException("strategy must not be null");
        if (groupBy == null) throw new IllegalArgumentException("groupBy must not be null");
        if (reservations == null) reservations = List.of();
        if (decorator == null) decorator = BlockDecorator.NOOP;

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atStartOfDay();

        List<RenderedBlock> blocks;
        List<Column> columns;
        switch (groupBy)
        {
            case DAY -> {
                columns = buildDayColumns(from, to);
                blocks = layoutByDay(fromDt, toDt, strategy, reservations, columns, locale, decorator);
            }
            case RESOURCE -> {
                List<Allocatable> resources = resourceFilter != null
                        ? resourceFilter
                        : distinctAllocatables(reservations);
                columns = buildResourceColumns(resources, locale);
                blocks = layoutByResource(fromDt, toDt, strategy, reservations, resources, locale, decorator);
            }
            default -> throw new IllegalArgumentException("unsupported groupBy: " + groupBy);
        }
        return new CalendarPage(from, to, groupBy, strategy, columns, blocks);
    }

    // ---------- DAY layout ----------

    private static List<RenderedBlock> layoutByDay(LocalDateTime fromDt, LocalDateTime toDt,
                                                   LayoutStrategyId strategyId,
                                                   Collection<Reservation> reservations,
                                                   List<Column> columns,
                                                   Locale locale,
                                                   BlockDecorator decorator)
    {
        // 1. Expand reservations → InternalBlocks. Each block carries the
        //    visible-allocatables list passed to the decorator's color hook.
        List<InternalBlock> blocks = new ArrayList<>();
        for (Reservation r : reservations)
        {
            List<Allocatable> reservationAllocatables = Arrays.asList(r.getAllocatables());
            for (Appointment a : r.getAppointments())
            {
                List<AppointmentBlock> expanded = new ArrayList<>();
                a.createBlocks(fromDt, toDt, expanded);
                for (AppointmentBlock ab : expanded)
                {
                    blocks.add(new InternalBlock(r, a, ab, reservationAllocatables));
                }
            }
        }

        // 2. Run strategy. AbstractGroupStrategy derives column from
        //    countDays(startDate, blockStart) — so this maps directly to
        //    our DAY columns.
        BuildStrategy strategy = createStrategy(strategyId);
        CapturingContainer capture = new CapturingContainer(columns.size(), locale, decorator);
        @SuppressWarnings({"rawtypes","unchecked"})
        List<Block> rawBlocks = (List) blocks;
        strategy.build(capture, rawBlocks, fromDt);

        // 3. Flatten captured (block, column, slot) tuples → RenderedBlock.
        return capture.flatten();
    }

    private static List<Column> buildDayColumns(LocalDate from, LocalDate to)
    {
        List<Column> cols = new ArrayList<>();
        LocalDate cursor = from;
        int idx = 0;
        while (cursor.isBefore(to))
        {
            cols.add(new Column(cursor.toString(), cursor.toString(), idx));
            cursor = cursor.plusDays(1);
            idx++;
        }
        return cols;
    }

    // ---------- RESOURCE layout ----------

    private static List<RenderedBlock> layoutByResource(LocalDateTime fromDt, LocalDateTime toDt,
                                                       LayoutStrategyId strategyId,
                                                       Collection<Reservation> reservations,
                                                       List<Allocatable> resources,
                                                       Locale locale,
                                                       BlockDecorator decorator)
    {
        Map<Allocatable, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < resources.size(); i++) columnIndex.put(resources.get(i), i);

        // For each resource: collect its blocks, run the strategy, collect
        // per-resource slots. The strategy's day-derivation still runs but
        // we override the captured column to be the resource's index.
        List<RenderedBlock> all = new ArrayList<>();
        for (Allocatable alloc : resources)
        {
            int col = columnIndex.get(alloc);
            // For RESOURCE groupBy the column allocatable is what drives
            // resource coloring on each block in this column.
            List<Allocatable> perColumnAllocatables = List.of(alloc);
            List<InternalBlock> perResource = new ArrayList<>();
            for (Reservation r : reservations)
            {
                if (!hasAllocatable(r, alloc)) continue;
                for (Appointment a : r.getAppointments())
                {
                    List<AppointmentBlock> expanded = new ArrayList<>();
                    a.createBlocks(fromDt, toDt, expanded);
                    for (AppointmentBlock ab : expanded)
                    {
                        perResource.add(new InternalBlock(r, a, ab, perColumnAllocatables));
                    }
                }
            }
            BuildStrategy strategy = createStrategy(strategyId);
            CapturingContainer capture = new CapturingContainer(1, locale, decorator);
            @SuppressWarnings({"rawtypes","unchecked"})
            List<Block> raw = (List) perResource;
            strategy.build(capture, raw, fromDt);
            for (RenderedBlock rb : capture.flatten())
            {
                // strategy emitted column = day-of-week relative to fromDt;
                // override to the resource-column.
                all.add(new RenderedBlock(
                        rb.reservationId(), rb.appointmentId(),
                        col, rb.slotIndex(), rb.slotCount(),
                        rb.start(), rb.end(), rb.colorsHex(),
                        rb.isException(), rb.isRequest(),
                        rb.name(), rb.tooltip()));
            }
        }
        return all;
    }

    private static List<Column> buildResourceColumns(List<Allocatable> resources, Locale locale)
    {
        List<Column> cols = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++)
        {
            Allocatable a = resources.get(i);
            cols.add(new Column(a.getId(), a.getName(locale), i));
        }
        return cols;
    }

    private static List<Allocatable> distinctAllocatables(Collection<Reservation> reservations)
    {
        // LinkedHashMap preserves first-seen order without an explicit comparator.
        Map<String, Allocatable> seen = new LinkedHashMap<>();
        for (Reservation r : reservations)
        {
            for (Allocatable a : r.getAllocatables())
            {
                seen.putIfAbsent(a.getId(), a);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private static boolean hasAllocatable(Reservation r, Allocatable a)
    {
        for (Allocatable owned : r.getAllocatables())
        {
            if (owned.equals(a)) return true;
        }
        return false;
    }

    // ---------- shared helpers ----------

    private static BuildStrategy createStrategy(LayoutStrategyId id)
    {
        // Both strategies default to m_conflictResolving=false; enable so
        // overlapping blocks land in distinct slots (the visible-on-screen
        // semantics callers expect).
        switch (id)
        {
            case GROUP_START_TIMES ->
            {
                GroupStartTimesStrategy s = new GroupStartTimesStrategy();
                s.setResolveConflictsEnabled(true);
                return s;
            }
            case BEST_FIT ->
            {
                BestFitStrategy s = new BestFitStrategy();
                s.setResolveConflictsEnabled(true);
                return s;
            }
            default -> throw new IllegalArgumentException("unknown strategy: " + id);
        }
    }

    /** Internal Block adapter — carries reservation/appointment refs plus the
     *  visible-allocatables list the decorator's color hook needs. */
    private static final class InternalBlock implements Block
    {
        final Reservation reservation;
        final Appointment appointment;
        final AppointmentBlock appointmentBlock;
        final List<Allocatable> visibleAllocatables;

        InternalBlock(Reservation r, Appointment a, AppointmentBlock ab, List<Allocatable> visibleAllocatables)
        {
            this.reservation = r;
            this.appointment = a;
            this.appointmentBlock = ab;
            this.visibleAllocatables = visibleAllocatables;
        }

        @Override public LocalDateTime getStart()
        {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(appointmentBlock.getStart()), ZoneOffset.UTC);
        }
        @Override public LocalDateTime getEnd()
        {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(appointmentBlock.getEnd()), ZoneOffset.UTC);
        }
        @Override public String getName() { return reservation.getName(null); }

        String getName(Locale locale) { return reservation.getName(locale); }

        boolean isException()
        {
            if (appointment.getRepeating() == null) return false;
            return appointment.getRepeating().isException(appointmentBlock.getStart());
        }
    }

    /** {@link BlockContainer} that records every strategy {@code addBlock(block, col, slot)} call. */
    private static final class CapturingContainer implements BlockContainer
    {
        private final List<Captured> captured = new ArrayList<>();
        private final int defaultColumnCount;
        private final Locale locale;
        private final BlockDecorator decorator;

        CapturingContainer(int columnCount, Locale locale, BlockDecorator decorator)
        {
            this.defaultColumnCount = columnCount;
            this.locale = locale;
            this.decorator = decorator;
        }

        @Override public void addBlock(Block bl, int column, int slot)
        {
            if (!(bl instanceof InternalBlock ib))
            {
                throw new IllegalStateException("unexpected block type: " + bl.getClass());
            }
            captured.add(new Captured(ib, column, slot));
        }

        /** Convert captured tuples into wire-format records. Slot counts are
         *  computed per-column as {@code max(slotIndex)+1}. */
        List<RenderedBlock> flatten()
        {
            // Per-column slot count: one pass to find max slot per column.
            Map<Integer, Integer> maxSlotPerColumn = new HashMap<>();
            for (Captured c : captured)
            {
                maxSlotPerColumn.merge(c.column, c.slot, Math::max);
            }
            List<RenderedBlock> out = new ArrayList<>(captured.size());
            for (Captured c : captured)
            {
                int slotCount = maxSlotPerColumn.getOrDefault(c.column, 0) + 1;
                InternalBlock ib = c.block;
                Reservation r = ib.reservation;
                List<String> colors = decorator.colorsFor(r, ib.visibleAllocatables);
                String tooltip = decorator.tooltipFor(r, ib.appointment);
                out.add(new RenderedBlock(
                        r.getId(),
                        ib.appointment.getId(),
                        c.column,
                        c.slot,
                        slotCount,
                        ib.getStart(),
                        ib.getEnd(),
                        colors,
                        ib.isException(),
                        r.getRequestStatus(null) != null,
                        ib.getName(locale),
                        tooltip
                ));
            }
            return out;
        }

        private record Captured(InternalBlock block, int column, int slot) {}
    }
}
