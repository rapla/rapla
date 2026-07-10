import { catchError, forkJoin, map, of, switchMap, type Observable } from 'rxjs';

import type { SpaCommand } from './command';
import type { MutationIssue, MutationResult } from '../graphql/mutation-result';
import type { GraphqlService } from '../graphql/graphql.service';
import type { EventDataService } from '../event/event-data.service';
import type { EventDraft } from '../event/event-draft';
import type { DeleteAction } from '../views/delete-scope';

/**
 * PRD 094 Phase 2 — delete commands with compensating inverses.
 *
 * - whole event: `deleteReservations` ⇄ `createReservation` with the captured
 *   full state and the SAME ids (D3 id-first makes the re-create id-stable).
 * - scoped (serie / single): full-state `updateReservation` of the modified
 *   draft ⇄ update back to the captured original. The inverse's
 *   `expectedLastChanged` is captured by reloading right after the forward
 *   save — a third-party edit between delete and undo then fails loudly with
 *   CONCURRENT_MODIFICATION instead of being silently overwritten.
 */
export function buildDeleteCommand(
  gql: GraphqlService,
  data: EventDataService,
  original: EventDraft,
  action: DeleteAction,
): SpaCommand {
  const name = String(original.values['name'] ?? '') || 'Veranstaltung';

  if (action.kind === 'deleteEvent') {
    return {
      label: `„${name}" gelöscht`,
      execute: () =>
        gql.mutate<{ deleteReservations: { overallStatus: string } }>(
          `mutation ($ids: [ID!]!) { deleteReservations(ids: $ids) { overallStatus } }`,
          { ids: [original.id] },
        ) as Observable<MutationResult<unknown>>,
      undo: () => {
        const restored = structuredClone(original);
        restored.persisted = false; // save() routes to createReservation (same ids)
        restored.lastChanged = null;
        return data.save(restored) as Observable<MutationResult<unknown>>;
      },
    };
  }

  return scopedDeleteCommand(data, original, action, name);
}

/**
 * PRD 099 Phase 3 — bulk whole-event delete over a multi-row selection.
 * Forward: ONE `deleteReservations` with all ids. Inverse (OQ2 best-effort):
 * re-create every captured state with the SAME ids; failures don't stop the
 * remaining restores — the aggregate reports them loudly (`invalid` issues)
 * and the history entry is dropped (094 D2 drop-on-stale).
 */
export function buildBulkDeleteCommand(
  gql: GraphqlService,
  data: EventDataService,
  originals: EventDraft[],
): SpaCommand {
  const n = originals.length;
  return {
    label: n === 1 ? '1 Veranstaltung gelöscht' : `${n} Veranstaltungen gelöscht`,
    execute: () =>
      gql.mutate<{ deleteReservations: { overallStatus: string } }>(
        `mutation ($ids: [ID!]!) { deleteReservations(ids: $ids) { overallStatus } }`,
        { ids: originals.map((o) => o.id) },
      ) as Observable<MutationResult<unknown>>,
    undo: () =>
      forkJoin(
        originals.map((original) => {
          const restored = structuredClone(original);
          restored.persisted = false; // save() routes to createReservation (same ids)
          restored.lastChanged = null;
          return (data.save(restored) as Observable<MutationResult<unknown>>).pipe(
            catchError(() =>
              of({ kind: 'transport', message: 'Netzwerkfehler' } as MutationResult<unknown>),
            ),
            map((result) => ({ original, result })),
          );
        }),
      ).pipe(map(aggregateRestore)),
  };
}

function aggregateRestore(
  outcomes: { original: EventDraft; result: MutationResult<unknown> }[],
): MutationResult<unknown> {
  const failed = outcomes.filter((o) => o.result.kind !== 'ok');
  if (failed.length === 0) return { kind: 'ok', data: null };
  const issues: MutationIssue[] = [
    {
      code: 'BULK_UNDO_PARTIAL',
      path: '',
      message: `${outcomes.length - failed.length} von ${outcomes.length} wiederhergestellt`,
    },
    ...failed.map((f) => ({
      code: 'BULK_UNDO_FAILED',
      path: '',
      message: `„${String(f.original.values['name'] ?? '') || 'Veranstaltung'}" nicht wiederhergestellt (${restoreFailureReason(f.result)})`,
    })),
  ];
  return { kind: 'invalid', issues };
}

function restoreFailureReason(result: MutationResult<unknown>): string {
  switch (result.kind) {
    case 'concurrent':
      return 'inzwischen geändert';
    case 'denied':
      return 'keine Berechtigung';
    case 'transport':
      return 'Netzwerkfehler';
    default:
      return 'ungültig';
  }
}

/**
 * PRD 095 Phase 3b / week grid — drag-move of a single-appointment,
 * non-repeating reservation (gate: `isMovableRow`, server re-checks).
 * Forward: `moveReservations` shifting by `shiftMinutes`; inverse: the compensating
 * negative shift (PRD 094 command shape). PRD 101: the minute-shift is expressed as a
 * `reference`/`target` pair against a fixed pivot (delta = target − reference); the
 * pivot is arbitrary since `reference` is an unvalidated arithmetic anchor for a move.
 */
const MOVE_PIVOT = '2000-01-01T00:00:00';

/** Add `minutes` to an ISO LocalDateTime ("YYYY-MM-DDTHH:MM:SS", no zone). The
 *  arithmetic runs in UTC purely to reuse the Date engine — appointments store
 *  zone-less wall-clock times, so a whole-day shift (N·1440) is DST-safe. */
