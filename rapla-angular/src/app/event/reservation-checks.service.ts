import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, catchError, map, of, switchMap } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
import { conflictDetails } from './reservation-warnings';
import { toReservationInput, type EventDraft } from './event-draft';
import {
  ReservationWarningsDialogComponent,
  type ConflictDetail,
} from './reservation-warnings-dialog.component';

/** One selection shape for both check fields — they return the same warning rows. */
const WARNING_FRAGMENT = `
  fragment warning on ReservationWarning {
    code
    args
    severity
    conflicts { allocatable { name } reservation2 { name } startDate }
  }`;
import { saveGate, type ReservationWarning } from './reservation-warnings';

/**
 * PRD 105 Phase 3 — the pre-save check, one roundtrip before the save (D1: a dry run, like Swing's
 * `EventCheck` chain before `dispatch`; the save contract stays untouched).
 *
 * A failing check must never block saving: if the field errors (older server, transport hiccup),
 * the SPA proceeds exactly as it did before this feature — a warning system that turns into an
 * outage is worse than no warning system.
 */
@Injectable({ providedIn: 'root' })
export class ReservationChecksService {
  private readonly gql = inject(GraphqlService);
  private readonly dialog = inject(MatDialog);

  check(draft: EventDraft, scopeAllocatableIds: string[] = []): Observable<ReservationWarning[]> {
    const input = {
      draft: { id: draft.id, ...toReservationInput(draft) },
      scopeAllocatableIds,
    };
    return this.gql
      .query<{ reservationChecks: ReservationWarning[] }>(
        `query ($input: ReservationCheckInput!) {
           reservationChecks(input: $input) { ...warning }
         }
         ${WARNING_FRAGMENT}`,
        { input },
      )
      .pipe(
        map((resp) => (resp.errors?.length ? [] : (resp.data?.reservationChecks ?? []))),
        catchError(() => of([])),
      );
  }

  /**
   * The funnel EVERY write path goes through (Swing: `ReservationControllerImpl.checkEvents` is
   * called from each write path and always opens the same dialog). Emits true when the write may
   * proceed: no findings at all, or the user confirmed the confirmable ones. A blocking finding
   * always emits false.
   */
  confirm(draft: EventDraft, scopeAllocatableIds: string[] = []): Observable<boolean> {
    return this.check(draft, scopeAllocatableIds).pipe(
      switchMap((warnings) =>
        saveGate(warnings) === 'save' ? of(true) : this.ask(warnings, conflictDetails(warnings)),
      ),
    );
  }

  /** Open the shared dialog for findings that did not come from a full draft check (drag/resize). */
  ask(warnings: ReservationWarning[], conflicts: ConflictDetail[]): Observable<boolean> {
    return this.dialog
      .open(ReservationWarningsDialogComponent, {
        data: { warnings, conflicts },
        width: '560px',
        maxWidth: '95vw',
        autoFocus: false,
      })
      .afterClosed()
      .pipe(map((proceed) => proceed === true));
  }

  /**
   * PRD 105 — the drag/resize twin of {@link #confirm}: the server computes the state the move verb
   * WOULD store and checks that, so the SPA never rebuilds move semantics (PRD 101). `input` comes
   * from `moveCheckInput(...)`; a gesture without a dry-run passes null and proceeds unchecked.
   */
  confirmMove(input: Record<string, unknown> | null): Observable<boolean> {
    if (!input) return of(true);
    return this.gql
      .query<{ moveChecks: ReservationWarning[] }>(
        `query ($input: MoveCheckInput!) {
           moveChecks(input: $input) { ...warning }
         }
         ${WARNING_FRAGMENT}`,
        { input },
      )
      .pipe(
        map((resp) => (resp.errors?.length ? [] : (resp.data?.moveChecks ?? []))),
        catchError(() => of([] as ReservationWarning[])),
        switchMap((warnings) =>
          saveGate(warnings) === 'save' ? of(true) : this.ask(warnings, conflictDetails(warnings)),
        ),
      );
  }
}
