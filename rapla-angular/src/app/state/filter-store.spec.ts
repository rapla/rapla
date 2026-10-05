import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { FilterStore, requestChipId, type FilterEntry } from './filter-store';
import { AuthService } from '../auth/auth.service';

const room = (id: string): FilterEntry => ({ id, kind: 'resource', label: id });
const event = (id: string): FilterEntry => ({ id, kind: 'event', label: id });

describe('FilterStore', () => {
  let store: FilterStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [FilterStore, AuthService] });
    store = TestBed.inject(FilterStore);
  });

  it('starts empty', () => {
    expect(store.entries()).toEqual([]);
    expect(store.isEmpty()).toBe(true);
    expect(store.count()).toBe(0);
  });

  it('replace() sets the filter to a single entry (stepping)', () => {
    store.add(room('A'));
    store.add(room('B'));
    store.replace(room('C'));
    expect(store.entries().map((e) => e.id)).toEqual(['C']);
  });

  it('add() accumulates entries (the + button)', () => {
    store.add(room('A'));
    store.add(event('E'));
    expect(store.entries().map((e) => e.id)).toEqual(['A', 'E']);
  });

  it('add() is idempotent by id', () => {
    store.add(room('A'));
    store.add(room('A'));
    expect(store.count()).toBe(1);
  });

  it('remove() drops one entry by id, leaving the rest', () => {
    store.add(room('A'));
    store.add(room('B'));
    store.remove('A');
    expect(store.entries().map((e) => e.id)).toEqual(['B']);
  });

  it('clear() empties the filter', () => {
    store.add(room('A'));
    store.clear();
    expect(store.isEmpty()).toBe(true);
  });

  it('has() reports membership', () => {
    store.add(room('A'));
    expect(store.has('A')).toBe(true);
    expect(store.has('B')).toBe(false);
  });

  it('persists the scope chips and restores them in a new store (survives reload)', () => {
    store.replace(room('A'));
    store.add(room('B'));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [FilterStore, AuthService] });
    const reloaded = TestBed.inject(FilterStore);
    expect(reloaded.entries()).toEqual([room('A'), room('B')]);
  });

  describe('PRD 127 D6/OQ6 — chips in tree order', () => {
    const user = (id: string): FilterEntry => ({ id, kind: 'user', label: id });
    const tree = new Map([
      ['r2', 'r2'],
      ['r1', 'r1'],
      ['p1', 'p1'],
      ['uA', 'uA'],
      ['uB', 'uB'],
    ]);

    it('a bulk selection lands in the order of the lean list, not in click order', () => {
      store.reconcile(tree);
      store.setAll([user('uB'), room('r1'), room('p1'), room('r2'), user('uA')]);
      expect(store.entries().map((e) => e.id)).toEqual(['r2', 'r1', 'p1', 'uA', 'uB']);
    });

    it('add() inserts at the tree position', () => {
      store.reconcile(tree);
      store.add(room('r1'));
      store.add(user('uA'));
      store.add(room('r2'));
      expect(store.entries().map((e) => e.id)).toEqual(['r2', 'r1', 'uA']);
    });

    it('restored chips are reordered once the list arrives; unknown ones keep their order at the end', () => {
      store.setAll([room('r1'), event('E'), room('r2')]);
      expect(store.entries().map((e) => e.id)).toEqual(['r1', 'E', 'r2']);
      store.reconcile(tree);
      expect(store.entries().map((e) => e.id)).toEqual(['r2', 'r1', 'E']);
    });
  });

  describe('selectGroup — "alle wählen" (PRD 123 D8, PRD 127 D8)', () => {
    it('replaces the selection, Ctrl adds, all selected → removes them', () => {
      store.setAll([room('X')]);
      store.selectGroup([room('A'), room('B')], false);
      expect(store.entries().map((e) => e.id)).toEqual(['A', 'B']);
      store.selectGroup([room('C')], true);
      expect(store.entries().map((e) => e.id)).toEqual(['A', 'B', 'C']);
      store.selectGroup([room('A'), room('C')], false);
      expect(store.entries().map((e) => e.id)).toEqual(['B']);
    });

    it('10 000 entries select and clear in well under 200 ms (no O(n²))', () => {
      const many = Array.from({ length: 10000 }, (_, i) => room(`r${i}`));
      store.selectGroup(many, false);
      const start = performance.now();
      store.selectGroup(many, false);
      const ms = performance.now() - start;
      expect(store.isEmpty()).toBe(true);
      expect(ms).toBeLessThan(200);
    });
  });
  describe('PRD 128 D3/OQ4 — Planen / Konflikte / Ressourcenanfragen contexts', () => {
    const conflict = (id: string): FilterEntry => ({ id, kind: 'conflict', label: id });
    const request = (id: string): FilterEntry => ({ id, kind: 'request', label: id });
    const reload = () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [FilterStore, AuthService] });
      return TestBed.inject(FilterStore);
    };

    it('starts in Planen; each context keeps its own selection and restores it in every direction', () => {
      expect(store.context()).toBe('plan');
      store.setAll([room('r1'), room('r2')]);
      store.setContext('conflicts');
      expect(store.entries()).toEqual([]);
      store.replace(conflict('CONFLICT;r1;a1;a2'));
      store.setContext('plan');
      expect(store.entries().map((e) => e.id)).toEqual(['r1', 'r2']);
      store.setContext('requests');
      store.add(request('REQUEST;r3;v1'));
      store.setContext('conflicts');
      expect(store.entries().map((e) => e.id)).toEqual(['CONFLICT;r1;a1;a2']);
      store.setContext('requests');
      expect(store.entries().map((e) => e.id)).toEqual(['REQUEST;r3;v1']);
      store.setContext('plan');
      expect(store.entries().map((e) => e.id)).toEqual(['r1', 'r2']);
    });

    it('all three selections persist per user; a reload always starts in Planen', () => {
      store.replace(room('r1'));
      store.setContext('conflicts');
      store.replace(conflict('CONFLICT;r1;a1;a2'));
      store.setContext('requests');
      store.replace(request('REQUEST;r3;v1'));
      const reloaded = reload();
      expect(reloaded.context()).toBe('plan');
      expect(reloaded.entries()).toEqual([room('r1')]);
      reloaded.setContext('conflicts');
      expect(reloaded.entries()).toEqual([conflict('CONFLICT;r1;a1;a2')]);
      reloaded.setContext('requests');
      expect(reloaded.entries()).toEqual([request('REQUEST;r3;v1')]);
    });

    it('restore with stale ids of every kind drops them silently and takes the current label', () => {
      const user = (id: string): FilterEntry => ({ id, kind: 'user', label: id });
      store.setAll([room('r1'), room('gone'), user('u1'), user('u-gone')]);
      store.setContext('conflicts');
      store.setAll([conflict('CONFLICT;r1;a1;a2'), conflict('CONFLICT;r1;a3;a4')]);
      store.setContext('requests');
      store.setAll([request('REQUEST;r3;v1'), request('REQUEST;r3;v2')]);

      store.reconcile(new Map([['CONFLICT;r1;a1;a2', 'Raum 1 · 05.10.']]), 'conflicts');
      store.reconcile(
        new Map([['REQUEST;r3;v1', { label: 'Beamer · Vorlesung', resourceName: 'Beamer' }]]),
        'requests',
      );
      expect(store.entries()).toEqual([
        {
          id: 'REQUEST;r3;v1',
          kind: 'request',
          label: 'Beamer · Vorlesung',
          resourceName: 'Beamer',
        },
      ]);
      store.reconcile(
        new Map([
          ['r1', 'Raum 1'],
          ['u1', 'Ute'],
        ]),
      );
      store.setContext('conflicts');
      expect(store.entries()).toEqual([
        { id: 'CONFLICT;r1;a1;a2', kind: 'conflict', label: 'Raum 1 · 05.10.' },
      ]);
      store.setContext('plan');
      expect(store.entries()).toEqual([
        { id: 'r1', kind: 'resource', label: 'Raum 1' },
        { id: 'u1', kind: 'user', label: 'Ute' },
      ]);
    });

    it('requestChipId composes resource and reservation id', () => {
      expect(requestChipId('r3', 'v1')).toBe('REQUEST;r3;v1');
    });
  });
});
