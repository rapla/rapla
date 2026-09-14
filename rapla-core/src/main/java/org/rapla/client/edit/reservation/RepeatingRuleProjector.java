package org.rapla.client.edit.reservation;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java projection of a {@link Repeating} rule into widget-ready state.
 * Carved out of {@code AppointmentController.RepeatingEditor} so the
 * decision logic (panel visibility, ending mode, day-chooser bucket,
 * exception-count formatting, weekday checkbox state) can be unit-tested
 * without Swing.
 * <p>
 * Methods are static and side-effect-free. The Swing layer reads the
 * returned record and applies the values to JPanels / JCheckBoxes /
 * JComboBoxes.
 */
public final class RepeatingRuleProjector
{
    private RepeatingRuleProjector() {}

    public enum EndingMode { UNTIL, N_TIMES, FOREVER }

    public enum DayChooserMode { SAME_DAY, NEXT_DAY, X_DAYS }

    public enum ExceptionCountStyle { NORMAL, HIGHLIGHTED }

    /**
     * The five repeating-choice radio buttons on the appointment editor.
     * {@code NONE} maps to "no repetition" (single-occurrence appointment);
     * the others map 1:1 to {@link org.rapla.entities.domain.RepeatingType}.
     */
    public enum RepeatingChoice { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

    public record ExceptionButtonState(int count, ExceptionCountStyle style, String label) {}

    public record EndingPanelVisibility(boolean endDateVisible,
                                        boolean endDatePeriodPanelVisible,
                                        boolean numberPanelVisible) {}

    public record RepeatingPanelVisibility(boolean weekdayInMonthPanelVisible,
                                           boolean intervalPanelVisible,
                                           boolean dayInMonthPanelVisible,
                                           boolean startDatePeriodVisible,
                                           boolean endDatePeriodVisible,
                                           boolean weekdaysPanelVisible,
                                           boolean dayLabelVisible,
                                           boolean weekdayChooserVisible,
                                           boolean monthChooserVisible) {}

    public record DayChooserState(DayChooserMode mode, boolean daysVisible, int days) {}

    public record WeekdaySelection(boolean selected, boolean enabled) {}

    public record EndDateBinding(LocalDateTime endDate, LocalDateTime endDatePeriodDate, int number) {}

    /**
     * Formats the exception-count label shown on the exception button.
     * Single-digit counts are padded with surrounding spaces so the
     * button width is stable for 0–8 vs. ≥9.
     */
    public static String formatExceptionCountLabel(String prefix, int count)
    {
        String countValue = String.valueOf(count);
        if (count < 9)
        {
            countValue = " " + countValue + " ";
        }
        return prefix + " (" + countValue + ")";
    }

    public static ExceptionCountStyle styleForExceptionCount(int count)
    {
        return count > 0 ? ExceptionCountStyle.HIGHLIGHTED : ExceptionCountStyle.NORMAL;
    }

    /**
     * Reads exceptions from a {@link Repeating} (null-tolerant) and returns
     * the rendered button state.
     */
    public static ExceptionButtonState exceptionButtonState(String prefix, Repeating repeating)
    {
        LocalDateTime[] exceptions = repeating != null ? repeating.getExceptions() : null;
        int count = exceptions != null ? exceptions.length : 0;
        return new ExceptionButtonState(count, styleForExceptionCount(count), formatExceptionCountLabel(prefix, count));
    }

    /**
     * Maps a repeating rule's end semantics to {@link EndingMode}.
     * <ul>
     *   <li>{@code getEnd() == null}  → FOREVER</li>
     *   <li>{@code getEnd() != null && isFixedNumber()} → N_TIMES</li>
     *   <li>{@code getEnd() != null && !isFixedNumber()} → UNTIL</li>
     * </ul>
     */
    public static EndingMode endingMode(Repeating repeating)
    {
        if (repeating == null || repeating.getEnd() == null)
        {
            return EndingMode.FOREVER;
        }
        return repeating.isFixedNumber() ? EndingMode.N_TIMES : EndingMode.UNTIL;
    }

    /**
     * Maps an {@link EndingMode} to the visibility of the three
     * ending-related panels (end date, end-date period, repeat-count).
     * Mirrors {@code AppointmentController.RepeatingEditor.showEnding(int)}.
     */
    public static EndingPanelVisibility endingPanelVisibility(EndingMode mode, boolean periodVisible)
    {
        return switch (mode)
        {
            case UNTIL    -> new EndingPanelVisibility(true,  periodVisible, false);
            case N_TIMES  -> new EndingPanelVisibility(false, false,         true);
            case FOREVER  -> new EndingPanelVisibility(false, false,         false);
        };
    }

    /**
     * Returns the end-date / period-date / number to bind into the
     * widgets when {@code endingMode != FOREVER}. Returns {@code null}
     * when the repeating has no end (FOREVER case — widgets keep their
     * prior values).
     */
    public static EndDateBinding endDateBinding(Repeating repeating)
    {
        if (repeating == null || repeating.getEnd() == null)
        {
            return null;
        }
        LocalDateTime endDate = DateTools.subDay(repeating.getEnd());
        LocalDateTime endDatePeriodDate = DateTools.cutDate(endDate);
        return new EndDateBinding(endDate, endDatePeriodDate, repeating.getNumber());
    }

