package org.rapla.server.spring.graphql;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.rapla.plugin.timeslot.Timeslot;

/**
 * PRD 097 Phase 5 — the pure layout core behind {@code strips} / {@code segments} / {@code bars} /
 * {@code banner} / {@code timeslot}. No engine, no entities: this is the server-side port of the
 * SPA's proven geometry — {@code segments} = `week-lanes.ts` DayBlock semantics (midnight split,
 * clipped minutes, compact collision lanes incl. the Swing 5-minute collision floor), {@code bars}
 * = `month-chunks.ts` WeekChunk semantics (strip-chunked, longer-first push-down stacking, the
 * EventCalendar algorithm with unit row heights). All indices are 1-based and CSS-grid-ready
 * (decided 2026-07-14): they substitute verbatim into {@code grid-column}/{@code grid-row}.
 *
 * <p>Lane and row are LIST-SCOPED: they are relative to the block list passed in (the query's own
 * §12-gated result page), which is exactly right for rendering that result.
 */
public final class CalendarGridLayout
{
    /** Swing {@code AbstractGroupStrategy.isCollision}: blocks shorter than 5 minutes still claim 5. */
    private static final int COLLISION_FLOOR_MIN = 5;
    private static final int DAY_MIN = 24 * 60;

    private static final String[] WEEKDAY_SHORT = { "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So" };

    private CalendarGridLayout() {}

    /** One rendered day of a strip. {@code index} is 1-based within the strip's day set. */
    public record StripDay(int index, LocalDate date, String label) {}

    /** One day-strip of the grid (a week row). {@code index} is 1-based. */
    public record Strip(int index, List<StripDay> days) {}

    /**
     * Time-column geometry of one block on one day (week-view region). {@code dayIndex} is the
     * 1-based position within the segment's strip day set; minutes are clipped to the day.
     */
    public record Segment(int strip, int dayIndex, int startMin, int endMin, int lane, int laneCount,
            boolean clippedStart, boolean clippedEnd) {}

    /** Day-spanning geometry of one block within one strip (month/band region). */
    public record Bar(int strip, int startDay, int span, int row, boolean clippedStart, boolean clippedEnd) {}

    /** The layout-relevant shadow of a block: its span plus the Rule-B classification. */
    public record BlockSpan(LocalDateTime start, LocalDateTime end, boolean banner) {}

    // ================================================================= strips

    /**
     * The day scaffold: the window snapped OUTWARD to full strip boundaries (Monday-first weeks,
     * decided 2026-07-14 — grid window ⊇ filter window), one strip per week, days restricted to
     * {@code weekdays} (null/empty = all seven). {@code to} is exclusive: a window ending Monday
     * 00:00 includes only the Sunday before. Pure calendar math — §12-clean by construction.
     */
    public static List<Strip> strips(LocalDateTime from, LocalDateTime to, Set<DayOfWeek> weekdays)
    {
        if (from == null || to == null || !to.isAfter(from)) return List.of();
        LocalDate firstDay = from.toLocalDate();
        LocalDate lastDay = to.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? to.toLocalDate().minusDays(1)
                : to.toLocalDate();
        LocalDate gridStart = firstDay.minusDays(firstDay.getDayOfWeek().getValue() - 1L);   // Monday
        LocalDate gridEnd = lastDay.plusDays(7L - lastDay.getDayOfWeek().getValue());        // Sunday

        List<Strip> strips = new ArrayList<>();
        int stripIndex = 1;
        for (LocalDate weekStart = gridStart; !weekStart.isAfter(gridEnd); weekStart = weekStart.plusDays(7))
        {
            List<StripDay> days = new ArrayList<>(7);
            for (int i = 0; i < 7; i++)
            {
                LocalDate date = weekStart.plusDays(i);
                if (weekdays != null && !weekdays.isEmpty() && !weekdays.contains(date.getDayOfWeek())) continue;
                days.add(new StripDay(days.size() + 1, date, label(date)));
            }
            strips.add(new Strip(stripIndex++, List.copyOf(days)));
        }
        return strips;
    }

    private static String label(LocalDate date)
    {
        return WEEKDAY_SHORT[date.getDayOfWeek().getValue() - 1] + " "
                + pad2(date.getDayOfMonth()) + "." + pad2(date.getMonthValue()) + ".";
    }

