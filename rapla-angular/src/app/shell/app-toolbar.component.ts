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
import { PermissionMigrationDialogComponent } from '../account/permission-migration-dialog.component';
import { PermissionMigrationService } from '../account/permission-migration.service';
import { OmniboxComponent } from './omnibox.component';
import { EventSheetComponent, type EventSheetDialogData } from '../event/event-sheet.component';
import { newScopedDraft } from '../event/event-draft';
import { GraphqlService } from '../graphql/graphql.service';
import { UndoToastService } from '../actions/undo-toast.service';
import { FilterStore } from '../state/filter-store';

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
      @if (eventTypes().length > 1) {
        <button
          matButton
          class="new-event"
          [matMenuTriggerFor]="newMenu"
          title="Neue Veranstaltung anlegen"
        >
          <mat-icon>add</mat-icon>
          <span class="new-event-label">Neu</span>
          <mat-icon class="caret">arrow_drop_down</mat-icon>
        </button>
        <mat-menu #newMenu="matMenu">
          @for (t of eventTypes(); track t.key) {
            <button mat-menu-item (click)="newEvent(t.key)">
              <span>{{ t.name }}</span>
            </button>
          }
        </mat-menu>
      } @else {
        <button matButton class="new-event" (click)="newEvent()" title="Neue Veranstaltung anlegen">
          <mat-icon>add</mat-icon>
          <span class="new-event-label">Neu</span>
        </button>
      }
      <button
        matIconButton
        class="undo-btn"
        [disabled]="!undo.canUndo()"
        [title]="undo.canUndo() ? 'Rückgängig: ' + undo.undoLabel() : 'Rückgängig'"
        (click)="undo.undo()"
      >
        <mat-icon>undo</mat-icon>
      </button>
      <button
        matIconButton
        class="redo-btn"
        [disabled]="!undo.canRedo()"
        [title]="undo.canRedo() ? 'Wiederholen: ' + undo.redoLabel() : 'Wiederholen'"
        (click)="undo.redo()"
      >
        <mat-icon>redo</mat-icon>
      </button>
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
          @if (showPermissionMigration()) {
            <button mat-menu-item (click)="openPermissionMigration()">
              <mat-icon>rule</mat-icon>
              <span>Permission migration</span>
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
  private readonly permissionMigration = inject(PermissionMigrationService);
  private readonly dialog = inject(MatDialog);
  private readonly gql = inject(GraphqlService);
  /** PRD 094 D2 — main-view command history behind the header ↶/↷ buttons. */
  protected readonly undo = inject(UndoToastService);
  private readonly filter = inject(FilterStore);

  /** PRD 094 Phase 2 — creatable event types; >1 turns "Neu" into a type menu
   *  (the Swing wizard-submenu analog), exactly 1 keeps the plain button. */
  readonly eventTypes = signal<{ key: string; name: string }[]>([]);

  /** Effective user shown in the chip (impersonation target when active, else self). */
  readonly effectiveUsername = computed(() => this.auth.identity()?.username ?? '');

  /** Two-letter avatar initials from the display name (fallback: username). */
  readonly initials = computed(() => {
    const id = this.auth.identity();
    const source = (id?.name?.trim() || id?.username || '').trim();
    if (!source) return '';
    const parts = source.split(/\s+/);
    const letters =
      parts.length >= 2 ? parts[0][0] + parts[parts.length - 1][0] : source.slice(0, 2);
    return letters.toUpperCase();
  });

  /** True if the caller can {@code canAdminUser} over ≥1 other user → enable switch-to-user. */
  readonly canImpersonate = signal(false);

  /** False while provisioned (external IdP owns the profile) → hide "Edit account". */
  readonly showEditAccount = signal(false);

  /** The admin actor's username while impersonating; '' otherwise. */
  readonly adminUsername = computed(() => this.auth.actorUsername());

  /** PRD 090 — show the "Permission migration" entry only to a global admin AND only
   * when the worklist is non-empty (nothing to migrate ⇒ no menu item). */
  readonly hasMigrationItems = signal(false);
  private readonly isAdmin = computed(() => this.auth.identity()?.admin ?? false);
  readonly showPermissionMigration = computed(() => this.isAdmin() && this.hasMigrationItems());

  ngOnInit(): void {
    // PRD 051 — non-empty /api/users (server-filtered by canAdminUser) ⇒ enable switch.
    this.usersService.list().subscribe((list) => this.canImpersonate.set(list.length > 0));
    // PRD 050 — local users (no external IdP) can edit their account.
    this.profile.capabilities().subscribe({
      next: (caps) => this.showEditAccount.set(caps.externalIdpLabel == null),
      error: () => this.showEditAccount.set(false),
    });
    // PRD 094 — creatable event types for the type-aware "Neu" (wizard analog).
    this.gql
      .query<{
        types: { key: string; name: string; classificationType: string }[];
      }>(`query { types { key name classificationType } }`)
      .subscribe((resp) => {
        const all = resp.data?.types ?? [];
        this.eventTypes.set(
          all
            .filter((t) => t.classificationType === 'RESERVATION')
            .map((t) => ({ key: t.key, name: t.name })),
        );
      });
    // PRD 090 — only surface the migration entry when there is at least one open item.
    if (this.isAdmin()) {
      this.permissionMigration.findings().subscribe({
        next: (list) => this.hasMigrationItems.set(list.length > 0),
        error: () => this.hasMigrationItems.set(false),
      });
    }
  }

  /**
   * PRD 091 — "Neu" opens the FULL event editor as a dialog over the current
   * view (2026-07-07 direction: the editor is not its own page; the
   * /app/event/:id route stays as the deep link only). Id minted client-side
   * per D3. The quick-create window (QuickEventDialogComponent) is reserved
   * for the future calendar-click entry point.
   */
  newEvent(typeKey?: string): void {
    const key = typeKey ?? this.eventTypes()[0]?.key ?? 'event';
    // Swing parity: the view's selected resources become allocations of the new
    // event. The mapping lives in newScopedDraft so every "new from a scoped
    // view" entry point (quick-create later) shares one implementation.
    const draft = newScopedDraft(key, new Date(), this.filter.entries());
    this.dialog.open(EventSheetComponent, {
      data: { id: draft.id, isNew: true, draft } satisfies EventSheetDialogData,
      width: '960px',
      maxWidth: '95vw',
      height: '90vh',
      restoreFocus: false,
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
    this.dialog.open(ApiKeysDialogComponent, {
      width: '680px',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  openEditAccount(): void {
    this.dialog.open(EditAccountDialogComponent, { width: '520px', autoFocus: false });
  }

  openPermissionMigration(): void {
    this.dialog
      .open(PermissionMigrationDialogComponent, {
        width: '640px',
        maxWidth: '92vw',
        autoFocus: false,
      })
      .afterClosed()
      .subscribe(() => {
        // Worklist may have been drained — re-evaluate so the menu entry hides when empty.
        this.permissionMigration.findings().subscribe({
          next: (list) => this.hasMigrationItems.set(list.length > 0),
          error: () => this.hasMigrationItems.set(false),
        });
      });
  }
}
