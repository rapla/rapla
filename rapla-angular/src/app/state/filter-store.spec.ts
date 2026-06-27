import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { FilterStore, type FilterEntry } from './filter-store';
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
});
