import { Injectable, inject, signal } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { GraphqlService } from '../graphql/graphql.service';
import { FilterStore, scopedResourceId, type ChipUpdate } from './filter-store';
import { bindPerUser } from './persist';
import {
  conflictEntry,
  requestEntry,
  type ConflictCount,
  type ConflictWire,
  type RequestWire,
} from './review-tree';

/** PRD 128 D5/OQ7 — what the two section heads need: counts per resource and state, every open request. */
const HEADS_QUERY = `{
  conflictStats(groupBy: [RESOURCE, DISABLED]) { keys { key value } count }
  resourceRequests { resource { id name } reservationId reservation { name } appointments { start } }
}`;

const CONFLICTS_QUERY = `query($f: ConflictFilter) {
  conflicts(filter: $f) {
    id startDate disabled description resource { id name } reservation1 { name } reservation2 { name }
  }
}`;

interface HeadsWire {
  conflictStats: { keys: { key: string; value: string }[]; count: number }[];
  resourceRequests: RequestWire[];
}

/**
 * PRD 128 — the data behind the Konflikte and Ressourcenanfragen sections. The heads load once per SPA start and again
 * on every switch into a Prüfen context (OQ8 interim); a resource's conflicts load when it is opened (D5). On a switch
 * the stored chips of that context are validated against the current data: unresolved ones drop silently (OQ19).
 */
@Injectable({ providedIn: 'root' })
export class ReviewStore {
  private readonly gql = inject(GraphqlService);
  private readonly filter = inject(FilterStore);

  private readonly _counts = signal<ConflictCount[]>([]);
  private readonly _requests = signal<RequestWire[]>([]);
  private readonly _loaded = signal<ReadonlyMap<string, ConflictWire[]>>(new Map());
  private started = false;

  readonly counts = this._counts.asReadonly();
  readonly requests = this._requests.asReadonly();
  readonly loaded = this._loaded.asReadonly();

  constructor() {
    const auth = inject(AuthService);
    const startedFor = auth.identity()?.userId ?? null;
    let registered = false;
    bindPerUser(auth, () => {
      if (!registered) {
        registered = true;
        if ((auth.identity()?.userId ?? null) === startedFor) return;
      }
      this._counts.set([]);
      this._requests.set([]);
      this._loaded.set(new Map());
      if (this.started) this.loadHeads();
    });
  }

  ensureLoaded(): void {
    if (this.started) return;
    this.started = true;
    this.loadHeads();
  }

  loadConflicts(resourceId: string): void {
    if (this._loaded().has(resourceId)) return;
    this.fetchConflicts([resourceId]);
  }

  /** On switching into {@code context}: reload the heads (and the opened resources), then validate its chips. */
  refresh(context: 'conflicts' | 'requests'): void {
    this.started = true;
    this.loadHeads(context === 'requests');
    if (context !== 'conflicts') return;
    const stored = this.filter.entries().map(scopedResourceId);
    const ids = [...new Set([...this._loaded().keys(), ...stored.filter((id) => id !== null)])];
    if (ids.length) this.fetchConflicts(ids, true);
  }

  private loadHeads(validateRequests = false): void {
    this.gql.query<HeadsWire>(HEADS_QUERY).subscribe((resp) => {
      if (!resp.data) return;
      this._counts.set(
        resp.data.conflictStats.map((b) => ({
          resourceId: b.keys.find((k) => k.key === 'RESOURCE')?.value ?? '',
          disabled: b.keys.find((k) => k.key === 'DISABLED')?.value === 'true',
          count: b.count,
        })),
      );
      this._requests.set(resp.data.resourceRequests);
      if (validateRequests)
        this.filter.reconcile(
          new Map<string, ChipUpdate>(
            resp.data.resourceRequests.map((r) => {
              const { id, ...update } = requestEntry(r);
              return [id, update];
            }),
          ),
          'requests',
        );
    });
  }

  private fetchConflicts(resourceIds: string[], validate = false): void {
    this.gql
      .query<{ conflicts: ConflictWire[] }>(CONFLICTS_QUERY, { f: { resourceIdsIn: resourceIds } })
      .subscribe((resp) => {
        if (!resp.data) return;
        const conflicts = resp.data.conflicts;
        const next = new Map(this._loaded());
        for (const id of resourceIds)
          next.set(
            id,
            conflicts.filter((c) => c.resource.id === id),
          );
        this._loaded.set(next);
        if (validate)
          this.filter.reconcile(
            new Map<string, ChipUpdate>(
              conflicts.map((c) => {
                const { id, ...update } = conflictEntry(c);
                return [id, update];
              }),
            ),
            'conflicts',
          );
      });
  }
}
