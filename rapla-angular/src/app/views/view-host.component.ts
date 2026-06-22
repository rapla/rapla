import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { GraphqlService, type ViewMeta, type ViewColumn } from '../graphql/graphql.service';
import { renderCell } from '../graphql/view-render';
import { groupByWeekday, groupByColumn } from '../graphql/weekday-grouping';
import { ViewStateStore, type DateWindow } from '../state/view-state-store';
import { FilterStore, type FilterEntry } from '../state/filter-store';
import { resolveWindowFromInputs } from './view-inputs';
import { buildVariablesByType } from './variable-binder';
import { LastViewStore } from './last-view-store';
import { formatGroupLabel } from './group-format';
import { isProjectedView, projectRow } from './stat-projection';

/** A rendered section: rows sharing one group value (or one flat group when ungrouped). */
interface Section {
  id: string;
  label: string;
  rows: Record<string, unknown>[];
}

/** The data envelope — root-field-agnostic: we take whatever array `data` carries
 *  (appointmentBlocks, appointmentBlockStats, …). */
type ViewData = Record<string, unknown>;

/**
 * The column to group weekday sections by: the first {@code Date}/
 * {@code LocalDateTime} column in the view meta (hidden or not), whatever its
 * alias. Falls back to {@code 'start'}. Without this the host would grope for a
 * fixed {@code 'start'} field and dump every row into "Ohne Datum" when a stored
 * view aliases its date column differently (e.g. {@code date}). Returns '' when
 * there is no date column (→ the view is not groupable, renders flat).
 */
export function pickDateAlias(columns: ViewColumn[]): string {
  return columns.find((c) => c.type === 'Date' || c.type === 'LocalDateTime')?.alias ?? '';
}

/**
 * Whether the active filter carries a SCOPE — at least one scoping chip. A
 * scope narrows the query to a selectable (resource / group) or a user; an
 * {@code event} chip is a navigation target, not a scope. No scope → the view
 * must not query (performance: an unscoped view is a full-window firehose).
 */
export function hasScope(chips: FilterEntry[]): boolean {
  return chips.some((c) => c.kind !== 'event');
}

/**
 * The generic view host (PRD 078 routing — {@code /app/views/:viewName}). One
 * component renders EVERY view: it looks up the view definition, resolves the
 * date window (runtime {@link ViewStateStore} window, else the view's
 * {@code inputs} defaults), executes the {@code @view} query, and renders
 * generically from {@code extensions.view}. Day-grouped views (no server
 * directive — PRD 074) get client-side {@link groupByWeekday} sectioning.
 *
 * The consumer path: {@code executeView(viewName)} runs the STORED query
 * (server-held) and the render-meta — columns, grouping, the variable signature —
 * arrives on {@code extensions.view}. There is no code-shipped fallback ViewMeta;
 * the server is the single source of truth for what a view looks like.
 */
@Component({
  selector: 'app-view-host',
  imports: [],
  template: `
    <section class="content">
      <h2 class="view-title">{{ meta()?.title ?? viewName() }}</h2>

      @if (noScope()) {
          <p class="empty">
            Wähle links eine Ressource, Gruppe oder Person als <strong>Scope</strong> (oder
            füge über die Suche einen Scope-Chip hinzu), um Termine zu laden.
          </p>
        } @else if (loading()) {
          <p class="meta">lädt…</p>
        } @else if (error()) {
          <p class="error">{{ error() }}</p>
        } @else {
          <p class="meta">{{ total() }} Termin(e){{ grouped() ? ' · ' + groups().length + ' Tag(e)' : '' }}</p>

          @for (g of groups(); track g.id) {
            <section class="day">
              @if (g.label) {
                <h3>{{ g.label }} <span class="cnt">({{ g.rows.length }})</span></h3>
              }
              <table class="grid">
                <thead>
                  <tr>
                    @for (col of visibleColumns(); track col.alias) {
                      <th>{{ col.header ?? col.alias }}</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (row of g.rows; track $index) {
                    <tr>
                      @for (col of visibleColumns(); track col.alias) {
                        <td>{{ cell(row, col) }}</td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </section>
          } @empty {
            <p class="empty">Keine Termine im Zeitraum.</p>
          }
        }
      </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .content {
        max-width: 1100px;
        margin: 1.25rem auto;
        padding: 0 1rem;
      }
      .view-title {
        font-size: 1.3rem;
        font-weight: 500;
        margin: 0 0 0.5rem;
      }
      .meta {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.85rem;
        margin: 0 0 0.75rem;
      }
      .day {
        margin-bottom: 1.25rem;
      }
      .day h3 {
        font-size: 1rem;
        font-weight: 600;
        margin: 0 0 0.4rem;
        color: var(--mat-sys-primary, #3f51b5);
      }
      .day .cnt {
        color: rgba(0, 0, 0, 0.45);
        font-weight: 400;
        font-size: 0.85rem;
      }
      table.grid {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.85rem;
      }
      .grid th,
      .grid td {
        text-align: left;
        padding: 0.35rem 0.6rem;
        border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      }
      .grid th {
        font-weight: 600;
        color: rgba(0, 0, 0, 0.6);
      }
      .empty {
        color: rgba(0, 0, 0, 0.5);
        font-style: italic;
      }
      .error {
        color: #c62828;
      }
    `,
  ],
})
export class ViewHostComponent {
  private readonly gql = inject(GraphqlService);
  private readonly viewState = inject(ViewStateStore);
  private readonly filter = inject(FilterStore);
  private readonly lastView = inject(LastViewStore);

