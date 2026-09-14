package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rapla.plugin.timeslot.Timeslot;

/**
 * PRD 097 Phase 5 — pure layout core (no Spring, no entities): the server-side port of the SPA's
 * proven geometry (`week-lanes.ts` DayBlock semantics → {@code segments}, `month-chunks.ts`
 * WeekChunk semantics → {@code bars}) plus the Rule-B banner classifier, the strip scaffold and
 * the timeslot band label. All indices are 1-based and CSS-grid-ready (decided 2026-07-14).
 */
class CalendarGridLayoutTest
{
    private static final Set<DayOfWeek> ALL_DAYS = EnumSet.allOf(DayOfWeek.class);
    private static final Set<DayOfWeek> MO_FR = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private static LocalDateTime t(String iso)
    {
        return LocalDateTime.parse(iso);
    }

    // ================================================================= strips

    @Nested
    class Strips
    {
        @Test
        void aWeekWindowIsOneStripOfSevenDays()
        {
            List<CalendarGridLayout.Strip> strips = CalendarGridLayout.strips(
                    t("2026-07-13T00:00:00"), t("2026-07-20T00:00:00"), ALL_DAYS);
            assertEquals(1, strips.size());
            CalendarGridLayout.Strip strip = strips.get(0);
            assertEquals(1, strip.index());
            assertEquals(7, strip.days().size());
            assertEquals(1, strip.days().get(0).index());
            assertEquals(LocalDate.of(2026, 7, 13), strip.days().get(0).date());
            assertEquals(7, strip.days().get(6).index());
            assertEquals(LocalDate.of(2026, 7, 19), strip.days().get(6).date());
        }

        @Test
        void aMonthWindowSnapsOutwardToFullWeeks()
        {
            // July 2026: 1st is a Wednesday → padding back to Mo 29.06.;
            // 31st is a Friday → padding forward to So 02.08. — five strips.
            List<CalendarGridLayout.Strip> strips = CalendarGridLayout.strips(
                    t("2026-07-01T00:00:00"), t("2026-08-01T00:00:00"), ALL_DAYS);
            assertEquals(5, strips.size());
            assertEquals(LocalDate.of(2026, 6, 29), strips.get(0).days().get(0).date());
            assertEquals(LocalDate.of(2026, 8, 2), strips.get(4).days().get(6).date());
        }

        @Test
        void anExclusiveMidnightEndDoesNotAddAWeek()
        {
            // to = Monday 00:00 exclusive → the last included day is the Sunday before.
            List<CalendarGridLayout.Strip> strips = CalendarGridLayout.strips(
                    t("2026-07-13T00:00:00"), t("2026-07-27T00:00:00"), ALL_DAYS);
            assertEquals(2, strips.size());
        }

        @Test
        void weekdaysRestrictTheDaySet()
        {
            List<CalendarGridLayout.Strip> strips = CalendarGridLayout.strips(
                    t("2026-07-13T00:00:00"), t("2026-07-20T00:00:00"), MO_FR);
            assertEquals(1, strips.size());
            List<CalendarGridLayout.StripDay> days = strips.get(0).days();
            assertEquals(5, days.size());
            assertEquals(1, days.get(0).index());
            assertEquals(5, days.get(4).index());
            assertEquals(LocalDate.of(2026, 7, 17), days.get(4).date());
        }

        @Test
        void labelsCarryWeekdayAndDate()
        {
            List<CalendarGridLayout.Strip> strips = CalendarGridLayout.strips(
                    t("2026-07-13T00:00:00"), t("2026-07-20T00:00:00"), ALL_DAYS);
            assertEquals("Mo 13.07.", strips.get(0).days().get(0).label());
            assertEquals("So 19.07.", strips.get(0).days().get(6).label());
        }

        @Test
        void anEmptyOrInvertedWindowYieldsNoStrips()
        {
            assertTrue(CalendarGridLayout.strips(
                    t("2026-07-13T00:00:00"), t("2026-07-13T00:00:00"), ALL_DAYS).isEmpty());
            assertTrue(CalendarGridLayout.strips(
                    t("2026-07-20T00:00:00"), t("2026-07-13T00:00:00"), ALL_DAYS).isEmpty());
        }
    }

    // ================================================================= banner (Rule B)

    @Nested
    class Banner
    {
        @Test
        void wholeDayIsAlwaysABanner()
        {
            assertTrue(CalendarGridLayout.banner(
                    t("2026-07-14T10:00:00"), t("2026-07-14T11:00:00"), true));
        }

