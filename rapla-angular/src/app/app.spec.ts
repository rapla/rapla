import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { Component, signal } from '@angular/core';
import { of } from 'rxjs';

import { App } from './app';
import { AuthService, Identity } from './auth/auth.service';
import { UsersService } from './auth/users.service';
import { ViewCatalogService, type ViewInfo } from './views/view-catalog.service';

const VIEWS: ViewInfo[] = [
  { name: 'Wochenansicht', title: 'Wochenansicht', source: 'CUSTOM' },
  { name: 'rapla_appointments', title: 'Termine', source: 'BUILTIN' },
];

const IDENTITY: Identity = {
  userId: 'u-1',
  username: 'testadmin',
  name: 'Test Admin',
  admin: false,
  roles: [],
  impersonating: false,
  actor: null,
  target: null,
};

@Component({ template: '' })
class RoutedStub {}

describe('App', () => {
  beforeEach(async () => {
    // App now hosts the global toolbar (AuthService / UsersService) + the
    // Material sidenav (animations). Provide stubs so the shell renders.
    const identity = signal<Identity | null>(IDENTITY);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'views/:viewName', component: RoutedStub }]),
        provideAnimationsAsync(),
        { provide: UsersService, useValue: { list: () => of([]) } },
        { provide: ViewCatalogService, useValue: { listViews: () => of(VIEWS) } },
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
    await TestBed.inject(Router).navigate(['/views', 'rapla_appointments']);
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

  it('renders the global toolbar', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent ?? '').toContain('Rapla');
  });

  /**
   * The switcher is a picker, not a list: the catalog grows with every stored view, and a list
   * takes its height straight out of the resource selection below it. Only the active view is on
   * screen; the rest live in the menu.
   */
  it('shows only the active view in the sidenav, not the whole catalog', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector('.view-trigger');
    expect(trigger).toBeTruthy();
    expect(trigger?.textContent).toContain('Termine');
    expect((fixture.nativeElement as HTMLElement).textContent ?? '').not.toContain('Wochenansicht');
  });

  it('lists every catalog view once the picker is opened', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    ((fixture.nativeElement as HTMLElement).querySelector('.view-trigger') as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const menu = document.querySelector('.mat-mdc-menu-panel')?.textContent ?? '';
    expect(menu).toContain('Wochenansicht');
    expect(menu).toContain('Termine');
  });

  /** The label follows the route — that is where the active view actually lives. */
  it('follows the route when another view is opened', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigate(['/views', 'Wochenansicht']);
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector('.view-trigger');
    expect(trigger?.textContent).toContain('Wochenansicht');
  });

  /** Off a view route (first paint, the default-view redirect) — never an empty button. */
  it('falls back to a neutral label when no view is routed', async () => {
    await TestBed.inject(Router).navigateByUrl('/');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector('.view-trigger');
    expect(trigger?.textContent?.trim()).toBeTruthy();
  });
});
