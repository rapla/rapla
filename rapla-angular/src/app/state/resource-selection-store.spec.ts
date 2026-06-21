import { describe, it, expect, beforeEach } from 'vitest';
import { ResourceSelectionStore, type ResourceItem } from './resource-selection-store';

const item = (id: string): ResourceItem => ({ id, label: id });

describe('ResourceSelectionStore', () => {
  let store: ResourceSelectionStore;

  beforeEach(() => {
    localStorage.clear(); // recents/favorites persist — isolate each test
    store = new ResourceSelectionStore();
  });

  it('starts on the recents tab with empty lists', () => {
    expect(store.activeTab()).toBe('recents');
    expect(store.recents()).toEqual([]);
    expect(store.favorites()).toEqual([]);
    expect(store.group()).toEqual([]);
    expect(store.activeList()).toEqual([]);
  });

  it('pushRecent() prepends most-recent-first', () => {
    store.pushRecent(item('A'));
    store.pushRecent(item('B'));
    expect(store.recents().map((x) => x.id)).toEqual(['B', 'A']);
  });

  it('pushRecent() keeps an existing item in place (no reshuffle on re-add)', () => {
    store.pushRecent(item('A'));
    store.pushRecent(item('B'));
    store.pushRecent(item('A')); // already present → order unchanged
    expect(store.recents().map((x) => x.id)).toEqual(['B', 'A']);
  });

  it('pushRecent() caps the list length', () => {
    for (let i = 0; i < 30; i++) store.pushRecent(item(`R${i}`));
    expect(store.recents().length).toBeLessThanOrEqual(20);
    expect(store.recents()[0].id).toBe('R29');
  });

  it('persists recents + favorites across instances (localStorage)', () => {
    store.pushRecent(item('A'));
    store.toggleFavorite(item('F'));
    const reloaded = new ResourceSelectionStore();
    expect(reloaded.recents().map((x) => x.id)).toEqual(['A']);
    expect(reloaded.isFavorite('F')).toBe(true);
  });

  it('toggleFavorite() adds then removes', () => {
    store.toggleFavorite(item('A'));
    expect(store.isFavorite('A')).toBe(true);
    store.toggleFavorite(item('A'));
    expect(store.isFavorite('A')).toBe(false);
  });

  it('loadGroup() fills the group, labels it, and switches to the group tab', () => {
    store.loadGroup('Räume C-Bau', [item('C348'), item('C452')]);
    expect(store.group().map((x) => x.id)).toEqual(['C348', 'C452']);
    expect(store.groupLabel()).toBe('Räume C-Bau');
    expect(store.activeTab()).toBe('group');
    expect(store.activeList().map((x) => x.id)).toEqual(['C348', 'C452']);
  });

  it('clearGroup() empties the group and its label', () => {
    store.loadGroup('G', [item('X')]);
    store.clearGroup();
    expect(store.group()).toEqual([]);
    expect(store.groupLabel()).toBeNull();
  });

  it('activeList() follows the active tab', () => {
    store.pushRecent(item('R'));
    store.toggleFavorite(item('F'));
    store.loadGroup('G', [item('GG')]);
    store.setActiveTab('recents');
    expect(store.activeList().map((x) => x.id)).toEqual(['R']);
    store.setActiveTab('favorites');
    expect(store.activeList().map((x) => x.id)).toEqual(['F']);
    store.setActiveTab('group');
    expect(store.activeList().map((x) => x.id)).toEqual(['GG']);
  });

  it('setActive() tracks the currently shown item', () => {
    expect(store.activeId()).toBeNull();
    store.setActive('C348');
    expect(store.activeId()).toBe('C348');
    store.setActive(null);
    expect(store.activeId()).toBeNull();
  });
});