        @Test
        void twentyFourHoursWithoutACoveredDayIsNotABanner()
        {
            // Mon 16:00 → Tue 16:00 — 24h, but no full calendar day inside → grid.
            assertFalse(CalendarGridLayout.banner(
                    t("2026-07-13T16:00:00"), t("2026-07-14T16:00:00"), false));
        }

        @Test
        void aFullyCoveredMiddleDayMakesABanner()
        {
            // Mon 08:00 → Wed 17:00 — covers all of Tuesday.
            assertTrue(CalendarGridLayout.banner(
                    t("2026-07-13T08:00:00"), t("2026-07-15T17:00:00"), false));
        }

        @Test
        void midnightToMidnightCoversItsDay()
        {
            assertTrue(CalendarGridLayout.banner(
                    t("2026-07-13T00:00:00"), t("2026-07-14T00:00:00"), false));
        }

        @Test
        void aNightShiftKeepsTheGrid()
        {
            assertFalse(CalendarGridLayout.banner(
                    t("2026-07-13T22:00:00"), t("2026-07-14T06:00:00"), false));
        }

        @Test
        void anEveningEndingAtMidnightKeepsTheGrid()
        {
            // end at exactly 00:00 belongs to the previous day — Monday is not covered.
            assertFalse(CalendarGridLayout.banner(
                    t("2026-07-13T08:00:00"), t("2026-07-14T00:00:00"), false));
        }
    }

    // ================================================================= timeslot

    @Nested
    class TimeslotLabel
    {
        private final List<Timeslot> slots = List.of(
                new Timeslot("vormittags", 0),
                new Timeslot("nachmittags", 12 * 60));

        @Test
        void classifiesByBlockStart()
        {
            assertEquals("vormittags", CalendarGridLayout.timeslot(t("2026-07-14T09:00:00"), slots));
            assertEquals("nachmittags", CalendarGridLayout.timeslot(t("2026-07-14T13:00:00"), slots));
            assertEquals("nachmittags", CalendarGridLayout.timeslot(t("2026-07-14T12:00:00"), slots));
        }

        @Test
        void aStartBeforeTheFirstSlotClampsToTheFirst()
        {
            List<Timeslot> late = List.of(new Timeslot("morgens", 6 * 60), new Timeslot("abends", 18 * 60));
            assertEquals("morgens", CalendarGridLayout.timeslot(t("2026-07-14T05:00:00"), late));
        }

        @Test
        void noConfiguredSlotsYieldsNull()
        {
            assertNull(CalendarGridLayout.timeslot(t("2026-07-14T09:00:00"), List.of()));
            assertNull(CalendarGridLayout.timeslot(t("2026-07-14T09:00:00"), null));
        }
    }

    // ================================================================= segments

    @Nested
    class Segments
    {
        private final List<CalendarGridLayout.Strip> week = CalendarGridLayout.strips(
                t("2026-07-13T00:00:00"), t("2026-07-20T00:00:00"), ALL_DAYS);

        private CalendarGridLayout.BlockSpan block(String start, String end)
        {
            return new CalendarGridLayout.BlockSpan(t(start), t(end), false);
        }

        @Test
        void aSingleDayBlockIsOneSegment()
        {
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(
                    List.of(block("2026-07-14T10:00:00", "2026-07-14T12:00:00")), week);
            assertEquals(1, result.size());
            List<CalendarGridLayout.Segment> segs = result.get(0);
            assertEquals(1, segs.size());
            CalendarGridLayout.Segment s = segs.get(0);
            assertEquals(1, s.strip());
            assertEquals(2, s.dayIndex());     // Tuesday
            assertEquals(600, s.startMin());
            assertEquals(720, s.endMin());
            assertEquals(1, s.lane());
            assertEquals(1, s.laneCount());
            assertFalse(s.clippedStart());
            assertFalse(s.clippedEnd());
        }

        @Test
        void overlappingBlocksSplitIntoLanes()
        {
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(List.of(
                    block("2026-07-14T10:00:00", "2026-07-14T12:00:00"),
                    block("2026-07-14T11:00:00", "2026-07-14T13:00:00")), week);
            CalendarGridLayout.Segment a = result.get(0).get(0);
            CalendarGridLayout.Segment b = result.get(1).get(0);
            assertEquals(1, a.lane());
            assertEquals(2, b.lane());
            assertEquals(2, a.laneCount());
            assertEquals(2, b.laneCount());
        }

