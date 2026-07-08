import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AvailabilitySearchService } from './availability-search.service';
import { GraphqlService } from '../graphql/graphql.service';
import type { DraftAppointment } from './event-draft';

/**
 * PRD 091 Phase 4.5 side-finding: the service used to swallow GraphQL
 * errors[] silently (data null → empty rows), which hid the
 * repeating-UNSUPPORTED gap for weeks. Errors must at least be logged;
 * the pipeline stays alive (empty result, no observable error — the
 * sheet's refresh$ subscription must survive).
 */

const APPOINTMENT: DraftAppointment = {
  id: 'a1',
  start: '2031-06-02T09:00:00',
  end: '2031-06-02T17:00:00',
  allDay: false,
  repeating: null,
};

describe('AvailabilitySearchService', () => {
  const query = vi.fn();

  beforeEach(() => {
    query.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: GraphqlService, useValue: { query } }],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  it('logs GraphQL errors instead of swallowing them, result stays empty', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    query.mockReturnValue(
      of({ data: null, errors: [{ message: 'boom', extensions: { code: 'UNSUPPORTED' } }] }),
    );
    const svc = TestBed.inject(AvailabilitySearchService);

    const result = await new Promise((resolve) =>
      svc.search([APPOINTMENT], '', ['r1'], null).subscribe(resolve),
    );

    expect(result).toEqual({ hits: [], byId: new Map() });
    expect(warn).toHaveBeenCalledWith(
      '[availability] resourceAvailability errors:',
      expect.arrayContaining([expect.objectContaining({ message: 'boom' })]),
    );
  });

  it('does not warn on a clean response', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    query.mockReturnValue(
      of({
        data: {
          resourceAvailability: [
            { allocatable: { id: 'r1', name: 'Room' }, status: 'AVAILABLE', conflictingAppointmentIds: [] },
          ],
        },
      }),
    );
    const svc = TestBed.inject(AvailabilitySearchService);

    const result = (await new Promise((resolve) =>
      svc.search([APPOINTMENT], '', ['r1'], null).subscribe(resolve),
    )) as { byId: Map<string, unknown> };

    expect(result.byId.get('r1')).toMatchObject({ status: 'AVAILABLE' });
    expect(warn).not.toHaveBeenCalled();
  });
});
