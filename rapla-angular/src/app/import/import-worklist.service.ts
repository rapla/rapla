import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, shareReplay, switchMap, tap } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
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
  query ($allocatableIds: [ID!]!) {
    externalEventWorklist(allocatableIds: $allocatableIds) {
      groups {
        allocatableId
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
    reservations(filter: { from: $from, to: $to, allocatableIdsIn: $ids, limit: 2000 }) {
      id
      name
      externalId
      firstDate
      allocations { allocatable { id } }
    }
  }
`;

interface WireLinked {
  id: string;
  name: string | null;
  externalId: string | null;
  firstDate: string;
  allocations: { allocatable: { id: string } }[];
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
  allocatableId: string;
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
    id: g.allocatableId,
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
        groupIds: [g.allocatableId],
        groupName: g.name,
        boundReservationId: i.boundReservationId ?? null,
      })),
  );
  return { groups, items };
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
  private readonly chipIds = computed(() =>
    this.filter
      .entries()
      .filter((c) => c.kind === 'resource')
      .map((c) => c.id)
      .sort(),
  );

  /** One-time source-label fetch — independent of chips/window so the button
   *  can show 0/0 on an empty selection instead of vanishing. */
  private ensureMeta(): void {
    if (this.metaLoaded) return;
    this.metaLoaded = true;
    this.http.get<{ sourceName?: string }>('/api/externaleventimport/metadata').subscribe({
      next: (m) => this.sourceName.set(m.sourceName ?? ''),
      error: () => {
        this.metaLoaded = false;
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
        }>(WORKLIST_QUERY, { allocatableIds: ids })
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
                        allocatableIds: r.allocations.map((a) => a.allocatable.id),
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
      .pipe(map((resp) => resp.data?.bindCandidates ?? []));
  }

  /** "Verknüpfen statt neu": stamp an OPEN item onto an existing reservation.
   *  Emits plain true/false — a false carries NO reason by design (§12: every
   *  denial is indistinguishable); the caller shows one neutral message. */
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
