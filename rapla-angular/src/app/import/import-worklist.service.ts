import { HttpClient, type HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, shareReplay, switchMap, tap } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { effectiveCourseIds } from './import-sync-scope';
import { FilterStore } from '../state/filter-store';
import {
  type BindCandidate,
  type DefaultTemplates,
  type LinkedEvent,
  type StagedState,
  type Worklist,
  type WorklistGroup,
  type WorklistItem,
} from './import-models';

/**
 * PRD 104 v2 — read side of the external-event worklist. The GraphQL contract
 * is GENERIC (rapla-app `externalEventWorklist`, backed by the
 * externaleventimport staging engine; the deployment only provides the
 * {@code ExternalEventSnapshotProvider} SPI). This service maps the wire shape
 * onto the source-neutral {@link Worklist} model everything else consumes; the
 * visible source label comes from the generic import metadata endpoint
 * ({@link sourceName} — '' means no import source deployed, which hides the
 * whole import UI = the plugin gate).
 */
/** UNSCOPED on purpose (join safety, 2026-08-11): the linked-reservations join
 *  below concludes "stamped but no Halde row ⇒ no longer in the export" — that
 *  only holds when the worklist covers ALL semesters of the selected Kurse.
 *  The Offen tab filters to the visible semester client-side instead. */
const WORKLIST_QUERY = `
  query ($resourceIds: [ID!]!) {
    externalEventWorklist(resourceIds: $resourceIds) {
      groups {
        resourceId
        name
        counts { open linked changed }
        items { sourceItemId scopeKey state changedSince boundReservationId columns { key value } }
      }
    }
  }
`;

/** 2026-08-11 redesign — "Verknüpft" is sourced from the RESERVATIONS (the
 *  durable `externalid` stamp), not from the volatile Halde rows: bindings must
 *  stay visible after their source row left the Dualis export window. */
const LINKED_QUERY = `
  query ($from: LocalDateTime!, $to: LocalDateTime!, $ids: [ID!]!) {
    reservations(filter: { from: $from, to: $to, resourceIdsIn: $ids, limit: 2000 }) {
      id
      name
      externalId
      firstDate
      allocations { resource { id } }
    }
  }
`;

interface WireLinked {
  id: string;
  name: string | null;
  externalId: string | null;
  firstDate: string;
  allocations: { resource: { id: string } }[];
}

interface WireColumn {
  key: string;
  value: string | null;
}

interface WireItem {
  sourceItemId: string;
  scopeKey: string | null;
  state: 'OPEN' | 'LINKED' | 'CHANGED' | 'ORPHANED' | 'IGNORED';
  changedSince: string | null;
  /** Requested once the server exposes it (halde, in flight) — null until then. */
  boundReservationId?: string | null;
  columns: WireColumn[];
}

interface WireGroup {
  resourceId: string;
  name: string;
  counts: { open: number; linked: number; changed: number };
  items: WireItem[];
}

interface WireWorklist {
  groups: WireGroup[];
}

/** Verwaist REMOVED (user decision 2026-08-11): rows drop out of the Dualis export
 *  simply by leaving the export window — no drift signal. ORPHANED wire rows are
 *  filtered out entirely (tolerated until the server drops the state too). */
const STATE_MAP: Record<Exclude<WireItem['state'], 'ORPHANED'>, StagedState> = {
  OPEN: 'OPEN',
  LINKED: 'BOUND',
  CHANGED: 'BOUND',
  IGNORED: 'IGNORED',
};

function column(item: WireItem, key: string): string | null {
  return item.columns.find((c) => c.key === key)?.value ?? null;
}

