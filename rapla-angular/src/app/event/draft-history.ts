import type { DraftAllocation, DraftAppointment } from './event-draft';

/**
 * PRD 091 D5 — in-sheet undo/redo as a memento (snapshot) stack, pre-save only.
 * Pure TS, no Angular. One entry = one deep-cloned copy of the draft's
 * user-editable content (the `toReservationInput()` boundary: typeKey, values,
 * appointments, allocations — never availability results, pins, view state or
 * the epoch constants id/persisted/lastChanged).
 *
 * Step boundary = one user gesture: pushes with the same non-null coalesceKey
 * merge into the top entry while they arrive within a sliding 1 s window (the
 * entry keeps the OLDEST snapshot — the state before the gesture began).
 * Discrete actions pass null and never coalesce. Cap 50, oldest evicted.
 */

export interface DraftContent {
  typeKey: string;
  values: Record<string, unknown>;
  appointments: DraftAppointment[];
  allocations: DraftAllocation[];
}

interface Entry {
  snapshot: DraftContent;
  label: string;
  key: string | null;
  at: number;
}

const CAP = 50;
const COALESCE_MS = 1000;

function clone(c: DraftContent): DraftContent {
  return structuredClone(c);
}

export class DraftHistory {
  private past: Entry[] = [];
  private future: Entry[] = [];

  /** Record the PRE-mutation state. Call before applying the edit. */
  push(before: DraftContent, label: string, key: string | null, now: number): void {
    this.future = [];
    const top = this.past[this.past.length - 1];
    if (key !== null && top && top.key === key && now - top.at <= COALESCE_MS) {
      top.at = now; // sliding window; the entry keeps the oldest snapshot
      return;
    }
    this.past.push({ snapshot: clone(before), label, key, at: now });
    if (this.past.length > CAP) this.past.shift();
  }

  /** Restore one step back; `current` is captured for redo. Null when empty. */
  undo(current: DraftContent): DraftContent | null {
    const entry = this.past.pop();
    if (!entry) return null;
    this.future.push({ ...entry, snapshot: clone(current) });
    return clone(entry.snapshot);
  }

  /** Restore one step forward; `current` is captured for undo. Null when empty. */
  redo(current: DraftContent): DraftContent | null {
    const entry = this.future.pop();
    if (!entry) return null;
    this.past.push({ ...entry, snapshot: clone(current) });
    return clone(entry.snapshot);
  }

  canUndo(): boolean {
    return this.past.length > 0;
  }

  canRedo(): boolean {
    return this.future.length > 0;
  }

  undoLabel(): string {
    return this.past[this.past.length - 1]?.label ?? '';
  }

  redoLabel(): string {
    return this.future[this.future.length - 1]?.label ?? '';
  }

  depth(): number {
    return this.past.length;
  }

  /** Hard boundary (D5): cleared on load, successful save, reload-discarding. */
  clear(): void {
    this.past = [];
    this.future = [];
  }
}
