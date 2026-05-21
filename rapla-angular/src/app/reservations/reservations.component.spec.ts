import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';

import { ReservationsComponent } from './reservations.component';
import { TableViewControllerService } from '../api/api/table-view-controller.service';
import { AuthService } from '../auth/auth.service';

describe('ReservationsComponent', () => {
  const reservations = vi.fn().mockReturnValue(
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
    reservations.mockClear();
    TestBed.configureTestingModule({
      imports: [ReservationsComponent],
      providers: [
        provideAnimationsAsync(),
        provideRouter([]),
        { provide: TableViewControllerService, useValue: { reservations } },
        {
          provide: AuthService,
          useValue: {
            signOut: vi.fn(),
            identityClaims: () => ({ preferred_username: 'testadmin' }),
            // PRD 051 — the toolbar reads these to decide whether to
            // render the "Impersonating X" badge. Default: no
            // impersonation; individual tests can override.
            isImpersonating: () => false,
            impersonationOverride: () => null,
          },
        },
      ],
    });
  });

  it('fetches the server-rendered reservation table over a date window', () => {
    const fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();

    expect(reservations).toHaveBeenCalledTimes(1);
    const [from, to] = reservations.mock.calls[0];
    expect(from).toMatch(/^\d{4}-01-01$/);
    expect(to).toMatch(/^\d{4}-12-31$/);
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
