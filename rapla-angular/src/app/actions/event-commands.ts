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
