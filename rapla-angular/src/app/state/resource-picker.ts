import type { ResourceItem } from './resource-selection-store';

/** PRD 119 D1/D12 — rows Alle shows without a query before "Weitere n anzeigen". */
export const FIRST_PAGE = 20;
/** PRD 119 D12 — rows per block for every other list and for the children of one tree node. */
export const PAGE_SIZE = 100;

export interface PickerChip {
  key: string;
  label: string;
}

const collator = new Intl.Collator('de');

export function byLabel(a: ResourceItem, b: ResourceItem): number {
  return collator.compare(a.label, b.label);
}

/** One chip per resource type present in the list, A–Z by type name. */
export function typeChips(list: readonly ResourceItem[]): PickerChip[] {
  const names = new Map<string, string>();
  for (const it of list) {
    if (it.typeKey && !names.has(it.typeKey)) names.set(it.typeKey, it.typeName ?? it.typeKey);
  }
  return [...names.entries()]
    .map(([key, label]) => ({ key: `type:${key}`, label }))
    .sort((a, b) => collator.compare(a.label, b.label));
}

/** PRD 119 D1 — Alle: favorites, then recents, then every other resource A–Z; each id once. */
export function rankAll(
  list: readonly ResourceItem[],
  favorites: readonly ResourceItem[],
  recents: readonly ResourceItem[],
): ResourceItem[] {
  const seen = new Set<string>();
  const out: ResourceItem[] = [];
  const take = (it: ResourceItem) => {
    if (seen.has(it.id)) return;
    seen.add(it.id);
    out.push(it);
  };
  favorites.forEach(take);
  recents.forEach(take);
  [...list].sort(byLabel).forEach(take);
  return out;
}

/** Case-insensitive substring match on the label; a blank query keeps every row. */
export function filterRows(rows: readonly ResourceItem[], query: string): ResourceItem[] {
  const q = query.trim().toLocaleLowerCase('de');
  if (!q) return [...rows];
  return rows.filter(
    (it) =>
      it.label.toLocaleLowerCase('de').includes(q) ||
      (it.username?.toLocaleLowerCase('de').includes(q) ?? false),
  );
}

/** PRD 119 D10 — users only appear as hits while typing. */
export function usersMatching(users: readonly ResourceItem[], query: string): ResourceItem[] {
  return query.trim() ? filterRows(users, query) : [];
}

export function page(
  rows: readonly ResourceItem[],
  limit: number,
): { shown: ResourceItem[]; hidden: number } {
  return { shown: rows.slice(0, limit), hidden: Math.max(0, rows.length - limit) };
}
