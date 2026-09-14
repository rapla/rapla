import { Component, effect, inject, signal, computed, OnInit } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

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
import { newScopedDraft, withScopeAllocations, type EventDraft } from '../event/event-draft';
import { NewEventOptionsService } from '../event/new-event-options.service';
import { NewEventPickerComponent } from '../event/new-event-picker.component';
import { ImportWorklistService } from '../import/import-worklist.service';
import {
  ImportSyncDialogComponent,
  type ImportSyncDialogData,
} from '../import/import-sync-dialog.component';
import { type PickItem } from '../event/new-event-picker-model';
import { TemplateInstantiationService } from '../event/template-instantiation.service';
import { ViewStateStore } from '../state/view-state-store';
import { UndoToastService } from '../actions/undo-toast.service';
import { FilterStore } from '../state/filter-store';
import {
  currentSemester,
  openItemsOfGroups,
  semesterRange,
  windowSemesterDate,
} from '../import/import-models';

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
      @if (canCreate()) {
        <button matButton class="new-event" (click)="newEvent()" title="Neues Ereignis anlegen">
          <mat-icon>add</mat-icon>
          <span class="new-event-label">Neu</span>
        </button>
      }
      @if (importSyncAvailable()) {
        <button
          matButton
          class="new-event"
          (click)="openImportSync()"
          title="Externe Veranstaltungen der aktuellen Auswahl: übernehmen, Verknüpfte und Auffällige ansehen"
        >
          <mat-icon>move_to_inbox</mat-icon>
          <span class="new-event-label">{{ importSyncLabel() }}</span>
          <span class="import-sync-badge" [class.done]="importSyncDone()">{{
            importSyncBadge()
          }}</span>
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
      .import-sync-badge {
        font-size: 11px;
        font-weight: 700;
        border-radius: 9px;
        padding: 0 7px;
        margin-left: 4px;
        background: var(--mat-sys-error-container);
        color: var(--mat-sys-on-error-container);
      }
      .import-sync-badge.done {
        background: #d3e8d0;
        color: #1e4620;
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
  private readonly snackBar = inject(MatSnackBar);
  private readonly templateInstantiation = inject(TemplateInstantiationService);
  private readonly viewState = inject(ViewStateStore);
  /** PRD 094 D2 — main-view command history behind the header ↶/↷ buttons. */
  protected readonly undo = inject(UndoToastService);
  private readonly filter = inject(FilterStore);

  /** PRD 104 D6 — creatable event types + templates from newEventOptions
   *  (canCreate-filtered + wizard-plugin-gated server-side). ONE "Neu" button;
   *  it opens the unified picker dialog, except for the classic exactly-1-type/
   *  0-templates case (direct-open). Hidden when the caller can create nothing. */
  protected readonly newOptions = inject(NewEventOptionsService);
  protected readonly canCreate = computed(
    () => this.newOptions.eventTypes().length > 0 || this.newOptions.templates().length > 0,
  );

  /** PRD 104 v3 — "Dualis-Sync" next to "Neu" (toolbar = home of global creator
   *  actions). Visibility (user rule 2026-08-11, sharpened same day): the button
   *  shows WHENEVER an import source is deployed — an empty selection or a
   *  semester with nothing to do reads "0/0" instead of vanishing (a hidden
   *  button is indistinguishable from a broken query). A failed load shows "!". */
  private readonly importWorklist = inject(ImportWorklistService);
  private readonly chipIds = computed(() =>
    this.filter
      .entries()
      .filter((c) => c.kind === 'resource')
      .map((c) => c.id),
  );
  private readonly importSyncGroups = computed(() => {
    const wl = this.importWorklist.worklist();
    if (!wl) return [];
    const ids = new Set(this.chipIds());
    return wl.groups.filter((g) => ids.has(g.id));
  });
  protected readonly importSyncAvailable = computed(() => this.importWorklist.sourceName() !== '');
  /** Open items of the visible semester in the selection (worklist is unscoped). */
  private readonly importOpenCount = computed(() => {
    const w = this.viewState.window();
    const semester = w ? currentSemester(windowSemesterDate(w)) : undefined;
    return openItemsOfGroups(this.importWorklist.worklist()?.items ?? [], this.chipIds(), semester)
      .length;
  });
  /** "24/27" — linked of total (linked + open) in the selection; "!" = query
   *  failed (must never masquerade as 0/0). Linked counts come from the
   *  reservations themselves (durable stamp), not the Halde. */
  protected readonly importSyncBadge = computed(() => {
    if (this.importWorklist.loadFailed()) return '!';
    const linked = this.importWorklist.linked().length;
    return `${linked}/${linked + this.importOpenCount()}`;
  });
  /** All linked, nothing open → the badge turns green (done), never in the
   *  error case (done and broken must not share a color). */
  protected readonly importSyncDone = computed(
    () =>
      !this.importWorklist.loadFailed() &&
      this.importOpenCount() === 0 &&
      this.importWorklist.linked().length > 0,
  );
  protected readonly importSyncLabel = computed(
    () => `${this.importWorklist.sourceName() || 'Import'}-Sync`,
  );

  protected openImportSync(): void {
    const groups = this.importSyncGroups();
    const w = this.viewState.window();
    const data: ImportSyncDialogData = {
      groupIds: groups.length > 0 ? groups.map((g) => g.id) : this.chipIds(),
      groupLabel: groups.map((g) => g.name).join(', ') || 'aktuelle Auswahl',
      semester: currentSemester(w ? windowSemesterDate(w) : new Date()),
      defaultStart: `${(this.viewState.window()?.from ?? new Date().toISOString()).slice(0, 10)}T08:00:00`,
    };
    this.dialog
      .open(ImportSyncDialogComponent, { data, autoFocus: false })
      .afterClosed()
      .subscribe((outcome) => {
        // Post-park flow: nothing is stored yet — just make sure the Parkstreifen
        // is visible (week render mode); placement happens by drag from there.
        if (outcome === 'parked') this.viewState.setRenderMode('week');
      });
  }

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

  /** Kurs-Scope worklist: reload whenever the selected resource chips change
   *  (ensureLoaded reads the chip signal, so this effect tracks it). Must never
   *  break the toolbar — swallow errors, the button just stays hidden. */
  private readonly worklistSync = effect(() => {
    try {
      // The linked-reservations query spans the SEMESTER of the visible window
      // (user decision 2026-08-12, option A): list, badge and dismiss share one
      // scope. The worklist itself loads unscoped (join safety, 2026-08-11).
      const w = this.viewState.window();
      if (w) this.importWorklist.windowRange.set(semesterRange(windowSemesterDate(w)));
      // Swallow (button stays hidden) but LOG — a silent schema/validation error
      // here cost a live debugging round (2026-08-11, verwaist field removal).
      this.importWorklist.ensureLoaded().subscribe({
        error: (e: unknown) => console.error('[import] worklist load failed', e),
      });
    } catch {
      /* toolbar works without the sync button */
    }
  });

  ngOnInit(): void {
    // PRD 051 — non-empty /api/users (server-filtered by canAdminUser) ⇒ enable switch.
    this.usersService.list().subscribe((list) => this.canImpersonate.set(list.length > 0));
    // PRD 050 — local users (no external IdP) can edit their account.
    this.profile.capabilities().subscribe({
      next: (caps) => this.showEditAccount.set(caps.externalIdpLabel == null),
      error: () => this.showEditAccount.set(false),
    });
    // PRD 104 — the caller's "Neu" options (types canCreate-filtered server-side).
    this.newOptions.ensureLoaded().subscribe();
    // PRD 090 — only surface the migration entry when there is at least one open item.
    if (this.isAdmin()) {
      this.permissionMigration.findings().subscribe({
        next: (list) => this.hasMigrationItems.set(list.length > 0),
        error: () => this.hasMigrationItems.set(false),
      });
    }
  }

  /**
   * PRD 104 D6 — "Neu" opens the unified picker (types + templates); only the
   * exactly-1-type/0-templates case skips the dialog. A type pick opens the
   * FULL event editor as a dialog over the current view (PRD 091); a template
   * pick instantiates onto the current calendar window date (D8 cascade —
   * window date > today; the drag entry point in view-host supplies the slot).
   */
  newEvent(): void {
    const types = this.newOptions.eventTypes();
    if (types.length === 1 && this.newOptions.templates().length === 0) {
      this.openTypeSheet(types[0].key);
      return;
    }
    this.dialog
      .open(NewEventPickerComponent, {
        width: '560px',
        maxWidth: '95vw',
        autoFocus: false,
        restoreFocus: false,
      })
      .afterClosed()
      .subscribe((item?: PickItem) => {
        if (!item) return;
        if (item.kind === 'type') this.openTypeSheet(item.id);
        else this.instantiateTemplate(item.id);
      });
  }

  private openTypeSheet(typeKey: string): void {
    // Swing parity: the view's selected resources become allocations of the new
    // event. The mapping lives in newScopedDraft so every "new from a scoped
    // view" entry point shares one implementation.
    this.openSheet(newScopedDraft(typeKey, new Date(), this.filter.entries()));
  }

  private instantiateTemplate(templateId: string): void {
    const windowFrom = this.viewState.window()?.from;
    const day = (windowFrom ?? new Date().toISOString()).slice(0, 10);
    this.templateInstantiation.instantiate(templateId, { day, startMin: null }).subscribe({
      next: (instantiated) => {
        const draft = instantiated && withScopeAllocations(instantiated, this.filter.entries());
        if (!draft) {
          this.snackBar.open(
            'Die Vorlage enthält keine Termine — bitte zuerst Termine in der Vorlage anlegen.',
            undefined,
            { duration: 4000 },
          );
          return;
        }
        this.openSheet(draft);
      },
      error: () =>
        this.snackBar.open('Vorlage konnte nicht geladen werden (Serverfehler).', undefined, {
          duration: 4000,
        }),
    });
  }

  private openSheet(draft: EventDraft): void {
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
