package org.rapla.plugin.reservationedit;

import java.time.LocalDateTime;

/**
 * Wire-format request for {@code POST /edit/expand-blocks} (PRD 026 §B4).
 * <p>
 * The caller passes a (possibly unsaved) {@link AppointmentSpec} —
 * including any recurrence rule — plus a visible time window. The server
 * runs the rules and returns the list of concrete occurrences that fall
 * inside the window.
 * <p>
 * {@code excludeExceptions} defaults to {@code true} (the typical UI
 * preview case). Set false to also receive exception dates back — useful
 * when the SPA wants to render the exceptions for the user to (re)enable.
 */
public record ExpandBlocksRequest(
        AppointmentSpec appointment,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        boolean excludeExceptions)
{
}
