import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AppToolbarComponent } from './app-toolbar.component';
import { AuthService, Identity } from '../auth/auth.service';
import { UsersService } from '../auth/users.service';
import { ProfileService, ProfileEditCapabilities } from '../account/profile.service';

function configure(
  identityValue: Identity | null,
  users: { username: string; displayName: string }[],
  caps: ProfileEditCapabilities = {
    canChangePassword: true,
    canChangeName: true,
    canChangeEmail: true,
    externalIdpLabel: null,
  },
) {
  const identity = signal<Identity | null>(identityValue);
  TestBed.configureTestingModule({
    imports: [AppToolbarComponent],
    providers: [
      provideAnimationsAsync(),
      { provide: UsersService, useValue: { list: () => of(users) } },
      { provide: ProfileService, useValue: { capabilities: () => of(caps) } },
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
});