    private static String pad2(int n)
    {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    /**
     * The day-set drop predicate ({@code ReservationFilter.weekdays}): true iff any calendar day
     * the block covers is in the set. A span of 7+ days always touches.
     */
    public static boolean touchesWeekdays(LocalDateTime start, LocalDateTime end, Set<DayOfWeek> weekdays)
    {
        if (weekdays == null || weekdays.isEmpty() || weekdays.size() == 7) return true;
        if (start == null || end == null) return true;
        LocalDate first = start.toLocalDate();
        LocalDate last = coveredEnd(start, end);
        if (java.time.temporal.ChronoUnit.DAYS.between(first, last) >= 6) return true;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1))
        {
            if (weekdays.contains(day.getDayOfWeek())) return true;
        }
        return false;
    }

    // ================================================================= banner (Rule B)

    /**
     * Rule B (decided 2026-07-14): a block is a banner iff it is whole-day OR a full calendar day
     * lies inside {@code [start, end)} — an end at exactly 00:00 belongs to the previous day. NOT
     * a duration threshold: Mon 16:00 → Tue 16:00 is 24h but covers no full day → grid.
     */
    public static boolean banner(LocalDateTime start, LocalDateTime end, boolean wholeDay)
    {
        if (wholeDay) return true;
        if (start == null || end == null) return false;
        LocalDate firstCandidate = start.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? start.toLocalDate()
                : start.toLocalDate().plusDays(1);
        return !firstCandidate.plusDays(1).atStartOfDay().isAfter(end);
    }

    // ================================================================= timeslot

    /**
     * The block's band label: its start time classified against the server-configured timeslots
     * (Swing {@code TimeslotProvider} config) — the slot with the greatest start not after the
     * block's start; a start before the first slot clamps to the first. Null without config.
     */
    public static String timeslot(LocalDateTime start, List<Timeslot> slots)
    {
        if (start == null || slots == null || slots.isEmpty()) return null;
        int minute = start.getHour() * 60 + start.getMinute();
        Timeslot match = slots.get(0);
        for (Timeslot slot : slots)
        {
            if (slot.getMinuteOfDay() <= minute) match = slot;
            else break;
        }
        return match.getName();
    }

    // ================================================================= segments

    /**
     * Per-block time-column segments over the strips' day set — one segment per covered rendered
     * day, minutes clipped, continuation markers at day edges, compact collision lanes per day
     * (Swing resolveConflicts + mergeSlots). Banner blocks emit no segments and occupy no lanes
     * (they live in the header band). Result is parallel to {@code blocks}.
     */
    public static List<List<Segment>> segments(List<BlockSpan> blocks, List<Strip> strips)
    {
        Map<LocalDate, int[]> dayLookup = dayLookup(strips);
        Map<LocalDate, List<SegBuild>> perDay = new HashMap<>();
        List<List<Segment>> result = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) result.add(new ArrayList<>());

        for (int i = 0; i < blocks.size(); i++)
        {
            BlockSpan block = blocks.get(i);
            if (block.banner() || block.start() == null || block.end() == null) continue;
            LocalDate firstDay = block.start().toLocalDate();
            LocalDate lastDay = coveredEnd(block.start(), block.end());
            for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1))
            {
                int[] pos = dayLookup.get(day);
                if (pos == null) continue;
                int startMin = day.equals(firstDay)
                        ? block.start().getHour() * 60 + block.start().getMinute() : 0;
                int endMin = day.equals(block.end().toLocalDate())
                        ? block.end().getHour() * 60 + block.end().getMinute() : DAY_MIN;
                if (endMin < startMin) endMin = startMin;
                perDay.computeIfAbsent(day, d -> new ArrayList<>()).add(new SegBuild(
                        i, pos[0], pos[1], startMin, endMin,
                        day.isAfter(firstDay), day.isBefore(lastDay)));
            }
        }

        for (List<SegBuild> daySegs : perDay.values())
        {
            daySegs.sort(CalendarGridLayout::byStart);
            List<List<SegBuild>> slots = new ArrayList<>();
            slots.add(new ArrayList<>(daySegs));
            resolveConflicts(slots);
            mergeSlots(slots);
            int laneCount = slots.size();
            for (int lane = 0; lane < slots.size(); lane++)
            {
                for (SegBuild seg : slots.get(lane))
                {
                    result.get(seg.blockIdx).add(new Segment(seg.strip, seg.dayIndex,
                            seg.startMin, seg.endMin, lane + 1, laneCount,
                            seg.clippedStart, seg.clippedEnd));
                }
            }
        }
        for (List<Segment> segs : result)
        {
            segs.sort(java.util.Comparator.comparingInt(Segment::strip).thenComparingInt(Segment::dayIndex));
        }
        return result;
    }

    /** The last calendar day the block covers — an end at exactly 00:00 belongs to the previous day. */
    private static LocalDate coveredEnd(LocalDateTime start, LocalDateTime end)
    {
        LocalDate lastDay = end.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? end.toLocalDate().minusDays(1)
                : end.toLocalDate();
        return lastDay.isBefore(start.toLocalDate()) ? start.toLocalDate() : lastDay;
    }

    private static Map<LocalDate, int[]> dayLookup(List<Strip> strips)
    {
        Map<LocalDate, int[]> lookup = new HashMap<>();
        for (Strip strip : strips)
        {
            for (StripDay day : strip.days())
            {
                lookup.put(day.date(), new int[] { strip.index(), day.index() });
            }
        }
        return lookup;
    }

    private static final class SegBuild
    {
        final int blockIdx;
        final int strip;
        final int dayIndex;
        final int startMin;
        final int endMin;
        final boolean clippedStart;
        final boolean clippedEnd;

        SegBuild(int blockIdx, int strip, int dayIndex, int startMin, int endMin,
                boolean clippedStart, boolean clippedEnd)
        {
            this.blockIdx = blockIdx;
            this.strip = strip;
            this.dayIndex = dayIndex;
            this.startMin = startMin;
            this.endMin = endMin;
            this.clippedStart = clippedStart;
            this.clippedEnd = clippedEnd;
        }
    }

    private static int byStart(SegBuild a, SegBuild b)
    {
        int c = Integer.compare(a.startMin, b.startMin);
        return c != 0 ? c : Integer.compare(b.endMin, a.endMin);
    }

    private static boolean collides(SegBuild a, SegBuild b)
    {
        int endA = Math.max(a.endMin, a.startMin + COLLISION_FLOOR_MIN);
        int endB = Math.max(b.endMin, b.startMin + COLLISION_FLOOR_MIN);
        return a.startMin < endB && b.startMin < endA;
    }

    /**
     * Swing {@code AbstractGroupStrategy.resolveConflicts}: within each slot, blocks colliding
     * with an earlier one move to ONE new slot inserted right after it; the new slot is itself
     * revisited, so cascading conflicts keep opening further lanes.
     */
    private static void resolveConflicts(List<List<SegBuild>> slots)
    {
        int pos = 0;
        while (pos < slots.size())
        {
            List<SegBuild> slot = slots.get(pos++);
            List<SegBuild> newSlot = null;
            int i = 0;
            while (i < slot.size())
            {
                SegBuild first = slot.get(i++);
                int j = i;
                while (j < slot.size())
                {
                    SegBuild other = slot.get(j++);
                    if (collides(first, other))
                    {
                        slot.remove(--j);
                        if (newSlot == null)
                        {
                            newSlot = new ArrayList<>();
                            slots.add(pos, newSlot);
                        }
                        newSlot.add(other);
                    }
                }
            }
        }
    }

    /** Swing {@code canMerge}: sorted-walk pairwise collision check between two slots. */
    private static boolean canMerge(List<SegBuild> slot1, List<SegBuild> slot2)
    {
        int i = 0;
        int j = 0;
        while (i < slot1.size() && j < slot2.size())
        {
            SegBuild b1 = slot1.get(i);
            SegBuild b2 = slot2.get(j);
            if (collides(b1, b2)) return false;
            if (b1.startMin < b2.startMin) i++;
            else j++;
        }
        return true;
    }

    /** Swing {@code mergeSlots}: greedy merge of non-colliding slots (compact mode). */
    private static void mergeSlots(List<List<SegBuild>> slots)
    {
        int pos = 0;
        while (pos < slots.size())
        {
            List<SegBuild> slot1 = slots.get(pos++);
            for (int i = pos; i < slots.size(); i++)
            {
                if (canMerge(slot1, slots.get(i)))
                {
                    slot1.addAll(slots.get(i));
                    slot1.sort(CalendarGridLayout::byStart);
                    slots.remove(i);
                    pos--;
                    break;
                }
            }
        }
    }

    // ================================================================= bars

    /**
     * Per-block day-spanning bars, chunked at strip boundaries AND day-set gaps (a Fr → Mo bar in
     * a Mo–Fr grid clips at Friday and continues Monday of the next strip), stacked longer-first
     * with push-down (`month-chunks.ts` / EventCalendar, unit row heights). {@code bannerOnly}
     * restricts participation to banner blocks (the week template's header band); the two scopes
     * stack independently. Result is parallel to {@code blocks}.
     */
    public static List<List<Bar>> bars(List<BlockSpan> blocks, List<Strip> strips, boolean bannerOnly)
    {
        List<List<Bar>> result = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) result.add(new ArrayList<>());

        for (Strip strip : strips)
        {
            List<BarBuild> chunks = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++)
            {
                BlockSpan block = blocks.get(i);
                if (bannerOnly && !block.banner()) continue;
                if (block.start() == null || block.end() == null) continue;
                LocalDate firstDay = block.start().toLocalDate();
                LocalDate lastDay = coveredEnd(block.start(), block.end());
                int firstIdx = 0;
                int matched = 0;
                LocalDate firstMatched = null;
                LocalDate lastMatched = null;
                for (StripDay day : strip.days())
                {
                    if (day.date().isBefore(firstDay) || day.date().isAfter(lastDay)) continue;
                    if (matched == 0)
                    {
                        firstIdx = day.index();
                        firstMatched = day.date();
                    }
                    matched++;
                    lastMatched = day.date();
                }
                if (matched == 0) continue;
                chunks.add(new BarBuild(i, firstIdx, matched,
                        firstDay.isBefore(firstMatched), lastDay.isAfter(lastMatched)));
            }
            stack(chunks);
            for (BarBuild chunk : chunks)
            {
                result.get(chunk.blockIdx).add(new Bar(strip.index(), chunk.col, chunk.span,
                        chunk.row, chunk.clippedStart, chunk.clippedEnd));
            }
        }
        for (List<Bar> bars : result)
        {
            bars.sort(java.util.Comparator.comparingInt(Bar::strip));
        }
        return result;
    }

    private static final class BarBuild
    {
        final int blockIdx;
        final int col;
        final int span;
        final boolean clippedStart;
        final boolean clippedEnd;
        int row;
        boolean placed;

        BarBuild(int blockIdx, int col, int span, boolean clippedStart, boolean clippedEnd)
        {
            this.blockIdx = blockIdx;
            this.col = col;
            this.span = span;
            this.clippedStart = clippedStart;
            this.clippedEnd = clippedEnd;
        }
    }

    /**
     * The EventCalendar stacking (`month-chunks.ts chunkWeek`) with unit row heights: multi-day
     * chunks first (longer first, stable), then singles in list order; each lands below the
     * previous chunk starting in its column, pushed further down below any placed longer chunk
     * passing through the column. Rows are 1-based.
     */
    private static void stack(List<BarBuild> chunks)
    {
        List<BarBuild> ordered = new ArrayList<>();
        List<BarBuild> multi = new ArrayList<>();
        List<BarBuild> singles = new ArrayList<>();
        for (BarBuild c : chunks)
        {
            (c.span > 1 ? multi : singles).add(c);
        }
        multi.sort((a, b) -> Integer.compare(b.span, a.span));
        ordered.addAll(multi);
        ordered.addAll(singles);

        Map<Integer, BarBuild> prevChunks = new HashMap<>();
        Map<Integer, List<BarBuild>> longChunks = new HashMap<>();
        for (BarBuild c : ordered)
        {
            for (int i = 1; i < c.span; i++)
            {
                longChunks.computeIfAbsent(c.col + i, k -> new ArrayList<>()).add(c);
            }
            BarBuild prev = prevChunks.put(c.col, c);
            int row = prev != null ? prev.row + 1 : 1;
            List<BarBuild> passing = new ArrayList<>(longChunks.getOrDefault(c.col, List.of()));
            passing.removeIf(lc -> !lc.placed);
            passing.sort(java.util.Comparator.comparingInt(lc -> lc.row));
            for (BarBuild lc : passing)
            {
                if (row == lc.row) row = lc.row + 1;
            }
            c.row = row;
            c.placed = true;
        }
    }
}
