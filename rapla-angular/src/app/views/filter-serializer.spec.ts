import { describe, it, expect } from 'vitest';
import { filterToReservationFilter } from './filter-serializer';
import type { FilterEntry } from '../state/filter-store';

const W = { from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' };
const res = (id: string): FilterEntry => ({ id, kind: 'resource', label: id });
const evt = (id: string): FilterEntry => ({ id, kind: 'event', label: id });

describe('filterToReservationFilter', () => {
  it('with no chips returns just the window', () => {
    expect(filterToReservationFilter([], W)).toEqual({ from: W.from, to: W.to });
  });

  it('maps resource chips to allocatableIdsIn (occupancy = ANY of these ids)', () => {
    expect(filterToReservationFilter([res('a1'), res('a2')], W)).toEqual({
      from: W.from,
      to: W.to,
      allocatableIdsIn: ['a1', 'a2'],
    });
  });

  it('a single resource chip (the stepping case) filters to that resource', () => {
    expect(filterToReservationFilter([res('C348')], W)).toEqual({
      from: W.from,
      to: W.to,
      allocatableIdsIn: ['C348'],
    });
  });

  it('ignores event chips for now (no reservation-id filter on ReservationFilter)', () => {
    // Event chips do not yet constrain the query — navigate is their primary action.
    expect(filterToReservationFilter([evt('e1')], W)).toEqual({ from: W.from, to: W.to });
  });

  it('keeps only the resource ids when chips are mixed', () => {
    expect(filterToReservationFilter([res('a1'), evt('e1'), res('a2')], W)).toEqual({
      from: W.from,
      to: W.to,
      allocatableIdsIn: ['a1', 'a2'],
    });
  });
});
