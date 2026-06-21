import { Component, inject, signal, computed, effect, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSortModule } from '@angular/material/sort';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { TablePage, TableRow, TableColumnDescriptor } from './table.types';
import { AuthService } from '../auth/auth.service';

/**
 * Reservation table — a thin viewer over the server-side table renderer
 * (GET /api/table/reservations, PRD 030). The server projects the columns and
 * the scalar row cells; this component only paints them. It holds no
 * reservation graph and runs no row projection of its own.
 *
 * PRD 078 — the account chrome (user chip / switch-user / sign-out) was lifted
 * to the global {@code AppToolbarComponent} in the shell; this page now renders
 * only its table. It still refetches when the impersonation identity flips
 * (the effect below), driven by {@link AuthService} state.
 */
@Component({
  selector: 'app-reservations',
  imports: [MatTableModule, MatSortModule, MatProgressSpinnerModule],
  template: `
    <section class="content">
      @if (loading()) {
        <div class="centered"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <p class="meta">
          {{ totalCount() }} reservation row(s)
          @if (incomplete()) {
            — result truncated, narrow the date range
          }
        </p>

        <table mat-table [dataSource]="dataSource" matSort class="mat-elevation-z1">
          @for (col of columns(); track col.id) {
            <ng-container [matColumnDef]="col.id ?? ''">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ col.label }}</th>
              <td mat-cell *matCellDef="let row">{{ formatCell(row, col) }}</td>
            </ng-container>
          }

          <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns()"></tr>

          <tr class="mat-row" *matNoDataRow>
            <td class="empty" [attr.colspan]="displayedColumns().length || 1">
              No reservations in the queried window.
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
export class ReservationsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly dataSource = new MatTableDataSource<TableRow>([]);
  readonly columns = signal<TableColumnDescriptor[]>([]);
  readonly displayedColumns = computed(() => this.columns().map((c) => c.id ?? ''));

  loading = signal(true);
  error = signal<string | null>(null);
  totalCount = signal(0);
  incomplete = signal(false);

  constructor() {
    // Refetch whenever the impersonation identity flips (switch-user / switch-back
    // in the global toolbar). Tracks the effective username; only fires on change.
    // The global toolbar triggers impersonation via AuthService; this page reacts
    // to the resulting identity change. The initial run is skipped — ngOnInit
    // fires the first fetch.
    let lastTarget: string | null | undefined = undefined;
    effect(() => {
      const target = this.auth.identity()?.username ?? null;
      if (lastTarget === undefined) {
        lastTarget = target;
        return;
      }
      if (target !== lastTarget) {
        lastTarget = target;
        this.fetchReservations();
      }
    });
  }

  ngOnInit() {
    this.fetchReservations();
  }

  private fetchReservations(): void {
    this.loading.set(true);
    this.error.set(null);

    // Window: today ±1 year. Until the SPA grows a date-picker UI the table
    // shows two years centred on now — wide enough to cover the typical
    // teacher's semester ±, narrow enough to keep the response small.
    const now = new Date();
    const fromDate = new Date(now);
    fromDate.setFullYear(now.getFullYear() - 1);
    const toDate = new Date(now);
    toDate.setFullYear(now.getFullYear() + 1);
    const from = fromDate.toISOString().slice(0, 10);
    const to = toDate.toISOString().slice(0, 10);

    // Scope to "reservations I made": fetch the rapla User id from
    // /api/users/me (the JWT alone isn't enough — when an external IdP
    // fronts rapla, `sub` is the IdP's id, not rapla's), then anchor the
    // query on that id via `owners`.
    this.http.get<{ id: string }>('/api/users/me').subscribe({
      next: (me) => {
        const body = { from, to, owners: [me.id] };
        this.http.post<TablePage>('/api/table/reservations', body).subscribe({
          next: (page) => {
            this.columns.set(page.columns ?? []);
            this.dataSource.data = page.rows ?? [];
            this.totalCount.set(page.totalCount ?? this.dataSource.data.length);
            this.incomplete.set(page.incomplete ?? false);
            this.loading.set(false);
          },
          error: (err) => {
            this.error.set(err?.error?.message ?? `Request failed (HTTP ${err?.status ?? '?'})`);
            this.loading.set(false);
          },
        });
      },
      error: (err) => {
        this.error.set(
          err?.error?.message ?? `Failed to resolve current user (HTTP ${err?.status ?? '?'})`,
        );
        this.loading.set(false);
      },
    });
  }

  formatCell(row: TableRow, col: TableColumnDescriptor): string {
    const v: unknown = row.cells?.[col.id ?? ''];
    if (v === null || v === undefined) return '';
    if (col.type === 'DATE') {
      const d = new Date(v as string | number);
      if (!isNaN(d.getTime())) return d.toLocaleString();
    }
    return String(v);
  }
}
