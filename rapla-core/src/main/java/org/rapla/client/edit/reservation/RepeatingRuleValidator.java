package org.rapla.client.edit.reservation;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.RepeatingType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link RepeatingRuleModel} before it is written back onto
 * a {@link org.rapla.entities.domain.Repeating}.
 * <p>
 * Validation is advisory — the {@link RepeatingRuleWriter} clamps
 * out-of-range values (interval &lt; 1, end-date before start, etc.). The
 * validator surfaces them so the UI / REST layer can show the user a
 * warning before the silent clamp happens.
 */
public final class RepeatingRuleValidator
{
    private RepeatingRuleValidator() {}

    /** A single advisory issue. */
    public record Issue(Code code, String detail) {}

    public enum Code
    {
        INTERVAL_LESS_THAN_ONE,
        WEEKLY_WITH_NO_WEEKDAYS,
        UNTIL_END_BEFORE_START,
        N_TIMES_COUNT_LESS_THAN_ONE,
        N_TIMES_COUNT_UNBOUNDED   // sentinel: user picked N_TIMES but left the count at -1
    }

    public record Result(List<Issue> issues)
    {
        public Result { issues = List.copyOf(issues); }
        public boolean isValid() { return issues.isEmpty(); }
    }

    public static Result validate(RepeatingRuleModel model, LocalDateTime appointmentStart)
    {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        if (appointmentStart == null) throw new IllegalArgumentException("appointmentStart must not be null");

        List<Issue> issues = new ArrayList<>();

        if (model.interval() < 1)
        {
            issues.add(new Issue(Code.INTERVAL_LESS_THAN_ONE,
                    "interval=" + model.interval() + " will be clamped to 1"));
        }

        if (model.type() == RepeatingType.WEEKLY && model.weekdays().isEmpty())
        {
            issues.add(new Issue(Code.WEEKLY_WITH_NO_WEEKDAYS,
                    "weekly recurrence with no weekday selected — anchor weekday will be used"));
        }

        switch (model.endingMode())
        {
            case UNTIL ->
            {
                if (model.endDate() == null || DateTools.countDays(appointmentStart, model.endDate()) < 0)
                {
                    issues.add(new Issue(Code.UNTIL_END_BEFORE_START,
                            "end-date is before appointment start; will be snapped to start"));
                }
            }
            case N_TIMES ->
            {
                if (model.repeatCount() == -1)
                {
                    issues.add(new Issue(Code.N_TIMES_COUNT_UNBOUNDED,
                            "N_TIMES selected but repeatCount=-1; clamping to 1"));
                }
                else if (model.repeatCount() < 1)
                {
                    issues.add(new Issue(Code.N_TIMES_COUNT_LESS_THAN_ONE,
                            "repeatCount=" + model.repeatCount() + " will be clamped to 1"));
                }
            }
            case FOREVER -> { /* always valid */ }
        }

        return new Result(issues);
    }
}
