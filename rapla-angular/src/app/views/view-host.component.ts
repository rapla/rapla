import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { GraphqlService, type ViewMeta, type ViewColumn } from '../graphql/graphql.service';
import { renderCell } from '../graphql/view-render';
import { groupByWeekday, groupByColumn } from '../graphql/weekday-grouping';
import { ViewStateStore, type DateWindow } from '../state/view-state-store';
import { FilterStore, type FilterEntry } from '../state/filter-store';
import { resolveWindowFromInputs } from './view-inputs';
import { filterToReservationFilter } from './filter-serializer';
import { formatGroupLabel } from './group-format';

/** A rendered section: rows sharing one group value (or one flat group when ungrouped). */
interface Section {
  id: string;
  label: string;
  rows: Record<string, unknown>[];
}

interface ViewData {
  appointmentBlocks: Record<string, unknown>[];
}

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
 * The generic view host (PRD 078 routing — {@code /app/views/:viewName}). One
 * component renders EVERY view: it looks up the view definition, resolves the
 * date window (runtime {@link ViewStateStore} window, else the view's
 * {@code inputs} defaults), executes the {@code @view} query, and renders
 * generically from {@code extensions.view}. Day-grouped views (no server
 * directive — PRD 074) get client-side {@link groupByWeekday} sectioning.
 *
 * BRIDGE: today it runs the registry's query text via {@code GraphqlService.query}
 * (authoring path). When server-side view persistence lands it swaps to
 * {@code executeView(viewName)} and reads {@code inputs} from {@code extensions.view}.
 */
@Component({
  selector: 'app-view-host',
  imports: [],
  template: `
    <section class="content">
      <h2 class="view-title">{{ meta()?.title ?? viewName() }}</h2>

      @if (loading()) {
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
                      <th>{{ col.header }}</th>
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

  /** Bound from the route param {@code :viewName} (withComponentInputBinding). */
  readonly viewName = input.required<string>();

  readonly meta = signal<ViewMeta | null>(null);
  readonly rows = signal<Record<string, unknown>[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

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

  /** Day-sections (date group column → weekday headers), generic value sections
   *  (any other group column), or one flat group when ungrouped. */
  readonly groups = computed<Section[]>(() => {
    const rows = this.rows();
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
    // Re-query whenever the view, the window, OR the active filter (chips) change.
    // First load: window is null → send no window, the server merges its default;
    // we then seed the date-nav window from the response inputs.
    effect(() => {
      const viewName = this.viewName();
      const w = this.viewState.window();
      const chips = this.filter.entries();
      this.run(viewName, w, chips);
    });
  }

  private run(viewName: string, window: DateWindow | null, chips: FilterEntry[]): void {
    const token = ++this.reqToken;
    this.loading.set(true);
    this.error.set(null);
    // With a window, send the full filter; without one (first load) send nothing
    // and let the server merge its defaults — chips re-apply on the seeded re-query.
    const variables = window ? { filter: filterToReservationFilter(chips, window) } : {};
    this.gql.executeView<ViewData>(viewName, variables).subscribe({
      next: (res) => {
        if (token !== this.reqToken) return; // a newer query superseded this one
        if (res.errors?.length) {
          this.error.set(res.errors.map((e) => e.message).join('; '));
          this.loading.set(false);
          return;
        }
        const rows = res.data?.appointmentBlocks ?? [];
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
