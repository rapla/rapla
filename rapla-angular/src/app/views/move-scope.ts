import type { GraphqlService } from '../graphql/graphql.service';
import type { SpaCommand } from '../actions/command';
import {
  buildMoveCommand,
  buildMoveAppointmentCommand,
  buildResizeAppointmentCommand,
  buildSplitOccurrenceCommand,
  shiftIso,
} from '../actions/event-commands';

/**
 * PRD 101 Phase 5 — the drag/resize scope logic, the move-side sibling of
 * {@link ../views/delete-scope}. A week-grid drop carries only the wire block
 * facts ({@link moveBlockFacts}); the SCOPE (EVENT/SERIE/SINGLE) picks which
 * server verb runs, and the transpose math itself stays server-side (D1) — the
 * SPA never rebuilds an updateReservation payload for a scoped move.
 *
 * Verb mapping (validated against ReservationMutationController):
 * - EVENT  → `moveReservations` (shift every appointment of the reservation)
 * - SERIE  → `moveAppointment`  (shift/resize this one appointment, rule rides along)
 * - SINGLE → `splitOccurrence`  when repeating (detach one occurrence);
 *            `moveAppointment`  when the appointment is non-repeating (nothing to split)
 */
export type MoveScope = 'event' | 'serie' | 'single';

/** Block facts read off a wire row — enough to decide scope + dispatch without a
 *  draft load (the delete flow loads a draft only because it rebuilds the payload
 *  client-side; move keeps that server-side). */
export interface MoveBlockFacts {
  reservationId: string;
  appointmentId: string | null;
  /** ISO LocalDateTime — the grabbed occurrence's start (the block's `start`). */
  occurrence: string;
  repeating: boolean;
  /** The reservation has more than one appointment. */
  multi: boolean;
  isException: boolean;
  canModify: boolean;
  name: string;
}

/** A drop gesture: a time/day shift (move) or a same-start end change (resize). */
export type MoveGesture =
  | { kind: 'move'; totalMinutes: number }
  | { kind: 'resize'; newEnd: string; oldEnd: string };

export interface MoveScopeOption {
  scope: MoveScope;
  label: string;
}

/** Read the block facts from a wire row; null when the mandatory subject/date
 *  facts are missing (custom view without the hidden fields → no move). */
export function moveBlockFacts(row: Record<string, unknown>): MoveBlockFacts | null {
  const reservation = row['reservation'] as Record<string, unknown> | null | undefined;
  const appointment = row['appointment'] as Record<string, unknown> | null | undefined;
  const start = row['start'];
  if (typeof reservation?.['id'] !== 'string' || typeof start !== 'string') return null;
  const count = reservation['appointmentCount'];
  return {
    reservationId: reservation['id'],
    appointmentId: typeof appointment?.['id'] === 'string' ? appointment['id'] : null,
    occurrence: start,
    repeating: appointment?.['repeating'] != null,
    multi: typeof count === 'number' && count > 1,
    isException: row['isException'] === true,
    canModify: reservation['canModify'] === true,
    name: String(row['name'] ?? '') || 'Veranstaltung',
  };
}

/**
 * The scope options offered for a gesture (mirrors {@link deleteScopeOptions}):
 * - MOVE: EVENT always; SERIE only when the appointment is repeating AND the
 *   reservation has siblings (else SERIE ≡ EVENT); SINGLE when repeating OR multi.
 * - RESIZE: no EVENT (Swing bars resizing "all appointments"); SERIE + SINGLE
 *   only when repeating — a non-repeating appointment has one occurrence, so a
 *   single implicit SERIE option (no dialog).
 *
 * When the result has ≤1 option the caller dispatches directly (no dialog).
 */
export function moveScopeOptions(facts: MoveBlockFacts, gesture: MoveGesture): MoveScopeOption[] {
  if (gesture.kind === 'resize') {
    if (facts.repeating) {
      return [
        { scope: 'serie', label: 'Ganze Serie' },
        { scope: 'single', label: 'Nur dieser Termin' },
      ];
    }
    return [{ scope: 'serie', label: 'Ganze Serie' }];
  }
  const options: MoveScopeOption[] = [{ scope: 'event', label: 'Ganze Veranstaltung' }];
  if (facts.repeating && facts.multi) {
    options.push({ scope: 'serie', label: 'Serie (alle Termine dieser Wiederholung)' });
  }
  if (facts.repeating || facts.multi) {
    options.push({ scope: 'single', label: 'Nur dieser Termin' });
  }
  return options;
}

/** Build the {@link SpaCommand} for a chosen scope + gesture. */
export function buildMoveScopeCommand(
  gql: GraphqlService,
  facts: MoveBlockFacts,
  scope: MoveScope,
  gesture: MoveGesture,
): SpaCommand {
  const { appointmentId, occurrence, name, reservationId } = facts;

  if (gesture.kind === 'resize') {
    // No appointment id (or an unexpected EVENT) → can't resize; fall back to a
    // whole-appointment resize is impossible without the id, so no-op guard:
    if (!appointmentId) return buildMoveCommand(gql, reservationId, name, 0);
    if (scope === 'single' && facts.repeating) {
      return buildSplitOccurrenceCommand(gql, appointmentId, name, occurrence, {
        dateTime: { start: occurrence, end: gesture.newEnd },
      });
    }
    return buildResizeAppointmentCommand(
      gql,
      appointmentId,
      name,
      occurrence,
      gesture.oldEnd,
      gesture.newEnd,
    );
  }

  // move
  if (scope === 'event' || !appointmentId) {
    return buildMoveCommand(gql, reservationId, name, gesture.totalMinutes);
  }
  if (scope === 'single' && facts.repeating) {
    return buildSplitOccurrenceCommand(gql, appointmentId, name, occurrence, {
      dateTime: { start: shiftIso(occurrence, gesture.totalMinutes) },
    });
  }
  return buildMoveAppointmentCommand(gql, appointmentId, name, occurrence, gesture.totalMinutes);
}
