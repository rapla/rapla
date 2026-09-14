package org.rapla.client.edit.reservation;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;

import java.time.LocalDateTime;
import java.util.TreeSet;

/**
 * Applies a {@link RepeatingRuleModel} to a live {@link Repeating} entity.
 * <p>
 * This is the inverse of {@link RepeatingRuleProjector} — that one reads
 * a {@code Repeating} for display; this one writes a model back to one.
 * <p>
 * Clamping rules (preserved from
 * {@code AppointmentController.RepeatingEditor.mapToAppointment()}):
 * <ul>
 *   <li>{@code interval} is clamped to {@code ≥ 1}.</li>
 *   <li>{@code repeatCount} is clamped to {@code ≥ 1} when used.</li>
 *   <li>For {@code UNTIL}: if {@code endDate} is null or before the
 *       appointment start, the appointment start is used. The stored
 *       {@code Repeating.setEnd(...)} value is the user-picked date
 *       plus one day (matches the existing UI convention).</li>
 *   <li>For {@code FOREVER}: {@code setEnd(null)} and
 *       {@code setNumber(-1)}.</li>
 *   <li>Weekdays are written only for {@code WEEKLY}; other types
 *       leave any existing weekday set untouched.</li>
 * </ul>
 */
public final class RepeatingRuleWriter
{
    private RepeatingRuleWriter() {}

    /**
     * Writes the {@code model} into {@code target}.
     *
     * @param appointmentStart the start of the appointment that owns the
     *                         repeating — used to clamp the UNTIL end-date.
     */
    public static void writeTo(RepeatingRuleModel model, Repeating target, LocalDateTime appointmentStart)
    {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        if (target == null) throw new IllegalArgumentException("target must not be null");
        if (appointmentStart == null) throw new IllegalArgumentException("appointmentStart must not be null");

        target.setType(model.type());
        target.setInterval(clampMinOne(model.interval()));

        if (model.type() == RepeatingType.WEEKLY)
        {
            target.setWeekdays(new TreeSet<>(model.weekdays()));
        }

        switch (model.endingMode())
        {
            case UNTIL ->
            {
                LocalDateTime end = model.endDate();
                if (end == null || DateTools.countDays(appointmentStart, end) < 0)
                {
                    end = appointmentStart;
                }
                target.setEnd(DateTools.addDay(end));
            }
            case N_TIMES -> target.setNumber(clampMinOne(model.repeatCount()));
            case FOREVER ->
            {
                target.setEnd(null);
                target.setNumber(-1);
            }
        }
    }

    private static int clampMinOne(int v)
    {
        return v < 1 ? 1 : v;
    }
}
