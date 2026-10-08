import { describe, expect, it } from 'vitest';

import type { ResourceItem } from '../state/resource-selection-store';
import { effectiveCourseIds, MAX_SYNC_COURSES, syncScope } from './import-sync-scope';

const item = (id: string, parentIds: string[] = []): ResourceItem =>
  ({ id, label: id, parentIds }) as ResourceItem;

describe('effectiveCourseIds — group chips resolve to their Kurse (dhbwrapla PRD 004 § H5a)', () => {
  const all = [
    item('group'),
    item('sub', ['group']),
    item('k1', ['group']),
    item('k2', ['sub']),
    item('k3'),
  ];

  it('keeps a Kurs chip as it is', () => {
    expect(effectiveCourseIds(['k3'], all)).toEqual(['k3']);
  });

  it('expands a group chip to all its leaf Kurse, also through sub-groups', () => {
    expect(effectiveCourseIds(['group'], all).sort()).toEqual(['k1', 'k2']);
  });

  it('counts a Kurs once when it is chosen directly and through its group', () => {
    expect(effectiveCourseIds(['group', 'k1'], all).sort()).toEqual(['k1', 'k2']);
  });

  it('survives a cycle in parentIds', () => {
    const cyclic = [item('a', ['b']), item('b', ['a']), item('leaf', ['a'])];
    expect(effectiveCourseIds(['a'], cyclic)).toEqual(['leaf']);
  });
});

describe('syncScope — the button is active only for 1 to 49 effective Kurse', () => {
  const ids = (n: number) => Array.from({ length: n }, (_, i) => `k${i}`);

  it('asks for a Kurs when none is chosen', () => {
    expect(syncScope([])).toEqual({ kind: 'none' });
  });

  it('is ready below the limit', () => {
    expect(syncScope(ids(MAX_SYNC_COURSES - 1))).toEqual({
      kind: 'ready',
      ids: ids(MAX_SYNC_COURSES - 1),
    });
  });

  it('asks to narrow from the limit on', () => {
    expect(MAX_SYNC_COURSES).toBe(50);
    expect(syncScope(ids(50))).toEqual({ kind: 'tooMany', count: 50 });
  });
});
