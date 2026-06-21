import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { GraphqlService, ViewMeta } from '../graphql/graphql.service';
import { renderCell, displayedColumns } from '../graphql/view-render';
import {
  APPOINTMENTS_QUERY,
  APPOINTMENTS_VIEW_FALLBACK,
  AppointmentsData,
  AppointmentBlockRow,
  defaultWindow,
} from './appointments-view';

/**
 * PRD 078 Phase 1 — the first GraphQL-driven SPA table view. Runs the
 * {@code appointments} query (recurrence blocks) and renders it generically
 * from {@link ViewMeta}: column order, headers, list-join and datetime format
 * all come from the render-meta, NOT from per-column template code. The meta is
 * taken from {@code extensions.view} when the server emits it, else the
 * code-shipped {@link APPOINTMENTS_VIEW_FALLBACK} — so this component does not
 * change when the {@code @view} directive lands.
 *
 * Selection for the first cut is the date window only (no search / resource
 * picker — deferred, see PRD 078).
 */
@Component({
  selector: 'app-appointments-view',
  imports: [MatTableModule, MatProgressSpinnerModule],
  template: `
    <section class="content">
      <h2 class="view-title">{{ meta()?.title ?? 'Termine' }}</h2>

      @if (loading()) {
        <div class="centered"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <p class="meta">{{ rows().length }} block(s)</p>

        <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
          @for (col of meta()?.columns ?? []; track col.alias) {
            @if (!col.hidden) {
              <ng-container [matColumnDef]="col.alias">
                <th mat-header-cell *matHeaderCellDef>{{ col.header }}</th>
                <td mat-cell *matCellDef="let row">{{ cell(row, col.alias) }}</td>
              </ng-container>
            }
          }

          <tr mat-header-row *matHeaderRowDef="displayed()"></tr>
          <tr mat-row *matRowDef="let row; columns: displayed()"></tr>

          <tr class="mat-row" *matNoDataRow>
            <td class="empty" [attr.colspan]="displayed().length || 1">
              No blocks in the queried window.
            </td>
          </tr>
        </table>
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
        margin: 1.5rem auto;
        padding: 0 1rem;
      }
      .view-title {
        font-size: 1.3rem;
        font-weight: 500;
        margin: 0 0 0.75rem;
      }
      .meta {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.85rem;
        margin: 0 0 0.5rem;
      }
      table {
        width: 100%;
      }
      .empty {
        padding: 1rem;
        color: rgba(0, 0, 0, 0.5);
        text-align: center;
        font-style: italic;
      }
      .centered {
        display: flex;
        justify-content: center;
        padding: 2rem;
      }
      .error {
        color: #c62828;
      }
    `,
  ],
})
export class AppointmentsViewComponent implements OnInit {
  private readonly gql = inject(GraphqlService);

  readonly meta = signal<ViewMeta | null>(null);
  readonly rows = signal<AppointmentBlockRow[]>([]);
  readonly displayed = computed(() => displayedColumns(this.meta()));

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const filter = { ...defaultWindow(), limit: 20 };
    this.gql.query<AppointmentsData>(APPOINTMENTS_QUERY, { filter }).subscribe({
      next: (res) => {
        if (res.errors?.length) {
          this.error.set(res.errors.map((e) => e.message).join('; '));
          this.loading.set(false);
          return;
        }
        // Prefer server render-meta; fall back to the code-shipped view until
        // the @view directive ships (PRD 074). Either way the renderer is generic.
        this.meta.set(res.extensions?.view ?? APPOINTMENTS_VIEW_FALLBACK);
        this.rows.set(res.data?.appointmentBlocks ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        // A 401 is owned by the auth layer, NOT this view: the refresh
        // interceptor (PRD 072) either replays transparently (success arrives
        // on `next`, not here) or redirects to /login. Surfacing it as
        // "Request failed (HTTP 401)" is wrong — stay in loading and let the
        // auth layer take over. Only real (non-auth) failures become view errors.
        if (err?.status === 401) return;
        this.error.set(err?.error?.message ?? `Request failed (HTTP ${err?.status ?? '?'})`);
        this.loading.set(false);
      },
    });
  }

  cell(row: AppointmentBlockRow, alias: string): string {
    const col = this.meta()?.columns.find((c) => c.alias === alias);
    return col ? renderCell(row as Record<string, unknown>, col) : '';
  }
}
