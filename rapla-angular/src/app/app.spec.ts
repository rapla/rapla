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
import { ReviewStore } from './state/review-store';
import { FilterStore } from './state/filter-store';
import { ViewStateStore } from './state/view-state-store';
import type { ConflictCount, RequestWire } from './state/review-tree';

const VIEWS: ViewInfo[] = [
  { name: 'Wochenansicht', title: 'Wochenansicht', source: 'CUSTOM' },
  { name: 'rapla_appointments', title: 'Termine', source: 'BUILTIN' },
  {
    name: 'rapla_conflicts',
    title: 'Konflikte',
    source: 'BUILTIN',
    selection: 'CONFLICTS',
    defaultRenderMode: 'day',
  },
  { name: 'rapla_requests', title: 'Ressourcenanfragen', source: 'BUILTIN', selection: 'REQUESTS' },
];

/** PRD 128 — the review data the shell reads; loads are recorded, not sent. */
const review = {
  counts: signal<ConflictCount[]>([]),
  requests: signal<RequestWire[]>([]),
  loaded: signal(new Map()),
  ensureLoaded: vi.fn(),
  refresh: vi.fn(),
  loadConflicts: vi.fn(),
};

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
  let identity: ReturnType<typeof signal<Identity | null>>;

  beforeEach(async () => {
    // App now hosts the global toolbar (AuthService / UsersService) + the
    // Material sidenav (animations). Provide stubs so the shell renders.
    identity = signal<Identity | null>(IDENTITY);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'views/:viewName', component: RoutedStub }]),
        provideAnimationsAsync(),
        { provide: UsersService, useValue: { list: () => of([]) } },
        { provide: ViewCatalogService, useValue: { listViews: () => of(VIEWS) } },
        { provide: ReviewStore, useValue: review },
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

  describe('PRD 128 D7 — Konflikte / Ressourcenanfragen as views', () => {
    beforeEach(() => {
      review.counts.set([]);
      review.requests.set([]);
      review.refresh.mockReset();
    });

    const badges = (el: HTMLElement) =>
      Array.from(el.querySelectorAll<HTMLAnchorElement>('.view-switch a.review-badge')).map((b) =>
        b.textContent?.replace(/\s+/g, ' ').trim(),
      );

    it('red badges next to the selector show the ACTIVE conflicts and the requests, only while > 0', async () => {
      const f = TestBed.createComponent(App);
      f.detectChanges();
      const el = f.nativeElement as HTMLElement;
      expect(badges(el)).toEqual([]);
      review.counts.set([
        { resourceId: 'r1', disabled: false, count: 3 },
        { resourceId: 'r1', disabled: true, count: 5 },
      ]);
      review.requests.set([{} as RequestWire, {} as RequestWire]);
      f.detectChanges();
      expect(badges(el)).toEqual(['warning 3', 'pending_actions 2']);
      el.querySelector<HTMLAnchorElement>('a.review-badge')!.click();
      await f.whenStable();
      expect(TestBed.inject(Router).url).toBe('/views/rapla_conflicts');
    });

    it('the menu entries of both views carry the same icon and count', async () => {
      review.counts.set([{ resourceId: 'r1', disabled: false, count: 3 }]);
      const f = TestBed.createComponent(App);
      f.detectChanges();
      ((f.nativeElement as HTMLElement).querySelector('.view-trigger') as HTMLElement).click();
      f.detectChanges();
      await f.whenStable();
      const entry = Array.from(document.querySelectorAll('.mat-mdc-menu-panel a')).find((a) =>
        a.textContent?.includes('Konflikte'),
      );
      expect(entry?.querySelector('.review-count')?.textContent?.replace(/\s+/g, '')).toBe(
        'warning3',
      );
    });

    it('the view menu lists the planning views, then the stored ones, then conflicts / requests, each block behind a divider', async () => {
      const f = TestBed.createComponent(App);
      f.detectChanges();
      ((f.nativeElement as HTMLElement).querySelector('.view-trigger') as HTMLElement).click();
      f.detectChanges();
      await f.whenStable();
      const panel = document.querySelector('.mat-mdc-menu-panel')!;
      const order = Array.from(panel.querySelectorAll('a.mat-mdc-menu-item, mat-divider')).map(
        (e) =>
          e.tagName === 'MAT-DIVIDER'
            ? '---'
            : (e.querySelector('span')?.textContent?.trim() ?? ''),
      );
      expect(order).toEqual([
        'Termine',
        '---',
        'Wochenansicht',
        '---',
        'Konflikte',
        'Ressourcenanfragen',
      ]);
    });

    it('a Prüfen view switches the chip context and the left pane; a planning view brings Planen back (D3)', async () => {
      const filter = TestBed.inject(FilterStore);
      const f = TestBed.createComponent(App);
      f.detectChanges();
      await TestBed.inject(Router).navigate(['/views', 'rapla_conflicts']);
      f.detectChanges();
      expect(filter.context()).toBe('conflicts');
      expect(review.refresh).toHaveBeenCalledWith('conflicts');
      expect(TestBed.inject(ViewStateStore).renderMode()).toBe('day');
      expect(
        (f.nativeElement as HTMLElement).querySelector('app-resource-selection .sechead'),
      ).toBeNull();
      await TestBed.inject(Router).navigate(['/views', 'rapla_appointments']);
      f.detectChanges();
      expect(filter.context()).toBe('plan');
    });
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

  /** PRD 118 D8-8 — the demo instance says so in the shell; the text comes from GET /api/auth/me. */
  it('dragging the grip at the rail edge resizes the sidenav; a double click restores 290 px (PRD 127)', () => {
    localStorage.clear();
    const f = TestBed.createComponent(App);
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    const nav = el.querySelector<HTMLElement>('mat-sidenav')!;
    const grip = el.querySelector('mat-sidenav .nav-resize')!;
    expect(nav.style.width).toBe('290px');
    const fire = (type: string, clientX: number) =>
      grip.dispatchEvent(new MouseEvent(type, { clientX, bubbles: true }));
    fire('pointerdown', 290);
    fire('pointermove', 410);
    fire('pointerup', 410);
    f.detectChanges();
    expect(nav.style.width).toBe('410px');
    fire('dblclick', 0);
    f.detectChanges();
    expect(nav.style.width).toBe('290px');
  });

  it('shows the demo banner from the identity', () => {
    identity.set({ ...IDENTITY, demoBanner: 'Demo — data resets nightly at 04:15' });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const banner = (fixture.nativeElement as HTMLElement).querySelector('.demo-banner');
    expect(banner?.textContent?.trim()).toBe('Demo — data resets nightly at 04:15');
  });

  it('shows no banner outside the demo', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.demo-banner')).toBeNull();
  });
});
