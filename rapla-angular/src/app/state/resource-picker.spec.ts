import { describe, it, expect } from 'vitest';

import type { ResourceItem } from './resource-selection-store';
import { filterRows, ofKind, openSection, usersMatching } from './resource-picker';

const res = (
  id: string,
  label: string,
  typeKey = 'room',
  typeName = 'Raum',
  classificationType = 'RESOURCE',
): ResourceItem => ({
  id,
  label,
  kind: 'resource',
  typeKey,
  typeName,
  classificationType,
});

describe('resource picker (PRD 119 Phase 1)', () => {
  describe('PRD 127 D1 — the open section', () => {
    const lists = { favorites: 0, recents: 0, persons: 1, users: true };

    it('starts on Favoriten when there are any, else on Ressourcen', () => {
      expect(openSection('', lists)).toBe('resources');
      expect(openSection('', { ...lists, favorites: 2 })).toBe('favorites');
    });

    it('keeps a chosen section', () => {
      expect(openSection('persons', lists)).toBe('persons');
      expect(openSection('users', lists)).toBe('users');
      expect(openSection('recents', { ...lists, recents: 1 })).toBe('recents');
    });

    it('an empty Favoriten or Zuletzt is hidden, so the default opens instead', () => {
      expect(openSection('recents', lists)).toBe('resources');
      expect(openSection('favorites', { ...lists, recents: 3 })).toBe('resources');
    });

    it('an empty Personen is hidden like Favoriten and Zuletzt (user, 2026-10-04)', () => {
      expect(openSection('persons', { ...lists, persons: 0 })).toBe('resources');
    });

    it("'none' keeps every section closed (D1: at most one open)", () => {
      expect(openSection('none', { ...lists, favorites: 3 })).toBeNull();
    });

    it('Benutzer falls back to the default where the host has no Benutzer section', () => {
      expect(openSection('users', { ...lists, users: false })).toBe('resources');
    });

    it('maps the old saved chips: a type chip to Ressourcen, Alle and unknown values to the default', () => {
      expect(openSection('type:room', lists)).toBe('resources');
      expect(openSection('all', { ...lists, favorites: 1 })).toBe('favorites');
      expect(openSection('bogus', lists)).toBe('resources');
    });
  });

  it('splits the lean list into resources and persons in server order', () => {
    const list = [
      res('r2', 'Zelt'),
      res('p1', 'Prof. Lehmann', 'lecturer', 'Dozent', 'PERSON'),
      res('r1', 'Aula'),
      { id: 'x', label: 'ohne Art' },
    ];
    expect(ofKind(list, 'RESOURCE').map((x) => x.id)).toEqual(['r2', 'r1', 'x']);
    expect(ofKind(list, 'PERSON').map((x) => x.id)).toEqual(['p1']);
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
});
