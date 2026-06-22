import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AppToolbarComponent } from './app-toolbar.component';
import { AuthService, Identity } from '../auth/auth.service';
import { UsersService } from '../auth/users.service';

function configure(identityValue: Identity | null, users: { username: string; displayName: string }[]) {
  const identity = signal<Identity | null>(identityValue);
  TestBed.configureTestingModule({
    imports: [AppToolbarComponent],
    providers: [
      provideAnimationsAsync(),
      { provide: UsersService, useValue: { list: () => of(users) } },
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

describe('AppToolbarComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('shows the logged-in username', () => {
    configure(LOGGED_IN, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.username') as HTMLElement | null;
    expect(el?.textContent?.trim()).toBe('testadmin');
  });

  it('makes the username chip a switch trigger when the caller can admin users', () => {
    configure(LOGGED_IN, [{ username: 'homer', displayName: 'Homer' }]);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    const chip = fixture.nativeElement.querySelector('.username-clickable') as HTMLElement | null;
    expect(chip).not.toBeNull();
    expect(chip?.textContent).toContain('testadmin');
  });

  it('renders Sign out when not impersonating', () => {
    configure(LOGGED_IN, []);
    const fixture = TestBed.createComponent(AppToolbarComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sign out');
  });
});