function toWorklist(wire: WireWorklist): Worklist {
  const groups: WorklistGroup[] = wire.groups.map((g) => ({
    id: g.resourceId,
    name: g.name,
    scope: 'KURS',
    openCount: g.counts.open,
    boundCount: g.counts.linked + g.counts.changed,
    changedCount: g.counts.changed,
  }));
  const items: WorklistItem[] = wire.groups.flatMap((g) =>
    g.items
      .filter((i): i is WireItem & { state: keyof typeof STATE_MAP } => i.state !== 'ORPHANED')
      .map((i) => ({
        sourceId: i.sourceItemId,
        kind: i.sourceItemId.startsWith('p:') ? ('p' as const) : ('v' as const),
        name: column(i, 'name') ?? i.sourceItemId,
        // RAW source name shown ADDITIONALLY in the dialog lists (user correction
        // 2026-08-11: display-only — never replaces the cleaned name logic).
        fullName: column(i, 'fullName'),
        unit: column(i, 'eventNr'),
        semester: i.scopeKey ?? column(i, 'semester') ?? '',
        state: STATE_MAP[i.state],
        changed: i.state === 'CHANGED',
        changedSince: i.changedSince,
        groupIds: [g.resourceId],
        groupName: g.name,
        boundReservationId: i.boundReservationId ?? null,
      })),
  );
  return { groups, items };
}

/** WP23x C4 — the full source record of one staged item (stagedEventDetails). */
export interface StagedEventDetails {
  sourceItemId: string;
  changedSince: string | null;
  fields: { key: string; value: string | null }[];
}

/** One ranked template suggestion; reason = exact | parent | generic. */
export interface TemplateSuggestion {
  id: string;
  name: string;
  reason: string;
}

@Injectable({ providedIn: 'root' })
export class ImportWorklistService {
  private readonly gql = inject(GraphqlService);
  private readonly http = inject(HttpClient);
  private readonly filter = inject(FilterStore);

  readonly worklist = signal<Worklist | null>(null);

  /** Stamped reservations of the selected Kurse inside the visible window —
   *  the durable "Verknüpft" source (2026-08-11 redesign). */
  readonly linked = signal<LinkedEvent[]>([]);

  /** Source label from the generic metadata endpoint; '' = no import source
   *  deployed → the whole import UI stays hidden (vanilla rapla). */
  readonly sourceName = signal('');

  /** True when the last completed load errored (≠ "0 items" — user rule
   *  2026-08-11: a broken query must never look like nothing-to-do). */
  readonly loadFailed = signal(false);

  /** Visible calendar window (ISO LocalDateTime) — set by the toolbar effect. */
  readonly windowRange = signal<{ from: string; to: string } | null>(null);

  private load$?: Observable<Worklist | null>;
  private loadedKey?: string;
  private lastLoadFailed = false;
  private metaLoaded = false;

  /** Kurs-Scope (2026-08-11 redesign): the worklist is loaded FOR the selected
   *  resource chips — there is no "everything I may book" mode anymore. */
  private readonly resources = inject(ResourceSelectionStore);
  /** dhbwrapla PRD 004 § H5a — group chips stand for their Kurse: the query asks for the
   *  effective Kurse, the same ids the sync button counts. */
  private readonly chipIds = computed(() =>
    effectiveCourseIds(
      this.filter
        .entries()
        .filter((c) => c.kind === 'resource')
        .map((c) => c.id),
      this.resources.resources(),
    ).sort(),
  );

  /** One-time source-label fetch — independent of chips/window so the button
   *  can show 0/0 on an empty selection instead of vanishing. */
  private ensureMeta(): void {
    if (this.metaLoaded) return;
    this.metaLoaded = true;
    this.http.get<{ sourceName?: string }>('/api/externaleventimport/metadata').subscribe({
      next: (m) => this.sourceName.set(m.sourceName ?? ''),
      // PRD 123 D6 — 404 = no import plugin deployed, final for the session; any other
      // failure is retried on the next call.
      error: (e: HttpErrorResponse) => {
        if (e.status === 404) this.sourceName.set('');
        else this.metaLoaded = false;
      },
    });
  }

