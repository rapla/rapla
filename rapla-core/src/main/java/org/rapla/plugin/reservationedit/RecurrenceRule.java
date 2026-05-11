package org.rapla.plugin.reservationedit;

import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingMode;
import org.rapla.entities.domain.RepeatingType;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Wire-format DTO mirroring
 * {@link org.rapla.client.edit.reservation.RepeatingRuleModel}.
 * Sent over {@code POST /edit/validate-recurrence}.
 * <p>
 * Why a separate record instead of reusing {@code RepeatingRuleModel}:
 * the model is keyed off enum types that may carry serialization
 * concerns (Jackson handles Sets / LocalDateTime / enums per Jackson 3
 * defaults — PRD 010); keeping the wire shape as a dedicated record
 * lets us version the wire independently of the in-memory model.
 */
public record RecurrenceRule(
        RepeatingType type,
        int interval,
        Set<Integer> weekdays,
        EndingMode endingMode,
        LocalDateTime endDate,
        int repeatCount,
        LocalDateTime appointmentStart)
{
}
