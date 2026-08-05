import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { GraphqlService } from '../graphql/graphql.service';
import { NewEventOptionsService } from './new-event-options.service';

/** PRD 104 Phase 2 — session-shared fetch of the "Neu" options. */
describe('NewEventOptionsService', () => {
  const response = {
    data: {
      newEventOptions: {
        eventTypes: [{ key: 'event', name: 'Veranstaltung' }],
        templates: [{ id: 't1', name: 'TINF23 Mathe', path: ['TINF23'] }],
      },
    },
  };

  function setup(query: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      providers: [{ provide: GraphqlService, useValue: { query } }],
    });
    return TestBed.inject(NewEventOptionsService);
  }

  it('exposes types and templates from newEventOptions', () => {
    const query = vi.fn(() => of(response));
    const service = setup(query);
    service.ensureLoaded().subscribe();
    expect(service.eventTypes()).toEqual([{ key: 'event', name: 'Veranstaltung' }]);
    expect(service.templates()).toEqual([{ id: 't1', name: 'TINF23 Mathe', path: ['TINF23'] }]);
  });

  it('shares one request across repeated callers', () => {
    const query = vi.fn(() => of(response));
    const service = setup(query);
    service.ensureLoaded().subscribe();
    service.ensureLoaded().subscribe();
    expect(query).toHaveBeenCalledTimes(1);
  });

  it('recovers after a failed fetch instead of caching the error', () => {
    const query = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new Error('boom')))
      .mockReturnValueOnce(of(response));
    const service = setup(query);
    let first: unknown;
    service.ensureLoaded().subscribe((o) => (first = o));
    expect(first).toEqual({ eventTypes: [], templates: [] });
    service.ensureLoaded().subscribe();
    expect(query).toHaveBeenCalledTimes(2);
    expect(service.eventTypes().length).toBe(1);
  });
});