        @Test
        void nonOverlappingBlocksMergeIntoOneLane()
        {
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(List.of(
                    block("2026-07-14T10:00:00", "2026-07-14T11:00:00"),
                    block("2026-07-14T11:00:00", "2026-07-14T12:00:00")), week);
            assertEquals(1, result.get(0).get(0).lane());
            assertEquals(1, result.get(1).get(0).lane());
            assertEquals(1, result.get(0).get(0).laneCount());
        }

        @Test
        void shortBlocksClaimTheFiveMinuteCollisionFloor()
        {
            // Swing AbstractGroupStrategy.isCollision: < 5-minute blocks still occupy 5 minutes.
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(List.of(
                    block("2026-07-14T10:00:00", "2026-07-14T10:00:00"),
                    block("2026-07-14T10:02:00", "2026-07-14T10:04:00")), week);
            assertEquals(1, result.get(0).get(0).lane());
            assertEquals(2, result.get(1).get(0).lane());
        }

        @Test
        void aMidnightCrosserSplitsWithContinuationMarkers()
        {
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(
                    List.of(block("2026-07-13T22:00:00", "2026-07-14T06:00:00")), week);
            List<CalendarGridLayout.Segment> segs = result.get(0);
            assertEquals(2, segs.size());
            CalendarGridLayout.Segment monday = segs.get(0);
            assertEquals(1, monday.dayIndex());
            assertEquals(22 * 60, monday.startMin());
            assertEquals(24 * 60, monday.endMin());
            assertFalse(monday.clippedStart());
            assertTrue(monday.clippedEnd());
            CalendarGridLayout.Segment tuesday = segs.get(1);
            assertEquals(2, tuesday.dayIndex());
            assertEquals(0, tuesday.startMin());
            assertEquals(6 * 60, tuesday.endMin());
            assertTrue(tuesday.clippedStart());
            assertFalse(tuesday.clippedEnd());
        }

        @Test
        void anEndAtExactlyMidnightBelongsToThePreviousDay()
        {
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(
                    List.of(block("2026-07-13T20:00:00", "2026-07-14T00:00:00")), week);
            List<CalendarGridLayout.Segment> segs = result.get(0);
            assertEquals(1, segs.size());
            assertEquals(1, segs.get(0).dayIndex());
            assertEquals(24 * 60, segs.get(0).endMin());
            assertFalse(segs.get(0).clippedEnd());
        }

        @Test
        void bannerBlocksEmitNoSegmentsAndDoNotOccupyLanes()
        {
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(List.of(
                    new CalendarGridLayout.BlockSpan(
                            t("2026-07-13T00:00:00"), t("2026-07-15T00:00:00"), true),
                    block("2026-07-14T10:00:00", "2026-07-14T12:00:00")), week);
            assertTrue(result.get(0).isEmpty());
            assertEquals(1, result.get(1).get(0).lane());
            assertEquals(1, result.get(1).get(0).laneCount());
        }

        @Test
        void aBlockOnAnExcludedWeekdayHasNoSegments()
        {
            List<CalendarGridLayout.Strip> moFr = CalendarGridLayout.strips(
                    t("2026-07-13T00:00:00"), t("2026-07-20T00:00:00"), MO_FR);
            List<List<CalendarGridLayout.Segment>> result = CalendarGridLayout.segments(
                    List.of(block("2026-07-18T10:00:00", "2026-07-18T12:00:00")), moFr);   // Saturday
            assertTrue(result.get(0).isEmpty());
        }
    }

    // ================================================================= bars

    @Nested
    class Bars
    {
        // July 2026 month grid: strip 1 = Mo 29.06. – So 05.07., strip 2 = Mo 06.07. – So 12.07., …
        private final List<CalendarGridLayout.Strip> month = CalendarGridLayout.strips(
                t("2026-07-01T00:00:00"), t("2026-08-01T00:00:00"), ALL_DAYS);

        private CalendarGridLayout.BlockSpan block(String start, String end)
        {
            return new CalendarGridLayout.BlockSpan(t(start), t(end), false);
        }

        @Test
        void aThreeDayEventInsideOneStripIsOneBar()
        {
            // Fr 03.07. 10:00 → So 05.07. 12:00 — covers Fr/Sa/So of strip 1.
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(
                    List.of(block("2026-07-03T10:00:00", "2026-07-05T12:00:00")), month, false);
            List<CalendarGridLayout.Bar> bars = result.get(0);
            assertEquals(1, bars.size());
            CalendarGridLayout.Bar bar = bars.get(0);
            assertEquals(1, bar.strip());
            assertEquals(5, bar.startDay());
            assertEquals(3, bar.span());
            assertEquals(1, bar.row());
            assertFalse(bar.clippedStart());
            assertFalse(bar.clippedEnd());
        }

