import { describe, it, expect } from 'vitest';

import type { DraftAllocation } from './event-draft';
import { allocationEntries, needsSearchHint } from './search-hint';

const alloc = (resourceId: string, resourceName: string): DraftAllocation => ({
  resourceId,
  resourceName,
  appointmentIds: null,
});

describe('search hint (PRD 119 D4)', () => {
  it('hints when none of the event resources is selected', () => {
    const allocations = [alloc('r1', 'Hörsaal 1'), alloc('p1', 'Prof. Lehmann')];
    expect(needsSearchHint(allocations, () => false)).toBe(true);
  });

  it('does not hint once one of them is selected', () => {
    const allocations = [alloc('r1', 'Hörsaal 1'), alloc('p1', 'Prof. Lehmann')];
    expect(needsSearchHint(allocations, (id) => id === 'p1')).toBe(false);
  });

  it('does not hint for an event without resources', () => {
    expect(needsSearchHint([], () => false)).toBe(false);
  });

  it('turns the event resources into resource filter entries', () => {
    expect(allocationEntries([alloc('r1', 'Hörsaal 1'), alloc('p1', 'Prof. Lehmann')])).toEqual([
      { id: 'r1', kind: 'resource', label: 'Hörsaal 1' },
      { id: 'p1', kind: 'resource', label: 'Prof. Lehmann' },
    ]);
  });
});
