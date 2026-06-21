import type { FilterEntry } from '../state/filter-store';

/** The ReservationFilter variable the view query takes (the subset the SPA sets). */
export interface ReservationFilterVars {
  from: string;
  to: string;
  allocatableIdsIn?: string[];
}

/**
 * Serialize the active filter (chip rail) + date window into the {@code $filter}
 * variable. Resource chips become {@code allocatableIdsIn} — "reservations using
 * ANY of these allocatable ids" (= their occupancy), which matches the
 * accumulate-with-`+` / step-with-replace model.
 *
 * Event chips are NOT serialized: {@code ReservationFilter} has no reservation-id
 * filter, and an event's primary action is navigate (to its first occurrence),
 * not filter. Adding one is a no-op on the query until a server-side
 * reservation-id filter exists.
 */
export function filterToReservationFilter(
  entries: FilterEntry[],
  window: { from: string; to: string },
): ReservationFilterVars {
  const vars: ReservationFilterVars = { from: window.from, to: window.to };
  const resourceIds = entries.filter((e) => e.kind === 'resource').map((e) => e.id);
  if (resourceIds.length) vars.allocatableIdsIn = resourceIds;
  return vars;
}
