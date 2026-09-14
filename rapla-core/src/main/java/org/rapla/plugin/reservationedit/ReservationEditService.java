package org.rapla.plugin.reservationedit;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Edit-time pre-check endpoints (PRD 024).
 * <p>
 * Phase 1 ({@code validate-recurrence}): wraps
 * {@link org.rapla.client.edit.reservation.RepeatingRuleValidator}.
 * Pure rule check — no facade hit, no permission gate beyond the JWT
 * filter. The Swing client today calls the validator in-process; this
 * endpoint exists for the future Angular client.
 * <p>
 * Synchronous return type — same caveat as
 * {@code RemoteLocaleService}: Spring's HttpServiceProxyFactory has no
 * built-in Promise adapter.
 */
@HttpExchange("/api/edit")
public interface ReservationEditService
{
    @PostExchange("/validate-recurrence")
    RecurrenceValidation validateRecurrence(@RequestBody RecurrenceRule rule) throws RaplaException;

    /**
     * Pre-check: for each allocatable in the request, compute which of
     * the supplied appointments would conflict (existing reservation
     * overlap) or be permission-denied. Unknown / unreadable allocatables
     * are silently dropped (AGENTS.md §12).
     */
    @PostExchange("/check-conflicts")
    ConflictReport checkConflicts(@RequestBody ConflictCheckRequest req) throws RaplaException;

    /**
     * Expand an appointment's recurrence rule into concrete occurrences
     * inside a time window (PRD 026 §B4). Server-side implementation of
     * {@link org.rapla.entities.domain.Appointment#createBlocks} — saves
     * the Angular client from re-porting the weekday-flip-on-move logic
     * and exception-skip rules.
     */
    @PostExchange("/expand-blocks")
    java.util.List<AppointmentBlockDto> expandBlocks(@RequestBody ExpandBlocksRequest req) throws RaplaException;
}
