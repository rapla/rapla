import { t as tr } from '../i18n/i18n.service';

/** PRD 122 D9 — a mixed type list grouped "Ressourcen" first, then "Personen"; null = flat (one kind). */
export function groupByKind<T extends { classificationType?: string }>(
  types: readonly T[],
): { label: string; types: T[] }[] | null {
  const groups = [
    { label: tr('resources'), types: types.filter((t) => t.classificationType === 'RESOURCE') },
    { label: tr('persons'), types: types.filter((t) => t.classificationType === 'PERSON') },
  ].filter((g) => g.types.length > 0);
  return groups.length > 1 ? groups : null;
}
