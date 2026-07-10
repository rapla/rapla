import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AppToolbarComponent } from './app-toolbar.component';
import { AuthService, Identity } from '../auth/auth.service';
import { UsersService } from '../auth/users.service';
import { ProfileService, ProfileEditCapabilities } from '../account/profile.service';
import {
  PermissionMigrationFinding,
  PermissionMigrationService,
} from '../account/permission-migration.service';
import { GraphqlService } from '../graphql/graphql.service';
import { MatDialog } from '@angular/material/dialog';
import { UndoToastService } from '../actions/undo-toast.service';
import { FilterStore } from '../state/filter-store';
import { signal as ngSignal } from '@angular/core';

const dialogOpen = vi.fn();

function configure(
  identityValue: Identity | null,
  users: { username: string; displayName: string }[],
  caps: ProfileEditCapabilities = {
    canChangePassword: true,
    canChangeName: true,
    canChangeEmail: true,
    externalIdpLabel: null,
  },
  migrationFindings: PermissionMigrationFinding[] = [],
  eventTypes: { key: string; name: string }[] = [{ key: 'event', name: 'Veranstaltung' }],
  scopeChips: { id: string; kind: string; label: string }[] = [],
) {
  const identity = signal<Identity | null>(identityValue);
  dialogOpen.mockClear();
  TestBed.configureTestingModule({
    imports: [AppToolbarComponent],
    providers: [
      provideAnimationsAsync(),
      provideRouter([]),
      { provide: FilterStore, useValue: { entries: () => scopeChips } },
      { provide: UsersService, useValue: { list: () => of(users) } },
      { provide: ProfileService, useValue: { capabilities: () => of(caps) } },
      {
        provide: GraphqlService,
        useValue: {
          query: () =>
            of({
              data: {
                types: eventTypes.map((t) => ({ ...t, classificationType: 'RESERVATION' })),
              },
            }),
        },
      },
      { provide: MatDialog, useValue: { open: dialogOpen } },
      {
        provide: PermissionMigrationService,
        useValue: { findings: () => of(migrationFindings) },
      },
      {
        provide: AuthService,
        useValue: {
          identity,
          isImpersonating: () => identity()?.impersonating ?? false,
          actorUsername: () => identity()?.actor ?? '',
          signOut: vi.fn(),
          endImpersonation: vi.fn(async () => true),
        } as unknown as Partial<AuthService>,
      },
    ],
  });
}

const LOGGED_IN: Identity = {
  userId: 'u-1',
  username: 'testadmin',
  name: 'Test Admin',
  admin: false,
  roles: [],
  impersonating: false,
  actor: null,
  target: null,
};

/** Open the user menu and return the menu-item buttons rendered into the CDK overlay. */
function openMenuItems(fixture: ReturnType<typeof TestBed.createComponent>): HTMLElement[] {
  const trigger = fixture.nativeElement.querySelector('.user-trigger') as HTMLButtonElement;
  trigger.click();
  fixture.detectChanges();
  return Array.from(document.querySelectorAll('button.mat-mdc-menu-item')) as HTMLElement[];
}