  ensureLoaded(): Observable<Worklist | null> {
    this.ensureMeta();
    const ids = this.chipIds();
    const win = this.windowRange();
    if (ids.length === 0) {
      this.load$ = undefined;
      this.loadedKey = undefined;
      this.worklist.set(null);
      this.linked.set([]);
      this.loadFailed.set(false);
      return of(null);
    }
    const key = `${win?.from ?? ''}|${win?.to ?? ''}|${ids.join(',')}`;
    // A failed load (e.g. the dev server was mid-restart) must not stick until a
    // page reload — retry whenever the last COMPLETED attempt yielded nothing.
    if (!this.load$ || this.loadedKey !== key || this.lastLoadFailed) {
      this.loadedKey = key;
      const worklist$ = this.gql
        .query<{
          externalEventWorklist: WireWorklist;
        }>(WORKLIST_QUERY, { resourceIds: ids })
        .pipe(
          map((resp) =>
            resp.errors?.length || !resp.data?.externalEventWorklist
              ? null
              : resp.data.externalEventWorklist,
          ),
          catchError(() => of(null)),
        );
      const linked$: Observable<LinkedEvent[] | null> = win
        ? this.gql
            .query<{ reservations: WireLinked[] }>(LINKED_QUERY, {
              from: win.from,
              to: win.to,
              ids,
            })
            .pipe(
              map((resp) =>
                resp.errors?.length || !resp.data?.reservations
                  ? null
                  : resp.data.reservations
                      .filter((r) => !!r.externalId)
                      .map((r) => ({
                        id: r.id,
                        name: r.name ?? r.id,
                        externalId: r.externalId as string,
                        firstDate: r.firstDate,
                        resourceIds: r.allocations.map((a) => a.resource.id),
                      })),
              ),
              // The externalid ANNOTATION alone is not enough — other importers
              // (iCal: holidays) stamp it too. Only ids the server confirms as
              // bound to THE import source (ExternalSyncEntity join) count
              // (holidays-as-verknüpft bug, 2026-08-12).
              switchMap((candidates) =>
                candidates === null || candidates.length === 0
                  ? of(candidates)
                  : this.gql
                      .query<{
                        externalEventLinkedReservationIds: string[];
                      }>(
                        `query ($ids: [ID!]!) { externalEventLinkedReservationIds(reservationIds: $ids) }`,
                        { ids: candidates.map((c) => c.id) },
                      )
                      .pipe(
                        map((resp) => {
                          const boundIds = resp.data?.externalEventLinkedReservationIds;
                          if (resp.errors?.length || !boundIds) return null;
                          const bound = new Set(boundIds);
                          return candidates.filter((c) => bound.has(c.id));
                        }),
                      ),
              ),
              catchError(() => of(null)),
            )
        : of([]);
      this.load$ = forkJoin([worklist$, linked$]).pipe(
        map(([wire, linked]) => {
          const wl = wire ? toWorklist(wire) : null;
          this.lastLoadFailed = wl === null || linked === null;
          this.loadFailed.set(this.lastLoadFailed);
          this.worklist.set(wl);
          this.linked.set(linked ?? []);
          return wl;
        }),
        shareReplay(1),
      );
      this.lastLoadFailed = false;
    }
    return this.load$;
  }

  refresh(): void {
    this.load$ = undefined;
    this.ensureLoaded().subscribe();
  }

  /** PRD 104 v3 — the Sync dialog's Übernehmen: stored reservations from staged items,
   *  one transaction server-side; refreshes the worklist on success. */
  /** Server-resolved default templates (naming rules live in the deployment, not here). */
  defaultTemplates(groupIds: string[]): Observable<DefaultTemplates> {
    const doc = `
      query ($groupIds: [ID!]!) {
        externalEventDefaultTemplates(groupIds: $groupIds) {
          lectureTemplateId lectureTemplateName examTemplateId examTemplateName
        }
      }
    `;
    return this.gql
      .query<{ externalEventDefaultTemplates: DefaultTemplates | null }>(doc, { groupIds })
      .pipe(map((resp) => resp.data?.externalEventDefaultTemplates ?? {}));
  }

