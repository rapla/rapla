package org.rapla.plugin.reservationedit;

import java.time.LocalDateTime;

/**
 * Wire-format DTO for one expanded occurrence of an {@link
 * org.rapla.entities.domain.Appointment}. Returned by
 * {@code POST /edit/expand-blocks} (PRD 026 §B4).
 * <p>
 * Each block is a concrete time interval — the appointment's recurrence
 * rules, weekday-flip-on-move semantics, and exception-skip rules
 * (~400 lines of subtle Java in {@code AppointmentImpl.createBlocks})
 * are applied server-side, so the Angular client gets a flat list of
 * (start, end) intervals without re-implementing them in TypeScript.
 * <p>
 * Time-zone semantics: {@link LocalDateTime} is timezone-naive — the
 * server interprets relative to its own zone (PRD 014 convention).
 */
public record AppointmentBlockDto(LocalDateTime start, LocalDateTime end)
{
}
