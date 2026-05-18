import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';

import { ReservationsComponent } from './reservations.component';
import { RemoteStorageControllerService } from '../api/api/remote-storage-controller.service';

describe('ReservationsComponent', () => {
  const getResources = vi.fn().mockReturnValue(
    of({
      resources: [],
      users: [{ id: 'u-test-123', username: 'testadmin' }],
      userId: 'u-test-123',
    }),
  );
  const queryAppointments = vi.fn().mockReturnValue(of({ reservations: [] }));

  beforeEach(() => {
    getResources.mockClear();
    queryAppointments.mockClear();
    TestBed.configureTestingModule({
      imports: [ReservationsComponent],
      providers: [
        provideAnimationsAsync(),
        provideRouter([]),
        provideOAuthClient(),
        {
          provide: RemoteStorageControllerService,
          useValue: { getResources, queryAppointments },
        },
      ],
    });
  });

  it('queries appointments scoped to the logged-in user by ownerIds, not by resource', () => {
    const fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();

    expect(queryAppointments).toHaveBeenCalledTimes(1);
    const job = queryAppointments.mock.calls[0][0];
    expect(job.resources).toBeUndefined();
    expect(job.ownerIds).toEqual(['u-test-123']);
    expect(job.start).toBeTruthy();
    expect(job.end).toBeTruthy();
  });

  it("shows the logged-in user's username in the toolbar", () => {
    const fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement.querySelector('.username') as HTMLElement | null;
    expect(el?.textContent?.trim()).toBe('testadmin');
  });
});
