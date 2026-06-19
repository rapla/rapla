import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { signal } from '@angular/core';

import { ReservationsComponent } from './reservations.component';
import { AuthService, Identity } from '../auth/auth.service';
import { UsersService } from '../auth/users.service';

describe('ReservationsComponent', () => {
  const get = vi
    .fn()
    .mockReturnValue(of({ id: 'u-rapla-id', username: 'testadmin', displayName: 'Test Admin' }));
  const post = vi.fn().mockReturnValue(
    of({
      columns: [
        { id: 'name', label: 'Name', type: 'STRING' },
        { id: 'start', label: 'Start', type: 'DATE' },
      ],
      rows: [{ id: 'r1', cells: { name: 'Event A', start: '2026-05-18T10:00:00' } }],
      totalCount: 1,
      incomplete: false,
    }),
  );

  beforeEach(() => {
    get.mockClear();
    post.mockClear();
    TestBed.configureTestingModule({
      imports: [ReservationsComponent],
      providers: [
        provideAnimationsAsync(),
        provideRouter([]),
        { provide: HttpClient, useValue: { get, post } },
        {
          provide: UsersService,
          useValue: { list: () => of([]) },
        },
        {
          provide: AuthService,
          useValue: ((): Partial<AuthService> => {
            // PRD 072 — the toolbar reads the identity signal (from
            // GET /api/auth/me) for the effective username + impersonation
            // badge. Default: a logged-in, non-impersonating user.
            const identity = signal<Identity | null>({
              username: 'testadmin',
              name: 'Test Admin',
              admin: false,
              roles: [],
              impersonating: false,
              actor: null,
              target: null,
            });
            return {
              identity,
              isImpersonating: () => identity()?.impersonating ?? false,
              actorUsername: () => identity()?.actor ?? '',
              signOut: vi.fn(),
              endImpersonation: vi.fn(async () => true),
              impersonate: vi.fn(async () => true),
            } as unknown as Partial<AuthService>;
          })(),
        },
      ],
    });
  });

  it('fetches /api/users/me and POSTs /api/table/reservations with the returned id', () => {
    const fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();

    // First call: GET /api/users/me to resolve the rapla User id.
    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0][0]).toBe('/api/users/me');

    // Second call: POST /api/table/reservations with owners=[<rapla id>].
    expect(post).toHaveBeenCalledTimes(1);
    const [url, body] = post.mock.calls[0];
    expect(url).toBe('/api/table/reservations');
    expect(body.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(body.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(body.owners).toEqual(['u-rapla-id']);
    // Window is roughly two years wide: a year before today through a
    // year after. Allow for month-end edge cases when the test runs
    // near a year boundary.
    const fromYear = Number(body.from.slice(0, 4));
    const toYear = Number(body.to.slice(0, 4));
    expect(toYear - fromYear).toBeGreaterThanOrEqual(1);
    expect(toYear - fromYear).toBeLessThanOrEqual(2);
  });

  it('paints the server-projected columns and rows', () => {
    const fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Name');
    expect(el.textContent).toContain('Event A');
  });

  it("shows the logged-in user's username in the toolbar", () => {
    const fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement.querySelector('.username') as HTMLElement | null;
    expect(el?.textContent?.trim()).toBe('testadmin');
  });
});
