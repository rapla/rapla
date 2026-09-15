import { describe, it, expect } from 'vitest';

import type { ResourceItem } from './resource-selection-store';
import { PAGE_SIZE, filterRows, page, rankAll, typeChips, usersMatching } from './resource-picker';

const res = (id: string, label: string, typeKey = 'room', typeName = 'Raum'): ResourceItem => ({
  id,
  label,
  kind: 'resource',
  typeKey,
  typeName,
});

describe('resource picker (PRD 119 Phase 1)', () => {
  it('derives one chip per type present in the list, A–Z by type name', () => {
    const list = [
      res('r1', 'Hörsaal 1', 'room', 'Raum'),
      res('p1', 'Prof. Lehmann', 'lecturer', 'Dozent'),
      res('r2', 'Hörsaal 2', 'room', 'Raum'),
      res('b1', 'Beamer 1', 'equipment', 'Ausleihgerät'),
    ];
    expect(typeChips(list)).toEqual([
      { key: 'type:equipment', label: 'Ausleihgerät' },
      { key: 'type:lecturer', label: 'Dozent' },
      { key: 'type:room', label: 'Raum' },
    ]);
  });

  it('ranks Alle as favorites, then recents, then the rest A–Z without duplicates', () => {
    const list = [res('a', 'Zeta'), res('b', 'Alpha'), res('c', 'Ärger'), res('d', 'Beta')];
    const favorites = [res('d', 'Beta')];
    const recents = [res('a', 'Zeta'), res('d', 'Beta')];
    expect(rankAll(list, favorites, recents).map((x) => x.id)).toEqual(['d', 'a', 'b', 'c']);
  });

  it('sorts the A–Z part with German collation (umlauts next to their base letter)', () => {
    const list = [res('1', 'Zelt'), res('2', 'Öfen'), res('3', 'Ofen'), res('4', 'Apfel')];
    expect(rankAll(list, [], []).map((x) => x.label)).toEqual(['Apfel', 'Ofen', 'Öfen', 'Zelt']);
  });

  it('filters case-insensitively on the label with no minimum length', () => {
    const rows = [res('1', 'C348 PC-Hörsaal'), res('2', 'A474 Hörsaal'), res('3', 'Labor')];
    expect(filterRows(rows, 'h').map((x) => x.id)).toEqual(['1', '2']);
    expect(filterRows(rows, 'HÖRSAAL').map((x) => x.id)).toEqual(['1', '2']);
    expect(filterRows(rows, '  ').map((x) => x.id)).toEqual(['1', '2', '3']);
  });

  it('matches a user row by username too (parity with the user search)', () => {
    const users: ResourceItem[] = [
      { id: 'u1', label: 'C. Montgomery Burns', kind: 'user', username: 'monty' },
      { id: 'u2', label: 'Simpson Homer', kind: 'user', username: 'homer' },
    ];
    expect(filterRows(users, 'MONTY').map((x) => x.id)).toEqual(['u1']);
    expect(usersMatching(users, 'hom').map((x) => x.id)).toEqual(['u2']);
  });

  it('offers users only while typing', () => {
    const users: ResourceItem[] = [
      { id: 'u1', label: 'Burns Monty', kind: 'user' },
      { id: 'u2', label: 'Simpson Homer', kind: 'user' },
    ];
    expect(usersMatching(users, '')).toEqual([]);
    expect(usersMatching(users, 'mon').map((x) => x.id)).toEqual(['u1']);
  });

  it('shows the first page and counts the rest; expanded shows all', () => {
    const rows = Array.from({ length: 27 }, (_, i) => res(`r${i}`, `Raum ${i}`));
    expect(PAGE_SIZE).toBe(20);
    const collapsed = page(rows, false);
    expect(collapsed.shown.length).toBe(20);
    expect(collapsed.hidden).toBe(7);
    const expanded = page(rows, true);
    expect(expanded.shown.length).toBe(27);
    expect(expanded.hidden).toBe(0);
  });
});
