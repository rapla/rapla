import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { AuthService } from '../auth/auth.service';
import { UsersService } from '../auth/users.service';
import { SwitchToUserDialogComponent } from '../auth/switch-to-user-dialog.component';
import { OmniboxComponent } from './omnibox.component';

/**
 * PRD 078 — the global app toolbar (account chrome). Lifted out of
 * {@code ReservationsComponent} so identity / switch-user / sign-out live in
 * the app shell and appear on EVERY page, not just one. Pages render only
 * their content inside the {@code <router-outlet>}; this is the persistent
 * frame around them.
 *
 * Impersonation is driven entirely through {@link AuthService} state: the
 * dialog calls {@code auth.impersonate(...)} which reloads {@code identity},
 * and any page that needs to react (e.g. refetch its table) does so via an
 * effect on {@code auth.identity()} — no dialog callback wiring needed here.
 */
@Component({
  selector: 'app-toolbar',
  imports: [MatToolbarModule, MatButtonModule, MatIconModule, OmniboxComponent],
  template: `
    <mat-toolbar color="primary" class="appbar">
      <span class="app-title">Rapla</span>
      <app-omnibox class="toolbar-search" />
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
  `,
  styles: [
    `
      .appbar {
        /* let the omnibox dropdown escape the toolbar */
        overflow: visible;
        gap: 1.5rem;
      }
      .app-title {
        font-weight: 500;
        flex: 0 0 auto;
      }
      /* The big search sits left-flush after the brand (tool look), capped width. */
      .toolbar-search {
        flex: 0 1 640px;
        margin: 0;
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
      .impersonation-marker,
      .switch-marker {
        font-size: 1.05rem;
        height: 1.05rem;
        width: 1.05rem;
      }
      .switch-marker {
        opacity: 0.7;
      }
    `,
  ],
})
export class AppToolbarComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly usersService = inject(UsersService);
  private readonly dialog = inject(MatDialog);

  /** Effective user shown in the chip (impersonation target when active, else self). */
  readonly effectiveUsername = computed(() => this.auth.identity()?.username ?? '');

  /** True if the caller can {@code canAdminUser} over ≥1 other user → chip is a switch trigger. */
  readonly canImpersonate = signal(false);

  /** The admin actor's username while impersonating; '' otherwise. */
  readonly adminUsername = computed(() => this.auth.actorUsername());

  ngOnInit(): void {
    // PRD 051 — non-empty /api/users (server-filtered by canAdminUser) ⇒ the
    // chip becomes a "Switch to user" trigger.
    this.usersService.list().subscribe((list) => this.canImpersonate.set(list.length > 0));
  }

  switchBack(): void {
    void this.auth.endImpersonation();
  }

  signOut(): void {
    this.auth.signOut();
  }

  openSwitchToUser(): void {
    this.dialog.open(SwitchToUserDialogComponent, { width: '420px', autoFocus: true });
  }
}
