import { Injectable, computed, inject, signal } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { ScopedStorage, bindPerUser } from './persist';

/** PRD 128 D3 — Planen keeps the old key; the two Prüfen selections get their own. */
const KEYS = {
  plan: 'rapla.scope',
  conflicts: 'rapla.scope.conflicts',
  requests: 'rapla.scope.requests',
} as const;

/** PRD 128 D3 — Planen (Favoriten … Benutzer) and the two Prüfen contexts, each with its own selection. */
export type FilterContext = keyof typeof KEYS;

const CONTEXTS = Object.keys(KEYS) as FilterContext[];

/**
 * A chip's kind. SCOPING kinds (`resource`, `user`; `group` later) narrow the
 * query; `event` is a navigation target, not a scope. See PRD 078 §"Scope".
 * PRD 128 D1/D2 — `conflict` (id `CONFLICT;<resource>;<app1>;<app2>`) and `request` (id {@link requestChipId})
 * scope their resource and focus their appointments.
 */
export type FilterKind = 'resource' | 'event' | 'user' | 'conflict' | 'request';

/** One filter clause — a chip in the rail. */
export interface FilterEntry {
  id: string;
  kind: FilterKind;
  label: string;
  color?: string;
  /** PRD 128 OQ17 — the name of a conflict/request chip's resource (the label names the case). */
  resourceName?: string;
}

/** The resource a chip scopes: a resource chip itself, a conflict/request chip the second part of its id (D1). */
export function scopedResourceId(e: { id: string; kind: string }): string | null {
  if (e.kind === 'resource') return e.id;
  if (e.kind === 'conflict' || e.kind === 'request') return e.id.split(';')[1];
  return null;
}

/** PRD 128 D5 — a request is one reservation × requested resource. */
export function requestChipId(resourceId: string, reservationId: string): string {
  return `REQUEST;${resourceId};${reservationId}`;
}

/** The current data of a restored chip: its label and resource name. */
export type ChipUpdate = string | Pick<FilterEntry, 'label' | 'resourceName'>;

/**
 * The active filter = what the current view shows (UI label "FILTER:"). The
 * chip rail renders it; a serialized form becomes a {@code ClassificationFilter[]}.
 * Stepping (a plain click in the ResourceSelection) {@link replace}s the whole
 * filter with one entry; the {@code +} button {@link add}s without clearing.
 * PRD 127 D6/OQ6 — the entries keep the picker-tree order {@link reconcile} hands in, whatever the click order.
 */
@Injectable({ providedIn: 'root' })
export class FilterStore {
  private readonly auth = inject(AuthService);
  private readonly storage = Object.fromEntries(
    CONTEXTS.map((c) => [c, new ScopedStorage(this.auth, KEYS[c])]),
  ) as Record<FilterContext, ScopedStorage>;

  // Restored from per-user localStorage so the chosen scope survives a reload
  // and is isolated per account (PRD 089 D2). bindPerUser reloads on identity flip.
  private readonly _lists = signal(this.load());
  /** PRD 128 OQ4 — not persisted: a reload always starts in Planen. */
  private readonly _context = signal<FilterContext>('plan');
  /** Tree position per id, from the last {@link reconcile}; ids without one keep their order at the end. */
  private order = new Map<string, number>();

  readonly context = this._context.asReadonly();
  /** The chips of the active context — the others rest (PRD 128 D3/OQ3). */
  readonly entries = computed(() => this._lists()[this._context()]);
  readonly count = computed(() => this.entries().length);
  readonly isEmpty = computed(() => this.entries().length === 0);

  constructor() {
    const loadedFor = this.auth.identity()?.userId ?? null;
    let registered = false;
    bindPerUser(this.auth, () => {
      // The registration run must not reset a context set before the first effect flush.
      if (!registered) {
        registered = true;
        if ((this.auth.identity()?.userId ?? null) === loadedFor) return;
      }
      this.order = new Map();
      this._context.set('plan');
      this._lists.set(this.load());
    });
  }

  setContext(context: FilterContext): void {
    this._context.set(context);
  }

  replace(entry: FilterEntry): void {
    this.write([entry]);
  }

  /** Bulk-replace the whole filter (PRD 099 — the rail's selection mirror). */
  setAll(entries: FilterEntry[]): void {
    this.write(this.sorted(entries));
  }

  add(entry: FilterEntry): void {
    if (this.has(entry.id)) return;
    this.write(this.sorted([...this.entries(), entry]));
  }

  remove(id: string): void {
    this.write(this.entries().filter((e) => e.id !== id));
  }

  clear(): void {
    this.write([]);
  }

  /**
   * Restored chips of {@code context} that no longer resolve are dropped silently; the rest take the current label
   * (PRD 128 OQ4). For Planen the map's order is the picker-tree order (PRD 127 OQ6:
   * resources in server order, then users A–Z).
   */
  reconcile(current: ReadonlyMap<string, ChipUpdate>, context: FilterContext = 'plan'): void {
    if (context === 'plan') this.order = new Map([...current.keys()].map((id, i) => [id, i]));
    const before = this._lists()[context];
    const next = this.sorted(
      before.flatMap((e) => {
        if (e.kind === 'event') return [e];
        const now = current.get(e.id);
        if (now === undefined) return [];
        return [typeof now === 'string' ? { ...e, label: now } : { ...e, ...now }];
      }),
    );
    if (
      next.length === before.length &&
      next.every(
        (e, i) =>
          e.id === before[i].id &&
          e.label === before[i].label &&
          e.resourceName === before[i].resourceName,
      )
    )
      return;
    this.write(next, context);
  }

  /** PRD 123 D8 — "alle wählen": replace, Ctrl adds, all selected → remove them. */
  selectGroup(entries: readonly FilterEntry[], ctrl: boolean): void {
    const ids = new Set(entries.map((e) => e.id));
    const selected = new Set(this.entries().map((c) => c.id));
    const others = this.entries().filter((c) => !ids.has(c.id));
    if (entries.every((e) => selected.has(e.id))) this.setAll(others);
    else this.setAll([...(ctrl ? others : []), ...entries]);
  }

  has(id: string): boolean {
    return this.entries().some((e) => e.id === id);
  }

  private sorted(entries: readonly FilterEntry[]): FilterEntry[] {
    const rank = (e: FilterEntry) => this.order.get(e.id) ?? Infinity;
    return [...entries].sort((a, b) => (rank(a) === rank(b) ? 0 : rank(a) < rank(b) ? -1 : 1));
  }

  private load(): Record<FilterContext, FilterEntry[]> {
    return Object.fromEntries(
      CONTEXTS.map((c) => [c, this.storage[c].load<FilterEntry[]>([])]),
    ) as Record<FilterContext, FilterEntry[]>;
  }

  private write(entries: FilterEntry[], context = this._context()): void {
    this._lists.update((lists) => ({ ...lists, [context]: entries }));
    this.storage[context].save(entries);
  }
}
