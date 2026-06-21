import { Component, computed, effect, inject, signal } from '@angular/core';

import { GraphqlService, type ViewMeta, type ViewColumn } from '../graphql/graphql.service';
import { renderCell } from '../graphql/view-render';
import { groupByWeekday, type WeekdayGroup } from '../graphql/weekday-grouping';
import { ViewStateStore } from '../state/view-state-store';
import { WEEK_VIEW_QUERY, WEEK_VIEW_FALLBACK, weekWindow } from './week-view';

interface WeekViewData {
  appointmentBlocks: Record<string, unknown>[];
}

/**
 * The "Wochenansicht" rendering view (prototype view #2): runs
 * {@link WEEK_VIEW_QUERY} for the current {@link ViewStateStore} window and
 * renders the flat blocks grouped by weekday ({@link groupByWeekday}) — the one
 * piece the server can't do (no display-grouping directive; PRD 074). Columns
 * come from {@code extensions.view} (else {@link WEEK_VIEW_FALLBACK}); the
 * hidden reservation/duration fields never become headers.
 */
@Component({
  selector: 'app-week-view',
  imports: [],
  template: `
    <section class="content">
      <h2 class="view-title">{{ meta()?.title ?? 'Wochenansicht' }}</h2>

      @if (loading()) {
        <p class="meta">lädt…</p>
      } @else if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <p class="meta">{{ total() }} Termin(e) · {{ groups().length }} Tag(e)</p>

        @for (g of groups(); track g.weekday) {
          <section class="day">
            <h3>{{ g.label }} <span class="cnt">({{ g.rows.length }})</span></h3>
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
          <p class="empty">Keine Termine in dieser Woche.</p>
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
export class WeekViewComponent {
  private readonly gql = inject(GraphqlService);
  private readonly viewState = inject(ViewStateStore);

  readonly meta = signal<ViewMeta | null>(null);
  readonly groups = signal<WeekdayGroup[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly visibleColumns = computed<ViewColumn[]>(() =>
    (this.meta()?.columns ?? [])
      .filter((c) => !c.hidden)
      .sort((a, b) => (a.order ?? 999) - (b.order ?? 999)),
  );

  constructor() {
    // Seed a default week window if nothing set the runtime state yet, then
    // re-query whenever the window changes (date-nav / render-mode switch).
    if (!this.viewState.window()) this.viewState.setWindow(weekWindow());
    effect(() => {
      const w = this.viewState.window();
      if (w) this.run(w.from, w.to);
    });
  }

  private run(from: string, to: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.gql.query<WeekViewData>(WEEK_VIEW_QUERY, { filter: { from, to } }).subscribe({
      next: (res) => {
        if (res.errors?.length) {
          this.error.set(res.errors.map((e) => e.message).join('; '));
          this.loading.set(false);
          return;
        }
        const rows = res.data?.appointmentBlocks ?? [];
        this.meta.set(res.extensions?.view ?? WEEK_VIEW_FALLBACK);
        this.total.set(rows.length);
        this.groups.set(groupByWeekday(rows, 'start'));
        this.loading.set(false);
      },
      error: (err: { status?: number; error?: { message?: string } }) => {
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
