import { Injectable, computed, inject, signal } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { ScopedStorage, bindPerUser } from './persist';

const KEY = 'rapla.scope';

/**
 * A chip's kind. SCOPING kinds (`resource`, `user`; `group` later) narrow the
 * query; `event` is a navigation target, not a scope. See PRD 078 §"Scope".
 */
export type FilterKind = 'resource' | 'event' | 'user';

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
 * PRD 127 D6/OQ6 — the entries keep the picker-tree order {@link reconcile} hands in, whatever the click order.
 */
@Injectable({ providedIn: 'root' })
export class FilterStore {
  private readonly storage = new ScopedStorage(inject(AuthService), KEY);

  // Restored from per-user localStorage so the chosen scope survives a reload
  // and is isolated per account (PRD 089 D2). bindPerUser reloads on identity flip.
  private readonly _entries = signal<FilterEntry[]>(this.storage.load<FilterEntry[]>([]));
  /** Tree position per id, from the last {@link reconcile}; ids without one keep their order at the end. */
  private order = new Map<string, number>();

  readonly entries = this._entries.asReadonly();
  readonly count = computed(() => this._entries().length);
  readonly isEmpty = computed(() => this._entries().length === 0);

  constructor() {
    bindPerUser(inject(AuthService), () => {
      this.order = new Map();
      this._entries.set(this.storage.load<FilterEntry[]>([]));
    });
  }

  replace(entry: FilterEntry): void {
    this._entries.set([entry]);
    this.persist();
  }

  /** Bulk-replace the whole filter (PRD 099 — the rail's selection mirror). */
  setAll(entries: FilterEntry[]): void {
    this._entries.set(this.sorted(entries));
    this.persist();
  }

  add(entry: FilterEntry): void {
    if (this.has(entry.id)) return;
    this._entries.update((es) => this.sorted([...es, entry]));
    this.persist();
  }

  remove(id: string): void {
    this._entries.update((es) => es.filter((e) => e.id !== id));
    this.persist();
  }

  clear(): void {
    this._entries.set([]);
    this.persist();
  }

  /**
   * Restored resource/user chips that no longer resolve are dropped silently; the rest take the current label. The
   * map's order is the picker-tree order (PRD 127 OQ6: resources in server order, then users A–Z).
   */
  reconcile(current: ReadonlyMap<string, string>): void {
    this.order = new Map([...current.keys()].map((id, i) => [id, i]));
    const next = this.sorted(
      this._entries().flatMap((e) => {
        if (e.kind === 'event') return [e];
        const label = current.get(e.id);
        return label === undefined ? [] : [{ ...e, label }];
      }),
    );
    if (
      next.length === this._entries().length &&
      next.every((e, i) => e.id === this._entries()[i].id && e.label === this._entries()[i].label)
    )
      return;
    this._entries.set(next);
    this.persist();
  }

  /** PRD 123 D8 — "alle wählen": replace, Ctrl adds, all selected → remove them. */
  selectGroup(entries: readonly FilterEntry[], ctrl: boolean): void {
    const ids = new Set(entries.map((e) => e.id));
    const selected = new Set(this._entries().map((c) => c.id));
    const others = this._entries().filter((c) => !ids.has(c.id));
    if (entries.every((e) => selected.has(e.id))) this.setAll(others);
    else this.setAll([...(ctrl ? others : []), ...entries]);
  }

  has(id: string): boolean {
    return this._entries().some((e) => e.id === id);
  }

  private sorted(entries: readonly FilterEntry[]): FilterEntry[] {
    const rank = (e: FilterEntry) => this.order.get(e.id) ?? Infinity;
    return [...entries].sort((a, b) => (rank(a) === rank(b) ? 0 : rank(a) < rank(b) ? -1 : 1));
  }

  private persist(): void {
    this.storage.save(this._entries());
  }
}