        @Test
        void aStripCrosserIsChunkedWithContinuationMarkers()
        {
            // Sa 04.07. → Di 07.07. 12:00 — Sa/So in strip 1, Mo/Di in strip 2.
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(
                    List.of(block("2026-07-04T10:00:00", "2026-07-07T12:00:00")), month, false);
            List<CalendarGridLayout.Bar> bars = result.get(0);
            assertEquals(2, bars.size());
            CalendarGridLayout.Bar first = bars.get(0);
            assertEquals(1, first.strip());
            assertEquals(6, first.startDay());
            assertEquals(2, first.span());
            assertFalse(first.clippedStart());
            assertTrue(first.clippedEnd());
            CalendarGridLayout.Bar second = bars.get(1);
            assertEquals(2, second.strip());
            assertEquals(1, second.startDay());
            assertEquals(2, second.span());
            assertTrue(second.clippedStart());
            assertFalse(second.clippedEnd());
        }

        @Test
        void anEndAtExactlyMidnightDoesNotCoverTheNextDay()
        {
            // Mo 06.07. 10:00 → Di 07.07. 00:00 — last covered day is Monday.
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(
                    List.of(block("2026-07-06T10:00:00", "2026-07-07T00:00:00")), month, false);
            assertEquals(1, result.get(0).size());
            assertEquals(1, result.get(0).get(0).span());
        }

        @Test
        void longerBarsStackFirstAndPushSinglesDown()
        {
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(List.of(
                    block("2026-07-06T10:00:00", "2026-07-06T12:00:00"),                    // single Mo
                    block("2026-07-06T08:00:00", "2026-07-08T17:00:00")), month, false);    // Mo–Mi
            CalendarGridLayout.Bar single = result.get(0).get(0);
            CalendarGridLayout.Bar multi = result.get(1).get(0);
            assertEquals(1, multi.row());     // longer-first
            assertEquals(2, single.row());    // pushed below the multi-day bar
        }

        @Test
        void singlesInDifferentColumnsShareRowOne()
        {
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(List.of(
                    block("2026-07-06T10:00:00", "2026-07-06T12:00:00"),
                    block("2026-07-07T10:00:00", "2026-07-07T12:00:00")), month, false);
            assertEquals(1, result.get(0).get(0).row());
            assertEquals(1, result.get(1).get(0).row());
        }

        @Test
        void bannerScopeStacksBannersOnly()
        {
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(List.of(
                    new CalendarGridLayout.BlockSpan(
                            t("2026-07-06T00:00:00"), t("2026-07-08T00:00:00"), true),
                    block("2026-07-06T10:00:00", "2026-07-06T12:00:00")), month, true);
            assertEquals(1, result.get(0).size());
            assertEquals(1, result.get(0).get(0).row());
            assertTrue(result.get(1).isEmpty());   // non-banner excluded from BANNER scope
        }

        @Test
        void weekdayGapsChunkLikeStripEdges()
        {
            // Mo–Fr day set: a Fr → Mo block clips at Friday and continues Monday of the next strip.
            List<CalendarGridLayout.Strip> moFr = CalendarGridLayout.strips(
                    t("2026-07-13T00:00:00"), t("2026-07-27T00:00:00"), MO_FR);
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(
                    List.of(block("2026-07-17T10:00:00", "2026-07-20T12:00:00")), moFr, false);
            List<CalendarGridLayout.Bar> bars = result.get(0);
            assertEquals(2, bars.size());
            assertEquals(1, bars.get(0).strip());
            assertEquals(5, bars.get(0).startDay());   // Friday
            assertEquals(1, bars.get(0).span());
            assertTrue(bars.get(0).clippedEnd());
            assertEquals(2, bars.get(1).strip());
            assertEquals(1, bars.get(1).startDay());   // Monday
            assertEquals(1, bars.get(1).span());
            assertTrue(bars.get(1).clippedStart());
        }

        @Test
        void aBlockOutsideTheGridHasNoBars()
        {
            List<List<CalendarGridLayout.Bar>> result = CalendarGridLayout.bars(
                    List.of(block("2026-09-01T10:00:00", "2026-09-01T12:00:00")), month, false);
            assertTrue(result.get(0).isEmpty());
        }
    }
}
