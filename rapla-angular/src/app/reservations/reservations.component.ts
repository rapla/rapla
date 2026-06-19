import { Component, inject, signal, computed, effect, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSortModule } from '@angular/material/sort';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { MatDialog } from '@angular/material/dialog';

import { TablePage, TableRow, TableColumnDescriptor } from './table.types';
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
                ? 'Acting as ' +
                  effectiveUsername() +
                  ' via admin ' +
                  adminUsername() +
                  ' — click to switch to another user'
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
        <button matButton (click)="signOut()">
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
export class ReservationsComponent implements OnInit {
  private readonly http = inject(HttpClient);
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

  /**
   * PRD 072 — effective user shown in the toolbar chip, from the identity
   * loaded via {@code GET /api/auth/me}. While impersonating, the server
   * reports the TARGET as {@code username}; otherwise it's the caller's own.
   * One source of truth so the badge, marker, and tooltip all agree.
   */
  readonly effectiveUsername = computed(() => this.auth.identity()?.username ?? '');

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

  /** The admin actor's username while impersonating, from {@code /api/auth/me}'s
   *  {@code actor} field. Empty when no impersonation is active. */
  readonly adminUsername = computed(() => this.auth.actorUsername());

  constructor() {
    // Re-fetch whenever the impersonation state flips — covers
    // openSwitchToUser → confirm → impersonate, switchBack, and any other
    // path that reloads the identity. Tracks the effective username (or
    // null when no identity); only fires when that changes. ngOnInit's
    // initial fetch isn't enough because the dialog-close subscription
    // path was unreliable across the angular-zone / microtask boundary.
    let lastTarget: string | null | undefined = undefined;
    effect(() => {
      const target = this.auth.identity()?.username ?? null;
      if (lastTarget === undefined) {
        lastTarget = target;
        return; // skip the initial run — ngOnInit fires the first fetch
      }
      if (target !== lastTarget) {
        lastTarget = target;
        this.fetchReservations();
      }
    });
  }

  ngOnInit() {
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

  /**
   * PRD 072 — click handler on the "Impersonating X" badge. Calls the
   * server {@code POST /api/auth/impersonate/end} (restores the admin
   * {@code access_token} cookie) and reloads the identity. The impersonation
   * effect then refires the table fetch; the user sees their own (admin's)
   * data again.
   */
  switchBack(): void {
    void this.auth.endImpersonation();
  }

  /** Explicit user-driven sign-out (server logout + cookie clear). */
  signOut(): void {
    this.auth.signOut();
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
