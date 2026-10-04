import type { ResourceItem } from './resource-selection-store';

/** PRD 119 D12 — rows per block for every other list and for the children of one tree node. */
export const PAGE_SIZE = 100;

/** PRD 127 D1 — the accordion's sections, at most one open. */
export type Section = 'favorites' | 'recents' | 'resources' | 'persons' | 'users';

/**
 * PRD 127 D1 — the section to open for a stored value: Favoriten/Zuletzt/Personen only while they have entries, Benutzer only where
 * the host shows it, an old type chip (PRD 123) as Ressourcen; 'none' = all closed; otherwise the start section, Favoriten
 * when there are any.
 */
export function openSection(
  chip: string,
  lists: { favorites: number; recents: number; persons: number; users: boolean },
): Section | null {
  if (chip === 'none') return null;
  if (chip === 'favorites' && lists.favorites) return 'favorites';
  if (chip === 'recents' && lists.recents) return 'recents';
  if (chip === 'users' && lists.users) return 'users';
  if (chip === 'persons' && lists.persons) return 'persons';
  if (chip === 'resources' || chip.startsWith('type:')) return 'resources';
  return lists.favorites ? 'favorites' : 'resources';
}

/** The lean list's resources or persons, in server order (PRD 127 D6); a row without a kind counts as a resource. */
export function ofKind(list: readonly ResourceItem[], kind: 'RESOURCE' | 'PERSON'): ResourceItem[] {
  return list.filter((it) => (it.classificationType === 'PERSON' ? 'PERSON' : 'RESOURCE') === kind);
}

const collator = new Intl.Collator('de');

export function byLabel(a: ResourceItem, b: ResourceItem): number {
  return collator.compare(a.label, b.label);
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
