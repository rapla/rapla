import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
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
) {
  const identity = signal<Identity | null>(identityValue);
  TestBed.configureTestingModule({
    imports: [AppToolbarComponent],
    providers: [
      provideAnimationsAsync(),
      { provide: UsersService, useValue: { list: () => of(users) } },
      { provide: ProfileService, useValue: { capabilities: () => of(caps) } },
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
    const avatar = fixture.nativeElement.querySelector('.user-trigger .avatar') as HTMLElement | null;
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
  function openAccountSubmenuLabels(
    fixture: ReturnType<typeof TestBed.createComponent>,
  ): string[] {
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
