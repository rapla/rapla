import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';

import { AuthService } from '../auth/auth.service';
import { UsersService } from '../auth/users.service';
import { ProfileService } from '../account/profile.service';
import { SwitchToUserDialogComponent } from '../auth/switch-to-user-dialog.component';
import { ApiKeysDialogComponent } from '../account/api-keys-dialog.component';
import { EditAccountDialogComponent } from '../account/edit-account-dialog.component';
import { OmniboxComponent } from './omnibox.component';

/**
 * PRD 078 — the global app toolbar (account chrome). The right-hand side is now
 * a single central user menu (avatar + username trigger) gathering every
 * account action that used to be loose toolbar buttons:
 *
 * <ul>
 *   <li><b>Account settings ▸</b> — Manage API keys (PRD 043/076) and, for
 *       LOCAL users only, Edit account (PRD 050). The Edit-account entry is
 *       hidden when {@code ProfileEditCapabilities.externalIdpLabel} is non-null
 *       (the user's profile is owned by an external IdP).</li>
 *   <li><b>Switch to user… / Switch back</b> — impersonation (PRD 051), shown
 *       only when the caller can {@code canAdminUser} ≥1 user, or is currently
 *       impersonating.</li>
 *   <li><b>Sign out</b>.</li>
 * </ul>
 *
 * Impersonation is driven through {@link AuthService} state; pages react via an
 * effect on {@code auth.identity()} — no dialog callback wiring here.
 */
@Component({
  selector: 'app-toolbar',
  imports: [MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule, OmniboxComponent],
  template: `
    <mat-toolbar color="primary" class="appbar">
      <span class="app-title">Rapla</span>
      <app-omnibox class="toolbar-search" />
      <span class="spacer"></span>

      @if (effectiveUsername()) {
        <button
          matButton
          class="user-trigger"
          [class.impersonating]="auth.isImpersonating()"
          [matMenuTriggerFor]="userMenu"
          [title]="
            auth.isImpersonating()
              ? 'Acting as ' + effectiveUsername() + ' via admin ' + adminUsername()
              : 'Account menu'
          "
        >
          <span class="avatar">{{ initials() }}</span>
          <span class="username">{{ effectiveUsername() }}</span>
          @if (auth.isImpersonating()) {
            <mat-icon class="impersonation-marker">person_search</mat-icon>
          }
          <mat-icon class="caret">arrow_drop_down</mat-icon>
        </button>

        <mat-menu #userMenu="matMenu">
          <button mat-menu-item [matMenuTriggerFor]="accountMenu">
            <mat-icon>manage_accounts</mat-icon>
            <span>Account settings</span>
          </button>

          @if (canImpersonate() && !auth.isImpersonating()) {
            <button mat-menu-item (click)="openSwitchToUser()">
              <mat-icon>swap_horiz</mat-icon>
              <span>Switch to user…</span>
            </button>
          }
          @if (auth.isImpersonating()) {
            <button mat-menu-item class="switch-back" (click)="switchBack()">
              <mat-icon>undo</mat-icon>
              <span>Switch back to admin</span>
            </button>
          }

          <button mat-menu-item (click)="signOut()">
            <mat-icon>logout</mat-icon>
            <span>Sign out</span>
          </button>
        </mat-menu>

        <mat-menu #accountMenu="matMenu">
          <button mat-menu-item (click)="openApiKeys()">
            <mat-icon>key</mat-icon>
            <span>Manage API keys</span>
          </button>
          @if (showEditAccount()) {
            <button mat-menu-item (click)="openEditAccount()">
              <mat-icon>badge</mat-icon>
              <span>Edit account</span>
            </button>
          }
        </mat-menu>
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
      .toolbar-search {
        flex: 0 1 640px;
        margin: 0;
      }
      .spacer {
        flex: 1 1 auto;
      }
      .user-trigger {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        height: 44px;
        border-radius: 22px;
        padding: 0 0.5rem 0 0.35rem;
      }
      .avatar {
        width: 30px;
        height: 30px;
        border-radius: 50%;
        background: #fff;
        color: var(--mat-sys-primary, #1565c0);
        font-size: 0.8rem;
        font-weight: 500;
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }
      .username {
        font-size: 0.95rem;
      }
      .caret {
        font-size: 1.25rem;
        height: 1.25rem;
        width: 1.25rem;
        opacity: 0.85;
      }
      .impersonation-marker {
        font-size: 1.05rem;
        height: 1.05rem;
        width: 1.05rem;
      }
      .user-trigger.impersonating {
        background: rgba(255, 193, 7, 0.9);
        color: rgba(0, 0, 0, 0.87);
      }
      .user-trigger.impersonating .avatar {
        background: var(--mat-sys-primary, #0d47a1);
        color: #fff;
      }
      .switch-back {
        color: #0d47a1;
      }
    `,
  ],
})
export class AppToolbarComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly usersService = inject(UsersService);
  private readonly profile = inject(ProfileService);
  private readonly dialog = inject(MatDialog);

  /** Effective user shown in the chip (impersonation target when active, else self). */
  readonly effectiveUsername = computed(() => this.auth.identity()?.username ?? '');

  /** Two-letter avatar initials from the display name (fallback: username). */
  readonly initials = computed(() => {
    const id = this.auth.identity();
    const source = (id?.name?.trim() || id?.username || '').trim();
    if (!source) return '';
    const parts = source.split(/\s+/);
    const letters = parts.length >= 2 ? parts[0][0] + parts[parts.length - 1][0] : source.slice(0, 2);
    return letters.toUpperCase();
  });

  /** True if the caller can {@code canAdminUser} over ≥1 other user → enable switch-to-user. */
  readonly canImpersonate = signal(false);

  /** False while provisioned (external IdP owns the profile) → hide "Edit account". */
  readonly showEditAccount = signal(false);

  /** The admin actor's username while impersonating; '' otherwise. */
  readonly adminUsername = computed(() => this.auth.actorUsername());

  ngOnInit(): void {
    // PRD 051 — non-empty /api/users (server-filtered by canAdminUser) ⇒ enable switch.
    this.usersService.list().subscribe((list) => this.canImpersonate.set(list.length > 0));
    // PRD 050 — local users (no external IdP) can edit their account.
    this.profile.capabilities().subscribe({
      next: (caps) => this.showEditAccount.set(caps.externalIdpLabel == null),
      error: () => this.showEditAccount.set(false),
    });
  }

  switchBack(): void {
    void this.auth.endImpersonation();
  }

  signOut(): void {
    void this.auth.signOut();
  }

  openSwitchToUser(): void {
    this.dialog.open(SwitchToUserDialogComponent, { width: '420px', autoFocus: true });
  }

  openApiKeys(): void {
    this.dialog.open(ApiKeysDialogComponent, { width: '680px', maxWidth: '92vw', autoFocus: false });
  }

  openEditAccount(): void {
    this.dialog.open(EditAccountDialogComponent, { width: '520px', autoFocus: false });
  }
}
