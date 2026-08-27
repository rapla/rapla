import { Injectable, signal } from '@angular/core';

/**
 * PRD 104 v3 "verknüpfen statt neu" — the manual target-pick mode: the sync
 * dialog arms it with an OPEN item, the calendar's next chip click binds that
 * item to the clicked reservation (`bindStagedEvent`). Session-local, like the
 * Parkstreifen.
 */
@Injectable({ providedIn: 'root' })
export class BindPickService {
  /** The open item waiting for its target; null = mode off. */
  readonly pending = signal<{ sourceItemId: string; label: string } | null>(null);

  arm(sourceItemId: string, label: string): void {
    this.pending.set({ sourceItemId, label });
  }

  cancel(): void {
    this.pending.set(null);
  }
}
