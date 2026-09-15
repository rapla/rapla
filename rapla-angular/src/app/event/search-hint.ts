import type { FilterEntry } from '../state/filter-store';
import type { DraftAllocation } from './event-draft';

/**
 * PRD 119 D4 — an event opened from the search whose resources are all outside the current
 * selection gets a hint: the week view would not show it.
 */
export function needsSearchHint(
  allocations: readonly DraftAllocation[],
  isSelected: (id: string) => boolean,
): boolean {
  return allocations.length > 0 && !allocations.some((a) => isSelected(a.resourceId));
}

/** "Ressourcen des Termins auswählen" — the event's resources as the new filter. */
export function allocationEntries(allocations: readonly DraftAllocation[]): FilterEntry[] {
  return allocations.map((a) => ({ id: a.resourceId, kind: 'resource', label: a.resourceName }));
}