  /** Bound from the route param {@code :viewName} (withComponentInputBinding). */
  readonly viewName = input.required<string>();

  readonly meta = signal<ViewMeta | null>(null);
  readonly rows = signal<Record<string, unknown>[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** True when no scope chip is set → the view shows a hint and fires NO query
   *  (performance: an unscoped view query is a full-window firehose). */
  readonly noScope = signal(false);

  /** Server render-info: the column to group by ({@code extensions.view.groupBy}),
   *  falling back to the first date column until the server emits groupBy. */
  readonly groupAlias = computed(
    () => this.meta()?.groupBy ?? pickDateAlias(this.meta()?.columns ?? []),
  );
  /** A view is groupable iff it has a group column. */
  readonly canGroup = computed(() => this.groupAlias() !== '');
  /** Group when in week mode AND the view actually has a group column. */
  readonly grouped = computed(() => this.viewState.renderMode() === 'week' && this.canGroup());

  readonly visibleColumns = computed<ViewColumn[]>(() =>
    (this.meta()?.columns ?? [])
      .filter((c) => !c.hidden)
      .sort((a, b) => (a.order ?? 999) - (b.order ?? 999)),
  );

  /** True for aggregation/pivot views (columns carry `kind`). Drives BOTH the
   *  row projection AND where the resource selection binds (allocatableFilter vs
   *  filter.allocatableIdsIn). A boolean computed → flips false→true once, no loop. */
  readonly aggregated = computed(() => isProjectedView(this.meta()?.columns ?? []));

  /** Stable key of the variable signature — flips once (null→signature) when the
   *  view's contract resolves, so the query effect re-runs WITHOUT looping on each
   *  response (a string computed memoizes by value). */
  private readonly bindingKey = computed(() => JSON.stringify(this.meta()?.variables ?? []));

  /** Rows projected into flat {alias: value} form. Flat views (no `kind`) pass
   *  through; aggregation/pivot views get projected via the column descriptors. */
  readonly displayRows = computed<Record<string, unknown>[]>(() => {
    const cols = this.meta()?.columns ?? [];
    const rows = this.rows();
    return this.aggregated() ? rows.map((r) => projectRow(r, cols)) : rows;
  });

  /** Day-sections (date group column → weekday headers), generic value sections
   *  (any other group column), or one flat group when ungrouped. */
  readonly groups = computed<Section[]>(() => {
    const rows = this.displayRows();
    if (!this.grouped()) return rows.length ? [{ id: '', label: '', rows }] : [];
    const meta = this.meta();
    const alias = this.groupAlias();

    // Server render-info: group by the marked column; format each header with the
    // opaque groupFormat token (e.g. "EE dd.MM" → "Mo 15.06").
    if (meta?.groupBy) {
      const fmt = meta.groupFormat;
      return groupByColumn(rows, alias).map((g) => ({
        id: g.key,
        label: fmt ? formatGroupLabel(g.key, fmt) : g.label,
        rows: g.rows,
      }));
    }

    // Fallback until the server emits groupBy: a date column → German weekday sections.
    const col = (meta?.columns ?? []).find((c) => c.alias === alias);
    if (col?.type === 'Date' || col?.type === 'LocalDateTime') {
      return groupByWeekday(rows, alias).map((g) => ({
        id: String(g.weekday),
        label: g.label,
        rows: g.rows,
      }));
    }
    return groupByColumn(rows, alias).map((g) => ({ id: g.key, label: g.label, rows: g.rows }));
  });

  /** Guards the once-per-view render-mode seed. */
  private lastSeededView: string | null = null;
  /** Monotonic request id — a slow (e.g. 500-row firehose) response from an OLDER
   *  query must not clobber a newer, filtered one. Stale responses are ignored. */
  private reqToken = 0;

  constructor() {
    // Persist the opened view so the default landing route restores it next time.
    effect(() => {
      const name = this.viewName();
      untracked(() => this.lastView.set(name));
    });

    // Re-query whenever the view, the window, OR the active filter (chips) change.
    // PERFORMANCE GATE: a view only queries when a SCOPE is set — at least one
    // scoping chip (resource / group / user; an `event` chip is a navigation
    // target, not a scope). With no scope we fire NO query (an unscoped view is a
    // full-window firehose) and show a hint instead.
    effect(() => {
      const viewName = this.viewName();
      const w = this.viewState.window();
      const chips = this.filter.entries();
      this.bindingKey(); // re-query when the variable signature resolves
      if (!hasScope(chips)) {
        this.clearForNoScope();
        return;
      }
      this.noScope.set(false);
      this.run(viewName, w, chips);
    });
  }

  /** No scope: drop any in-flight result, clear the table, show the hint. */
  private clearForNoScope(): void {
    this.reqToken++; // invalidate any in-flight response
    this.loading.set(false);
    this.error.set(null);
    this.rows.set([]);
    this.total.set(0);
    this.noScope.set(true);
  }

  private run(viewName: string, window: DateWindow | null, chips: FilterEntry[]): void {
    const token = ++this.reqToken;
    this.loading.set(true);
    this.error.set(null);
    // Type-driven binding: fill each declared variable BY TYPE (ReservationFilter ←
    // window+selection, AllocatableFilter ← selection). The server emits the
    // variable signature on extensions.view.variables; the FIRST query (before meta
    // lands) sends {} → the server merges its stored defaults, then bindingKey
    // re-queries with the resolved signature.
    // untracked: run() executes INSIDE the query effect — reading meta() tracked
    // here would make the effect re-fire on every response (meta.set) → infinite
    // loop. The one-time re-query when the signature lands is driven by bindingKey.
    const signature = untracked(() => this.meta()?.variables) ?? [];
    const resourceIds = chips.filter((c) => c.kind === 'resource').map((c) => c.id);
    // ReservationFilter.ownerEq is single — take the first user chip (the pinned
    // self is the common case; multi-user scope would need ownerIdsIn server-side).
    const ownerId = chips.find((c) => c.kind === 'user')?.id ?? null;
    const variables = buildVariablesByType(signature, { window, resourceIds, ownerId });
    this.gql.executeView<ViewData>(viewName, variables).subscribe({
      next: (res) => {
        if (token !== this.reqToken) return; // a newer query superseded this one
        if (res.errors?.length) {
          this.error.set(res.errors.map((e) => e.message).join('; '));
          this.loading.set(false);
          return;
        }
        // Root-agnostic: take whatever array `data` carries, not a fixed field name.
        const rows =
          (Object.values(res.data ?? {}).find(Array.isArray) as
            | Record<string, unknown>[]
            | undefined) ?? [];
        this.meta.set(res.extensions?.view ?? null);
        this.total.set(rows.length);
        this.rows.set(rows);
        this.loading.set(false);
        // Seed the render mode from the server render-info (groupable → week), once
        // per view — replaces the old hardcoded client registry hint.
        if (this.lastSeededView !== viewName) {
          this.lastSeededView = viewName;
          untracked(() => this.viewState.setRenderMode(this.canGroup() ? 'week' : 'table'));
        }
        // Seed the date-nav window from the server's input metadata, once.
        if (!window) {
          const seeded = resolveWindowFromInputs(res.extensions?.view?.inputs ?? []);
          if (seeded) untracked(() => this.viewState.setWindow(seeded));
        }
      },
      error: (err: { status?: number; error?: { message?: string } }) => {
        if (token !== this.reqToken) return; // superseded — ignore its failure too
        // 401 is owned by the auth refresh interceptor (PRD 072) — stay loading.
        if (err?.status === 401) return;
        this.error.set(err?.error?.message ?? `Request failed (HTTP ${err?.status ?? '?'})`);
        this.loading.set(false);
      },
    });
  }

  cell(row: Record<string, unknown>, col: ViewColumn): string {
    return renderCell(row, col);
  }
}