export function shiftIso(iso: string, minutes: number): string {
  const d = new Date(iso + 'Z');
  d.setUTCMinutes(d.getUTCMinutes() + minutes);
  return d.toISOString().slice(0, 19);
}

export function buildMoveCommand(
  gql: GraphqlService,
  reservationId: string,
  name: string,
  shiftMinutes: number,
): SpaCommand {
  const move = (minutes: number) =>
    gql.mutate<{ moveReservations: { overallStatus: string } }>(
      `mutation ($ids: [ID!]!, $ref: LocalDateTime!, $target: Target!) {
         moveReservations(ids: $ids, reference: $ref, target: $target) { overallStatus } }`,
      {
        ids: [reservationId],
        ref: MOVE_PIVOT,
        target: { dateTime: shiftIso(MOVE_PIVOT, minutes) },
      },
    ) as Observable<MutationResult<unknown>>;
  return {
    label: `„${name}" verschoben`,
    execute: () => move(shiftMinutes),
    undo: () => move(-shiftMinutes),
  };
}

/**
 * PRD 101 Phase 5 — SERIE move (or a non-repeating appointment inside a
 * multi-appointment reservation): shift ONE appointment (rule rides along) via
 * `moveAppointment`. `occurrence` is the grabbed block start (drag style — a
 * mid-series grab must send it so the delta is measured from the right block).
 * The inverse is a normal move back: after the forward shift the grabbed block
 * lives at `shifted`, so undo grabs it there and targets the original start.
 */
export function buildMoveAppointmentCommand(
  gql: GraphqlService,
  appointmentId: string,
  name: string,
  occurrence: string,
  shiftMinutes: number,
): SpaCommand {
  const shifted = shiftIso(occurrence, shiftMinutes);
  const move = (occ: string, start: string) =>
    gql.mutate<{ moveAppointment: { id: string } }>(
      `mutation ($id: ID!, $occ: LocalDateTime, $target: ResizableTarget!) {
         moveAppointment(appointmentId: $id, occurrence: $occ, target: $target) { id } }`,
      { id: appointmentId, occ, target: { dateTime: { start } } },
    ) as Observable<MutationResult<unknown>>;
  return {
    label: `„${name}" verschoben`,
    execute: () => move(occurrence, shifted),
    undo: () => move(shifted, occurrence),
  };
}

/**
 * PRD 101 Phase 5 — SERIE resize: change the appointment's duration (start
 * fixed, end moves) via `moveAppointment` with a `dateTime.end`. The server
 * propagates the new duration to every occurrence; the inverse restores the old
 * end. Resize never offers EVENT scope (Swing parity — different appointments
 * have different durations).
 */
export function buildResizeAppointmentCommand(
  gql: GraphqlService,
  appointmentId: string,
  name: string,
  occurrence: string,
  oldEnd: string,
  newEnd: string,
): SpaCommand {
  const resize = (end: string) =>
    gql.mutate<{ moveAppointment: { id: string } }>(
      `mutation ($id: ID!, $occ: LocalDateTime, $target: ResizableTarget!) {
         moveAppointment(appointmentId: $id, occurrence: $occ, target: $target) { id } }`,
      { id: appointmentId, occ: occurrence, target: { dateTime: { start: occurrence, end } } },
    ) as Observable<MutationResult<unknown>>;
  return {
    label: `„${name}" Dauer geändert`,
    execute: () => resize(newEnd),
    undo: () => resize(oldEnd),
  };
}

/**
 * PRD 101 Phase 5 — SINGLE: detach one occurrence via `splitOccurrence` (clone
 * as non-repeating at the target + add an exception on the series). Not
 * undoable in v1 (D6 — the detach permanently mints a new appointment id and
 * writes a series exception; a clean inverse needs the new id + an
 * updateReservation rebuild, which D1 keeps server-side). The `target` is built
 * by the caller (move → shifted start; resize → same start + new end).
 */
export function buildSplitOccurrenceCommand(
  gql: GraphqlService,
  appointmentId: string,
  name: string,
  occurrence: string,
  target: { dateTime: { start: string; end?: string } },
): SpaCommand {
  return {
    label: `„${name}" – Termin geändert`,
    execute: () =>
      gql.mutate<{ splitOccurrence: { id: string } }>(
        `mutation ($id: ID!, $occ: LocalDateTime!, $target: ResizableTarget!) {
           splitOccurrence(appointmentId: $id, occurrence: $occ, target: $target) { id } }`,
        { id: appointmentId, occ: occurrence, target },
      ) as Observable<MutationResult<unknown>>,
    undo: null,
  };
}

function scopedDeleteCommand(
  data: EventDataService,
  original: EventDraft,
  action: DeleteAction & { kind: 'update' },
  name: string,
): SpaCommand {
  let postDeleteLastChanged: string | null = null;
  return {
    label: `Termin aus „${name}" gelöscht`,
    execute: () =>
      (data.save(action.draft) as Observable<MutationResult<unknown>>).pipe(
        switchMap((result) => {
          if (result.kind !== 'ok') return of(result);
          // Capture the post-delete concurrency token for the inverse.
          return data.load(original.id).pipe(
            map((loaded) => {
              postDeleteLastChanged = loaded?.draft.lastChanged ?? null;
              return result;
            }),
          );
        }),
      ),
    undo: () => {
      const restored = structuredClone(original);
      restored.lastChanged = postDeleteLastChanged;
      return data.save(restored) as Observable<MutationResult<unknown>>;
    },
  };
}