  /** Server-ranked bind proposals for an OPEN item inside the given calendar
   *  window (ISO LocalDateTime). Empty means "nothing suitable in this window",
   *  never "no match exists". Ranking is a display order only — no auto-match. */
  bindCandidates(sourceItemId: string, from: string, to: string): Observable<BindCandidate[]> {
    return this.gql
      .query<{ bindCandidates: BindCandidate[] }>(
        `query ($id: ID!, $from: String!, $to: String!) {
           bindCandidates(sourceItemId: $id, from: $from, to: $to) {
             reservationId name firstDate score
           }
         }`,
        { id: sourceItemId, from, to },
      )
      .pipe(
        map((resp) => resp.data?.bindCandidates ?? []),
        // one failing row must not end the dialog's candidate stream (review round 18 M2)
        catchError(() => of([] as BindCandidate[])),
      );
  }

  /** "Verknüpfen statt neu": stamp an OPEN item onto an existing reservation.
   *  Emits plain true/false — a false carries NO reason by design (§12: every
   *  denial is indistinguishable); the caller shows one neutral message. */
  /** WP23x C4 — the item's source record; null when unknown, not visible or on error. */
  stagedEventDetails(sourceItemId: string): Observable<StagedEventDetails | null> {
    return this.gql
      .query<{
        stagedEventDetails: StagedEventDetails | null;
      }>(
        `query ($id: ID!) { stagedEventDetails(sourceItemId: $id) { sourceItemId changedSince fields { key value } } }`,
        { id: sourceItemId },
      )
      .pipe(
        map((resp) => resp.data?.stagedEventDetails ?? null),
        catchError(() => of(null)),
      );
  }

  /** dhbwrapla PRD 004 § H5a — ranked template suggestions (exact | parent | generic), best first. */
  templateSuggestions(groupIds: string[], kind: 'v' | 'p'): Observable<TemplateSuggestion[]> {
    return this.gql
      .query<{
        externalEventTemplateSuggestions: TemplateSuggestion[];
      }>(
        `query ($ids: [ID!]!, $kind: String!) { externalEventTemplateSuggestions(groupIds: $ids, kind: $kind) { id name reason } }`,
        { ids: groupIds, kind },
      )
      .pipe(
        map((resp) => resp.data?.externalEventTemplateSuggestions ?? []),
        catchError(() => of([] as TemplateSuggestion[])),
      );
  }

  /** dhbwrapla PRD 004 § H3a "Übernehmen": re-apply the changed source to its bound reservation.
   *  Plain true/false like bindStagedEvent (§12). */
  applyStagedChanges(sourceItemId: string): Observable<'applied' | 'refused' | 'error'> {
    return this.gql
      .mutate<{
        applyStagedChanges: boolean;
      }>(`mutation ($item: ID!) { applyStagedChanges(sourceItemId: $item) }`, {
        item: sourceItemId,
      })
      .pipe(
        map((result) =>
          result.kind !== 'ok'
            ? 'error'
            : result.data.applyStagedChanges === true
              ? 'applied'
              : 'refused',
        ),
        catchError(() => of('error' as const)),
        tap((outcome) => {
          if (outcome === 'applied') this.refresh();
        }),
      );
  }

  /** H3a "Verwerfen": clear the "changed" marker only (a human has seen it). */
  /** @returns rows cleared, or null on a technical error (review round 18 M3). */
  dismissStagedChanges(sourceItemIds: string[]): Observable<number | null> {
    return this.gql
      .mutate<{
        dismissStagedChanges: number;
      }>(`mutation ($ids: [ID!]!) { dismissStagedChanges(sourceItemIds: $ids) }`, {
        ids: sourceItemIds,
      })
      .pipe(
        map((result) => (result.kind === 'ok' ? result.data.dismissStagedChanges : null)),
        catchError(() => of(null)),
        tap((n) => {
          if (n !== null && n > 0) this.refresh();
        }),
      );
  }

  bindStagedEvent(sourceItemId: string, reservationId: string): Observable<boolean> {
    return this.gql
      .mutate<{
        bindStagedEvent: boolean;
      }>(
        `mutation ($item: ID!, $res: ID!) { bindStagedEvent(sourceItemId: $item, reservationId: $res) }`,
        { item: sourceItemId, res: reservationId },
      )
      .pipe(
        map((result) => result.kind === 'ok' && result.data.bindStagedEvent === true),
        tap((ok) => {
          if (ok) this.refresh();
        }),
      );
  }
}
