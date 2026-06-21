import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { App } from './app';
import { AuthService, Identity } from './auth/auth.service';
import { UsersService } from './auth/users.service';

const IDENTITY: Identity = {
  username: 'testadmin',
  name: 'Test Admin',
  admin: false,
  roles: [],
  impersonating: false,
  actor: null,
  target: null,
};

describe('App', () => {
  beforeEach(async () => {
    // App now hosts the global toolbar (AuthService / UsersService) + the
    // Material sidenav (animations). Provide stubs so the shell renders.
    const identity = signal<Identity | null>(IDENTITY);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: UsersService, useValue: { list: () => of([]) } },
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
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the routed view through a router outlet', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('router-outlet')).toBeTruthy();
  });

  it('renders the global toolbar and the sidenav nav items', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Rapla');
    expect(text).toContain('Termine');
    expect(text).toContain('Reservierungen');
  });
});
