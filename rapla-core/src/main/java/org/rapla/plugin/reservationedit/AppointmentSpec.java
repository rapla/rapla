package org.rapla.plugin.reservationedit;

import java.time.LocalDateTime;

/**
 * Wire-format DTO for an unsaved appointment in a conflict-check
 * request. {@code recurrence} may be null (single occurrence).
 * <p>
 * Why not reuse the entity {@code Appointment}: entities carry ids and
 * a back-reference to their persisted reservation. The conflict-check
 * endpoint operates on hypothetical schedule slots, not stored entities.
 * <p>
 * Time-zone semantics: {@link LocalDateTime} is timezone-naive — the
 * server interprets relative to its own zone (PRD 014 convention).
 */
public record AppointmentSpec(
        LocalDateTime start,
        LocalDateTime end,
        RecurrenceRule recurrence)
{
}
