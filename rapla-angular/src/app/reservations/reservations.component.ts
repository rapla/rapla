import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  AfterViewInit,
  ViewChild,
} from '@angular/core';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { TableViewControllerService } from '../api/api/table-view-controller.service';
import { TablePage } from '../api/model/table-page';
import { TableRow } from '../api/model/table-row';
import { TableColumnDescriptor } from '../api/model/table-column-descriptor';
import { AuthService } from '../auth/auth.service';

/**
 * Reservation table — a thin viewer over the server-side table renderer
 * (GET /api/table/reservations, PRD 030). The server projects the columns and
 * the scalar row cells; this component only paints them. It holds no
 * reservation graph and runs no row projection of its own.
 */
@Component({
  selector: 'app-reservations',
  imports: [
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    <mat-toolbar color="primary">
      <span>Reservations</span>
      <span class="spacer"></span>
      @if (auth.isImpersonating()) {
        <span class="impersonation-badge" title="Admin {{ adminUsername() }} is acting as {{ impersonatedUsername() }}">
          <mat-icon>person_search</mat-icon>
          Impersonating {{ impersonatedUsername() }}
        </span>
      } @else if (username()) {
        <span class="username">{{ username() }}</span>
      }
      <button matButton (click)="auth.signOut()">
        <mat-icon>logout</mat-icon>
        Sign out
      </button>
    </mat-toolbar>

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

        <mat-paginator
          [pageSizeOptions]="[10, 25, 50, 100]"
          [pageSize]="25"
          showFirstLastButtons
        ></mat-paginator>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .spacer {
        flex: 1 1 auto;
      }
      .username {
        margin-right: 1rem;
        font-size: 0.95rem;
      }
      .impersonation-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        margin-right: 1rem;
        padding: 0.2rem 0.6rem;
        background: rgba(255, 193, 7, 0.85);
        color: rgba(0, 0, 0, 0.87);
        border-radius: 4px;
        font-size: 0.9rem;
        font-weight: 500;
      }
      .impersonation-badge mat-icon {
        font-size: 1.1rem;
        height: 1.1rem;
        width: 1.1rem;
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
export class ReservationsComponent implements OnInit, AfterViewInit {
  private readonly table = inject(TableViewControllerService);
  protected readonly auth = inject(AuthService);

  readonly dataSource = new MatTableDataSource<TableRow>([]);
  readonly columns = signal<TableColumnDescriptor[]>([]);
  readonly displayedColumns = computed(() => this.columns().map((c) => c.id ?? ''));

  loading = signal(true);
  error = signal<string | null>(null);
  totalCount = signal(0);
  incomplete = signal(false);
  username = signal('');

  /**
   * PRD 051 — when an impersonation is active, the toolbar shows
   * "Impersonating <target>" instead of the admin's normal username
   * badge. The target name comes straight from the override (which
   * the admin set when they clicked "Switch to user"); the
   * {@link adminUsername} resolves from the JWT `act.username` claim
   * for the tooltip, so an admin always knows whose authority is
   * being used.
   */
  readonly impersonatedUsername = computed(
    () => this.auth.impersonationOverride()?.targetUsername ?? '',
  );

  /** The actor's username, decoded from the impersonation token's
   *  `act.username` claim. Used in the tooltip on the badge. Empty
   *  when no impersonation is active. */
  readonly adminUsername = computed(() => {
    const override = this.auth.impersonationOverride();
    if (!override) return '';
    const claim = decodeActUsername(override.accessToken);
    return claim ?? '';
  });

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnInit() {
    const claims = this.auth.identityClaims() ?? {};
    this.username.set(String(claims['preferred_username'] ?? claims['name'] ?? ''));

    const year = new Date().getFullYear();
    const from = `${year - 1}-01-01`;
    const to = `${year + 2}-12-31`;

    this.table.reservations(from, to).subscribe({
      next: (page: TablePage) => {
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
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
    this.dataSource.sortingDataAccessor = (row, columnId) => {
      const v: unknown = row.cells?.[columnId];
      return typeof v === 'number' ? v : String(v ?? '');
    };
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

/**
 * Decode the {@code act.username} claim from a rapla-SAS-minted
 * impersonation token. Used for the tooltip on the
 * "Impersonating X" badge so admins can see whose authority is being
 * used. Signature isn't checked here — that's the resource server's
 * job; we just want to render the claim. Returns null on any parse
 * failure (don't fall back to noisy errors in the toolbar).
 */
function decodeActUsername(jwt: string): string | null {
  try {
    const parts = jwt.split('.');
    if (parts.length < 2) return null;
    const padded = parts[1] + '='.repeat((4 - (parts[1].length % 4)) % 4);
    const json = JSON.parse(atob(padded.replace(/-/g, '+').replace(/_/g, '/')));
    const act = json.act;
    if (act && typeof act === 'object' && typeof act.username === 'string') {
      return act.username;
    }
    return null;
  } catch {
    return null;
  }
}
