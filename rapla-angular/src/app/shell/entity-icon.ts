import type { SearchResultKind } from '../search/search.types';

/**
 * Material icon name for an entity, by kind + (hardcoded for now) rapla type key.
 * Type keys win over the kind so a {@code resource} renders as room / course /
 * study-program / building rather than a generic resource icon. The type-key
 * matches are substring + case-insensitive so {@code "Raum"} / {@code "raum"}
 * both hit. Hardcoded set per the dhbw deployment; a server-driven icon hint can
 * replace this later.
 */
export function entityIcon(kind: SearchResultKind, typeKey?: string | null): string {
  const t = (typeKey ?? '').toLowerCase();
  if (t.includes('raum')) return 'meeting_room';
  if (t.includes('kurs')) return 'class';
  if (t.includes('studiengang')) return 'school';
  if (t.includes('gebaeude') || t.includes('gebäude')) return 'apartment';
  if (t.includes('person')) return 'person';

  switch (kind) {
    case 'user':
      return 'account_circle'; // a login account — distinct from a person resource
    case 'event':
      return 'event';
    case 'group':
      return 'folder';
    case 'occurrence':
      return 'schedule';
    case 'savedView':
      return 'bookmark';
    default:
      return 'category'; // generic resource
  }
}