    /**
     * Panel visibility flags for the repeating-type cards.
     * Mirrors the {@code weekdayInMonthPanel.setVisible(...)}, …,
     * {@code monthChooser.setVisible(...)} block of
     * {@code mapFromAppointment}.
     */
    public static RepeatingPanelVisibility repeatingPanelVisibility(Repeating repeating, boolean periodVisible)
    {
        if (repeating == null)
        {
            return new RepeatingPanelVisibility(false, false, false, false, false, false, false, false, false);
        }
        boolean weekly = repeating.isWeekly();
        boolean daily = repeating.isDaily();
        boolean monthly = repeating.isMonthly();
        boolean yearly = repeating.isYearly();
        return new RepeatingPanelVisibility(
                monthly,
                daily || weekly,
                yearly,
                periodVisible && (daily || weekly),
                daily || weekly,
                weekly,
                daily,
                weekly || monthly,
                yearly
        );
    }

    /**
     * Maps the day delta between appointment start and end to the
     * day-chooser combobox bucket (SAME_DAY / NEXT_DAY / X_DAYS).
     * Mirrors the {@code daysBetween} switch in {@code mapFromAppointment}.
     */
    public static DayChooserState dayChooserState(long daysBetween)
    {
        if (daysBetween == 0)
        {
            return new DayChooserState(DayChooserMode.SAME_DAY, false, 0);
        }
        if (daysBetween == 1)
        {
            return new DayChooserState(DayChooserMode.NEXT_DAY, false, 1);
        }
        return new DayChooserState(DayChooserMode.X_DAYS, true, (int) daysBetween);
    }

    /**
     * Returns the (selected, enabled) state for each weekday checkbox in
     * a weekly recurrence. The appointment-start weekday is always
     * selected and disabled (it is the anchor — the user cannot opt out
     * of it). Other weekdays reflect {@link Repeating#getWeekdays()}.
     * <p>
     * Returns an empty map when {@code repeating} is null or not weekly —
     * caller should hide the weekdays panel.
     *
     * @param weekdayKeys the weekday-checkbox keys present in the UI
     *                    (typically Mon–Sun in some order); the returned
     *                    map has exactly the same key set.
     */
    public static Map<Integer, WeekdaySelection> weekdaySelections(Repeating repeating,
                                                                   int startWeekday,
                                                                   Set<Integer> weekdayKeys)
    {
        if (repeating == null || !repeating.isWeekly() || weekdayKeys == null || weekdayKeys.isEmpty())
        {
            return Collections.emptyMap();
        }
        Set<Integer> active = repeating.getWeekdays();
        Map<Integer, WeekdaySelection> out = new LinkedHashMap<>();
        for (Integer weekday : weekdayKeys)
        {
            boolean isStart = weekday != null && weekday == startWeekday;
            boolean selected = isStart || (active != null && active.contains(weekday));
            boolean enabled = !isStart;
            out.put(weekday, new WeekdaySelection(selected, enabled));
        }
        return out;
    }

    /**
     * Map a {@link RepeatingType} (or {@code null} for "no repetition")
     * to the corresponding radio-button choice. Used at initial display
     * and on undo/redo of a repeating-type change.
     */
    public static RepeatingChoice choiceFor(RepeatingType type)
    {
        if (type == null) return RepeatingChoice.NONE;
        return switch (type)
        {
            case DAILY   -> RepeatingChoice.DAILY;
            case WEEKLY  -> RepeatingChoice.WEEKLY;
            case MONTHLY -> RepeatingChoice.MONTHLY;
            case YEARLY  -> RepeatingChoice.YEARLY;
        };
    }

    /**
     * Inverse of {@link #choiceFor}. {@code NONE} maps to {@code null} —
     * callers should branch on null to disable the repeating editor.
     */
    public static RepeatingType repeatingTypeFor(RepeatingChoice choice)
    {
        if (choice == null) return null;
        return switch (choice)
        {
            case NONE    -> null;
            case DAILY   -> RepeatingType.DAILY;
            case WEEKLY  -> RepeatingType.WEEKLY;
            case MONTHLY -> RepeatingType.MONTHLY;
            case YEARLY  -> RepeatingType.YEARLY;
        };
    }

    /**
     * Weekday-selection update when the appointment's anchor weekday
     * shifts (typically: user changed the start date, the anchor moves
     * with it). Distinct semantics from {@link #weekdaySelections}:
     * <ul>
     *   <li>If the current weekday set has &gt; 1 entries, keep the
     *       existing selection unchanged.</li>
     *   <li>If &le; 1 entries, select only the new anchor weekday.</li>
     * </ul>
     * The anchor weekday is always enabled=false (cannot opt out — it's
     * implied by the start date).
     * <p>
     * Mirrors {@code AppointmentController.RepeatingEditor.resetWeekdays(int)}.
     */
    public static Map<Integer, WeekdaySelection> weekdaysOnAnchorShift(
            Set<Integer> currentWeekdays,
            int newAnchorWeekday,
            Set<Integer> weekdayKeys)
    {
        if (weekdayKeys == null || weekdayKeys.isEmpty())
        {
            return Collections.emptyMap();
        }
        boolean moreThanOneSelected = currentWeekdays != null && currentWeekdays.size() > 1;
        Map<Integer, WeekdaySelection> out = new LinkedHashMap<>();
        for (Integer weekday : weekdayKeys)
        {
            boolean selected = moreThanOneSelected
                    ? (currentWeekdays.contains(weekday))
                    : (weekday != null && weekday == newAnchorWeekday);
            boolean enabled = weekday == null || weekday != newAnchorWeekday;
            out.put(weekday, new WeekdaySelection(selected, enabled));
        }
        return out;
    }
}