describe('AppToolbarComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('shows the logged-in username in the menu trigger', () => {
    configure(LOGGED_IN, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.user-trigger .username') as HTMLElement | null;
    expect(el?.textContent?.trim()).toBe('testadmin');
  });

  it('renders avatar initials from the display name', () => {
    configure(LOGGED_IN, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const avatar = fixture.nativeElement.querySelector(
      '.user-trigger .avatar',
    ) as HTMLElement | null;
    expect(avatar?.textContent?.trim()).toBe('TA');
  });

  it('the menu contains Account settings and Sign out', () => {
    configure(LOGGED_IN, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const labels = openMenuItems(fixture).map((b) => b.textContent ?? '');
    expect(labels.some((t) => t.includes('Account settings'))).toBe(true);
    expect(labels.some((t) => t.includes('Sign out'))).toBe(true);
  });

  it('offers Switch to user only when the caller can admin users', () => {
    configure(LOGGED_IN, [{ username: 'homer', displayName: 'Homer' }]);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const labels = openMenuItems(fixture).map((b) => b.textContent ?? '');
    expect(labels.some((t) => t.includes('Switch to user'))).toBe(true);
  });

  it('omits Switch to user when the caller cannot admin anyone', () => {
    configure(LOGGED_IN, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const labels = openMenuItems(fixture).map((b) => b.textContent ?? '');
    expect(labels.some((t) => t.includes('Switch to user'))).toBe(false);
  });

  // PRD 090 — the "Permission migration" entry (in the Account settings submenu) is
  // shown only to a global admin AND only when the worklist is non-empty.
  const ADMIN: Identity = { ...LOGGED_IN, admin: true };
  const FINDING: PermissionMigrationFinding = {
    allocatableId: 'a1',
    allocatableName: 'Room A',
    escalations: [],
  };

  /** Open the user menu, then the Account-settings submenu; return its item labels. */
  function openAccountSubmenuLabels(fixture: ReturnType<typeof TestBed.createComponent>): string[] {
    const items = openMenuItems(fixture);
    const account = items.find((b) => (b.textContent ?? '').includes('Account settings'));
    account?.click();
    fixture.detectChanges();
    return (Array.from(document.querySelectorAll('button.mat-mdc-menu-item')) as HTMLElement[]).map(
      (b) => b.textContent ?? '',
    );
  }

  it('shows Permission migration for an admin with open worklist items', () => {
    configure(ADMIN, [], undefined, [FINDING]);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    expect(openAccountSubmenuLabels(fixture).some((t) => t.includes('Permission migration'))).toBe(
      true,
    );
  });

  it('hides Permission migration when the worklist is empty', () => {
    configure(ADMIN, [], undefined, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    expect(openAccountSubmenuLabels(fixture).some((t) => t.includes('Permission migration'))).toBe(
      false,
    );
  });

  it('hides Permission migration from a non-admin even if items exist', () => {
    configure(LOGGED_IN, [], undefined, [FINDING]);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    expect(openAccountSubmenuLabels(fixture).some((t) => t.includes('Permission migration'))).toBe(
      false,
    );
  });
});

describe('AppToolbarComponent — header undo/redo (PRD 094 D2)', () => {
  beforeEach(() => TestBed.resetTestingModule());

  function configureUndo(canUndo: boolean, canRedo: boolean) {
    const undo = vi.fn();
    const redo = vi.fn();
    const identity = ngSignal<Identity | null>(LOGGED_IN);
    TestBed.configureTestingModule({
      imports: [AppToolbarComponent],
      providers: [
        provideAnimationsAsync(),
        provideRouter([]),
        { provide: UsersService, useValue: { list: () => of([]) } },
        {
          provide: ProfileService,
          useValue: {
            capabilities: () =>
              of({
                canChangePassword: true,
                canChangeName: true,
                canChangeEmail: true,
                externalIdpLabel: null,
              }),
          },
        },
        { provide: PermissionMigrationService, useValue: { findings: () => of([]) } },
        { provide: GraphqlService, useValue: { query: () => of({ data: { types: [] } }) } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        {
          provide: UndoToastService,
          useValue: {
            canUndo: () => canUndo,
            canRedo: () => canRedo,
            undoLabel: () => 'A gelöscht',
            redoLabel: () => 'A wiederhergestellt',
            undo,
            redo,
          },
        },
        {
          provide: AuthService,
          useValue: {
            identity,
            isImpersonating: () => false,
            actorUsername: () => '',
            signOut: vi.fn(),
            endImpersonation: vi.fn(),
          } as unknown as Partial<AuthService>,
        },
      ],
    });
    return { undo, redo };
  }

  it('undo button disabled when the history is empty, enabled when it has entries', () => {
    configureUndo(false, false);
    let f = TestBed.createComponent(AppToolbarComponent);
    f.detectChanges();
    expect((f.nativeElement.querySelector('.undo-btn') as HTMLButtonElement).disabled).toBe(true);
    TestBed.resetTestingModule();
    configureUndo(true, false);
    f = TestBed.createComponent(AppToolbarComponent);
    f.detectChanges();
    expect((f.nativeElement.querySelector('.undo-btn') as HTMLButtonElement).disabled).toBe(false);
  });

  it('clicking undo / redo calls the service', () => {
    const { undo, redo } = configureUndo(true, true);
    const f = TestBed.createComponent(AppToolbarComponent);
    f.detectChanges();
    (f.nativeElement.querySelector('.undo-btn') as HTMLButtonElement).click();
    (f.nativeElement.querySelector('.redo-btn') as HTMLButtonElement).click();
    expect(undo).toHaveBeenCalledTimes(1);
    expect(redo).toHaveBeenCalledTimes(1);
  });
});

describe('AppToolbarComponent — type-aware Neu (PRD 094 Phase 2)', () => {
  beforeEach(() => TestBed.resetTestingModule());

  const TWO_TYPES = [
    { key: 'event', name: 'Veranstaltung' },
    { key: 'ausleihe', name: 'Ausleihe' },
  ];

  it('one creatable type: Neu opens the sheet directly with that type', () => {
    configure(LOGGED_IN, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.new-event') as HTMLButtonElement;
    btn.click();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    const [, config] = dialogOpen.mock.calls[0] as [
      unknown,
      { data: { id: string; isNew?: boolean; draft?: { id: string; typeKey: string } } },
    ];
    expect(config.data.isNew).toBe(true);
    expect(config.data.draft?.typeKey).toBe('event');
    expect(config.data.id).toBe(config.data.draft?.id);
  });

  it('Swing parity: resource scope chips are pre-added as allocations, users are not', () => {
    configure(
      LOGGED_IN,
      [],
      undefined,
      [],
      [{ key: 'event', name: 'Veranstaltung' }],
      [
        { id: 'r1', kind: 'resource', label: 'Kamera G40' },
        { id: 'u1', kind: 'user', label: 'admin' },
      ],
    );
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.new-event') as HTMLButtonElement).click();
    const [, config] = dialogOpen.mock.calls[0] as [
      unknown,
      {
        data: {
          draft?: {
            allocations: {
              allocatableId: string;
              allocatableName: string;
              appointmentIds: string[] | null;
            }[];
          };
        };
      },
    ];
    expect(config.data.draft?.allocations).toEqual([
      { allocatableId: 'r1', allocatableName: 'Kamera G40', appointmentIds: null },
    ]);
  });

  it('several creatable types: Neu opens a type menu, choice pre-selects the type', () => {
    configure(LOGGED_IN, [], undefined, [], TWO_TYPES);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.new-event') as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();
    expect(dialogOpen).not.toHaveBeenCalled(); // menu first, no direct open
    const items = Array.from(
      document.querySelectorAll<HTMLButtonElement>('button.mat-mdc-menu-item'),
    );
    const labels = items.map((b) => (b.textContent ?? '').trim());
    expect(labels).toEqual(['Veranstaltung', 'Ausleihe']);
    items[1].click();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    const [, config] = dialogOpen.mock.calls[0] as [
      unknown,
      { data: { draft?: { typeKey: string } } },
    ];
    expect(config.data.draft?.typeKey).toBe('ausleihe');
  });
});
