import { describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { EditAccountDialogComponent } from './edit-account-dialog.component';
import { ProfileService, ProfileEditCapabilities } from './profile.service';
import { AuthService, Identity } from '../auth/auth.service';

const IDENTITY: Identity = {
  userId: 'u1',
  username: 'homer',
  name: 'Homer Simpson',
  admin: false,
  roles: [],
  impersonating: false,
  actor: null,
  target: null,
};

const LOCAL: ProfileEditCapabilities = {
  canChangePassword: true,
  canChangeName: true,
  canChangeEmail: true,
  externalIdpLabel: null,
};

const PROVISIONED: ProfileEditCapabilities = {
  canChangePassword: false,
  canChangeName: false,
  canChangeEmail: false,
  externalIdpLabel: 'keycloak:dhbw',
};

function configure(caps: ProfileEditCapabilities, profileStub: Partial<ProfileService> = {}) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [EditAccountDialogComponent],
    providers: [
      provideAnimationsAsync(),
      { provide: ProfileService, useValue: { capabilities: () => of(caps), ...profileStub } },
      { provide: AuthService, useValue: { identity: signal<Identity | null>(IDENTITY) } },
    ],
  });
}

describe('EditAccountDialogComponent', () => {
  it('renders the three edit sections for a local user', async () => {
    configure(LOCAL);
    const fixture = TestBed.createComponent(EditAccountDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Name');
    expect(text).toContain('E-mail');
    expect(text).toContain('Password');
    expect(text).not.toContain('managed by your identity provider');
  });

  it('shows the read-only banner and no sections for a provisioned user', async () => {
    configure(PROVISIONED);
    const fixture = TestBed.createComponent(EditAccountDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const c = fixture.componentInstance;
    expect(c.externalIdpLabel()).toBe('keycloak:dhbw');
    expect(c.canChangeName()).toBe(false);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('managed by your identity provider');
  });

  it('posts a name change carrying the caller username and three parts', async () => {
    const changeName = vi.fn(() => of(undefined));
    configure(LOCAL, { changeName });
    const fixture = TestBed.createComponent(EditAccountDialogComponent);
    const c = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    c.nameForm.setValue({ title: 'Mr', firstname: 'Homer', lastname: 'Simpson' });
    c.saveName();

    expect(changeName).toHaveBeenCalledWith('homer', 'Mr', 'Homer', 'Simpson');
    expect(c.successMessage()).toContain('Name updated');
  });

  it('blocks the password save until the confirmation matches', () => {
    configure(LOCAL);
    const fixture = TestBed.createComponent(EditAccountDialogComponent);
    const c = fixture.componentInstance;
    c.passwordForm.setValue({ oldPassword: 'old', newPassword: 'a', confirm: 'b' });
    expect(c.passwordsMatch()).toBe(false);
    c.passwordForm.setValue({ oldPassword: 'old', newPassword: 'a', confirm: 'a' });
    expect(c.passwordsMatch()).toBe(true);
  });
});
