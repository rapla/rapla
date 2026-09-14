import { Injectable, computed, inject, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';

import type { SpaCommand } from './command';
import type { MutationResult } from '../graphql/mutation-result';

/**
 * PRD 094 D2 (revised 2026-07-07) — the main-view command history.
 *
 * A flat undo/redo stack over committed actions (cap {@link CAP}), surfaced BOTH
 * as the immediate "Rückgängig" toast AND persistent header ↶/↷ buttons that
 * survive paging/scrolling within the session. Each undo/redo runs one entry per
 * click as a compensating/forward mutation — never a client state restore.
 *
 * Drop-on-stale: if an inverse (or a redo's re-execute) fails
 * (CONCURRENT_MODIFICATION / denied / invalid — a foreign edit OR a later own
 * command that moved the token), the entry is dropped with a loud error toast
 * rather than corrupting the stack. This is the same handling depth-1 already
 * needs, applied per entry — the only reason a flat stack is close in cost to a
 * single slot (no guaranteed multi-step consistency is attempted).
 */
const CAP = 5;

@Injectable({ providedIn: 'root' })
export class UndoToastService {
  private readonly snackBar = inject(MatSnackBar);

  /** Fires after every successful execute/undo/redo — views re-query on it. */
  readonly mutated$ = new Subject<void>();

  private readonly past = signal<SpaCommand[]>([]);
  private readonly future = signal<SpaCommand[]>([]);

  readonly canUndo = computed(() => this.past().length > 0);
  readonly canRedo = computed(() => this.future().length > 0);
  readonly undoLabel = computed(() => this.past().at(-1)?.label ?? '');
  readonly redoLabel = computed(() => this.future().at(-1)?.label ?? '');

  /** Run a committed action; on success push it and show the undo toast. */
  run(command: SpaCommand): void {
    command.execute().subscribe((result) => {
      if (result.kind !== 'ok') {
        this.showError(result);
        return;
      }
      this.mutated$.next();
      if (command.undo) {
        this.past.update((s) => [...s, command].slice(-CAP));
        this.future.set([]);
      }
      const ref = this.snackBar.open(command.label, command.undo ? 'Rückgängig' : undefined, {
        duration: command.undo ? 15000 : 5000,
      });
      if (command.undo) ref.onAction().subscribe(() => this.undo());
    });
  }

  /** Undo the top committed action (one per click). */
  undo(): void {
    const command = this.past().at(-1);
    if (!command?.undo) return;
    this.past.update((s) => s.slice(0, -1));
    command.undo().subscribe((result) => {
      if (result.kind !== 'ok') {
        this.showError(result); // drop-on-stale: entry already popped, not requeued
        // PRD 099 OQ2 — a bulk inverse is best-effort: it may have partially
        // applied before failing, so the view must re-query to show reality.
        this.mutated$.next();
        return;
      }
      this.future.update((s) => [...s, command].slice(-CAP));
      this.mutated$.next();
      this.snackBar.open('Rückgängig gemacht', undefined, { duration: 5000 });
    });
  }

  /** Redo the last undone action by re-executing it forward (best-effort). */
  redo(): void {
    const command = this.future().at(-1);
    if (!command) return;
    this.future.update((s) => s.slice(0, -1));
    command.execute().subscribe((result) => {
      if (result.kind !== 'ok') {
        this.showError(result); // drop-on-stale
        return;
      }
      this.past.update((s) => [...s, command].slice(-CAP));
      this.mutated$.next();
    });
  }

  private showError(result: MutationResult<unknown>): void {
    const message =
      result.kind === 'concurrent'
        ? 'Nicht möglich — die Veranstaltung wurde inzwischen geändert.'
        : result.kind === 'denied'
          ? 'Keine Berechtigung.'
          : result.kind === 'invalid'
            ? 'Nicht möglich: ' + result.issues.map((i) => i.message).join('; ')
            : 'Serverfehler — bitte erneut versuchen.';
    this.snackBar.open(message, undefined, { duration: 8000 });
  }
}
