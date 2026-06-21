import { Injectable, computed, signal } from '@angular/core';

export type FilterKind = 'resource' | 'event';

/** One filter clause — a chip in the rail. */
export interface FilterEntry {
  id: string;
  kind: FilterKind;
  label: string;
  color?: string;
}

/**
 * The active filter = what the current view shows (UI label "FILTER:"). The
 * chip rail renders it; a serialized form becomes a {@code ClassificationFilter[]}.
 * Stepping (a plain click in the ResourceSelection) {@link replace}s the whole
 * filter with one entry; the {@code +} button {@link add}s without clearing.
 */
@Injectable({ providedIn: 'root' })
export class FilterStore {
  private readonly _entries = signal<FilterEntry[]>([]);

  readonly entries = this._entries.asReadonly();
  readonly count = computed(() => this._entries().length);
  readonly isEmpty = computed(() => this._entries().length === 0);

  replace(entry: FilterEntry): void {
    this._entries.set([entry]);
  }

  add(entry: FilterEntry): void {
    if (this.has(entry.id)) return;
    this._entries.update((es) => [...es, entry]);
  }

  remove(id: string): void {
    this._entries.update((es) => es.filter((e) => e.id !== id));
  }

  clear(): void {
    this._entries.set([]);
  }

  has(id: string): boolean {
    return this._entries().some((e) => e.id === id);
  }
}
