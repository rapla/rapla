import type { Observable } from 'rxjs';

import type { MutationResult } from '../graphql/mutation-result';

/**
 * PRD 094 D1 — a committed main-view action as a command with a compensating
 * inverse. `execute()` runs the forward mutation; `undo()` (when present)
 * runs the domain inverse as a NORMAL mutation — through version checks,
 * permissions and validation — never a client state restore. The Swing analog
 * is the global `CommandHistory` (`SaveUndo`/`DeleteBlocksCommand`).
 */
export interface SpaCommand {
  /** Toast text, e.g. '„Physik" gelöscht'. */
  label: string;
  execute(): Observable<MutationResult<unknown>>;
  /** Null = not undoable. Called only after execute() succeeded. */
  undo: (() => Observable<MutationResult<unknown>>) | null;
}
