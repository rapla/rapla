import type { ResourceItem } from '../state/resource-selection-store';
import { childIndex } from '../state/resource-tree';

/** dhbwrapla PRD 004 § H5a — the sync dialog is offered for fewer than this many effective Kurse. */
export const MAX_SYNC_COURSES = 50;

/** The Kurse behind the resource chips: a chip with children (a course group, PRD 120
 *  parentIds) stands for all its leaf descendants, a chip without children for itself.
 *  Deduplicated, cycle-safe. */
export function effectiveCourseIds(
  chipIds: readonly string[],
  all: readonly ResourceItem[],
): string[] {
  const childrenOf = childIndex(all);
  const result = new Set<string>();
  const visited = new Set<string>();
  const walk = (id: string) => {
    if (visited.has(id)) return;
    visited.add(id);
    const children = childrenOf.get(id) ?? [];
    if (children.length === 0) result.add(id);
    for (const child of children) walk(child.id);
  };
  chipIds.forEach(walk);
  return [...result];
}

export type SyncScope =
  | { kind: 'none' }
  | { kind: 'tooMany'; count: number }
  | { kind: 'ready'; ids: string[] };

export function syncScope(ids: readonly string[]): SyncScope {
  if (ids.length === 0) return { kind: 'none' };
  if (ids.length >= MAX_SYNC_COURSES) return { kind: 'tooMany', count: ids.length };
  return { kind: 'ready', ids: [...ids] };
}
