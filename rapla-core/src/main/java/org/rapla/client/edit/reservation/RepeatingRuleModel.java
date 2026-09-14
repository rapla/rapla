package org.rapla.client.edit.reservation;

import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingMode;
import org.rapla.entities.domain.RepeatingType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure-Java view of a {@link org.rapla.entities.domain.Repeating} rule as
 * the editor sees it. Carved out of
 * {@code AppointmentController.RepeatingEditor.mapToAppointment()}.
 * <p>
 * The model holds the user's raw widget reads (interval, ending mode,
 * weekday set, end-date, repeat count). Clamping / defaulting /
 * end-date plus-one is applied by {@link RepeatingRuleWriter#writeTo}
 * when the model is committed back onto a real {@code Repeating}.
 * <p>
 * No Swing imports, no facade access. Re-usable by the future Angular
 * client.
 */
public record RepeatingRuleModel(
        RepeatingType type,
        int interval,
        Set<Integer> weekdays,
        EndingMode endingMode,
        LocalDateTime endDate,
        int repeatCount)
{
    public RepeatingRuleModel
    {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (endingMode == null) throw new IllegalArgumentException("endingMode must not be null");
        // Defensive copy + immutability for the weekday set.
        weekdays = (weekdays == null || weekdays.isEmpty())
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new TreeSet<>(weekdays));
    }
}
