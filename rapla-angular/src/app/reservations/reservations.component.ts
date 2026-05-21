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

import { MatDialog } from '@angular/material/dialog';

import { TableViewControllerService } from '../api/api/table-view-controller.service';
import { TablePage } from '../api/model/table-page';
import { TableRow } from '../api/model/table-row';
import { TableColumnDescriptor } from '../api/model/table-column-descriptor';
import { AuthService } from '../auth/auth.service';
import { UsersService } from '../auth/users.service';
import { SwitchToUserDialogComponent } from '../auth/switch-to-user-dialog.component';

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
      @if (effectiveUsername()) {
        @if (canImpersonate() || auth.isImpersonating()) {
          <button
            matButton
            class="username username-clickable"
            [class.impersonating]="auth.isImpersonating()"
            [title]="
              auth.isImpersonating()
                ? 'Acting as ' + effectiveUsername() + ' via admin ' + adminUsername() + ' — click to switch to another user'
                : 'Click to switch to another user'
            "
            (click)="openSwitchToUser()"
          >
            @if (auth.isImpersonating()) {
              <mat-icon class="impersonation-marker">person_search</mat-icon>
            } @else {
              <mat-icon class="switch-marker">swap_horiz</mat-icon>
            }
            {{ effectiveUsername() }}
          </button>
        } @else {
          <span class="username">{{ effectiveUsername() }}</span>
        }
      }
      @if (auth.isImpersonating()) {
        <button matButton (click)="switchBack()" title="Return to admin identity">
          <mat-icon>undo</mat-icon>
          Switch back
        </button>
      } @else {
        <button matButton (click)="auth.signOut()">
          <mat-icon>logout</mat-icon>
          Sign out
        </button>
      }
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
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
      }
      .username-clickable {
        cursor: pointer;
      }
      .username.impersonating {
        background: rgba(255, 193, 7, 0.85);
        color: rgba(0, 0, 0, 0.87);
        padding: 0.15rem 0.6rem;
        border-radius: 4px;
        font-weight: 500;
      }
      .username.impersonating:hover {
        background: rgba(255, 193, 7, 1);
      }
      .impersonation-marker {
        font-size: 1.05rem;
        height: 1.05rem;
        width: 1.05rem;
      }
      .switch-marker {
        font-size: 1.05rem;
        height: 1.05rem;
        width: 1.05rem;
        opacity: 0.7;
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
  private readonly usersService = inject(UsersService);
  private readonly dialog = inject(MatDialog);

  readonly dataSource = new MatTableDataSource<TableRow>([]);
  readonly columns = signal<TableColumnDescriptor[]>([]);
  readonly displayedColumns = computed(() => this.columns().map((c) => c.id ?? ''));

  loading = signal(true);
  error = signal<string | null>(null);
  totalCount = signal(0);
  incomplete = signal(false);
  username = signal('');

  /**
   * PRD 051 — effective user shown in the toolbar chip. While
   * impersonating, this is the target's username (from the
   * impersonation override). Otherwise the admin's own preferred
   * username from JWT identity claims (the {@code username()} signal
   * set in {@link ngOnInit}). One source of truth so the badge,
   * marker, and tooltip all agree.
   */
  readonly effectiveUsername = computed(
    () => this.auth.impersonationOverride()?.targetUsername ?? this.username(),
  );

  /**
   * PRD 051 — `true` if the caller can {@code canAdminUser} over ≥1
   * other user (i.e. is a global admin or a group admin). Derived
   * from {@code GET /api/users} returning a non-empty list — that
   * endpoint is server-side filtered by the same {@code canAdminUser}
   * rule. Refreshed on init; static for the session (group-admin
   * status doesn't change mid-session — if it does, the next
   * impersonate call's 403 surfaces it).
   */
  readonly canImpersonate = signal(false);

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
    // PRD 051 — probe /api/users to decide whether the username chip
    // is clickable. Empty list = caller has no admin authority;
    // non-empty = chip becomes a "Switch to user" trigger.
    this.usersService.list().subscribe((list) => {
      this.canImpersonate.set(list.length > 0);
    });
    this.fetchReservations();
  }

  private fetchReservations(): void {
    this.loading.set(true);
    this.error.set(null);
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

  /**
   * PRD 051 — click handler on the "Impersonating X" badge. Clears
   * the override in {@link AuthService}; next outbound request uses
   * the admin's own Bearer. Reloads the table so the user sees their
   * own (admin's) data again.
   */
  switchBack(): void {
    this.auth.endImpersonation();
    this.fetchReservations();
  }

  /**
   * PRD 051 — opens the "Switch to user" dialog. On successful
   * impersonation (dialog closes with a {target} result) the toolbar
   * re-renders and the table reloads for the new identity.
   */
  openSwitchToUser(): void {
    const ref = this.dialog.open(SwitchToUserDialogComponent, {
      width: '420px',
      autoFocus: true,
    });
    ref.afterClosed().subscribe((result: { target: string } | undefined) => {
      if (result?.target) {
        this.fetchReservations();
      }
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
