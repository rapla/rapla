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
 */
@Injectable({ providedIn: 'root' })
export class FilterStore {
  private readonly storage = new ScopedStorage(inject(AuthService), KEY);

  // Restored from per-user localStorage so the chosen scope survives a reload
  // and is isolated per account (PRD 089 D2). bindPerUser reloads on identity flip.
  private readonly _entries = signal<FilterEntry[]>(this.storage.load<FilterEntry[]>([]));

  readonly entries = this._entries.asReadonly();
  readonly count = computed(() => this._entries().length);
  readonly isEmpty = computed(() => this._entries().length === 0);

  constructor() {
    bindPerUser(inject(AuthService), () => this._entries.set(this.storage.load<FilterEntry[]>([])));
  }

  replace(entry: FilterEntry): void {
    this._entries.set([entry]);
    this.persist();
  }

  add(entry: FilterEntry): void {
    if (this.has(entry.id)) return;
    this._entries.update((es) => [...es, entry]);
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

  has(id: string): boolean {
    return this._entries().some((e) => e.id === id);
  }

  private persist(): void {
    this.storage.save(this._entries());
  }
}
