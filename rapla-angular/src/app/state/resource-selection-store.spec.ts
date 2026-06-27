import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ResourceSelectionStore, type ResourceItem } from './resource-selection-store';
import { RecentsFavoritesService } from './recents-favorites.service';
import { AuthService, type Identity } from '../auth/auth.service';

const item = (id: string): ResourceItem => ({ id, label: id });

function identity(userId: string): Identity {
  return {
    userId,
    username: userId,
    name: userId,
    admin: false,
    roles: [],
    impersonating: false,
    actor: null,
    target: null,
  };
}

/**
 * PRD 089: recents + favorites are now server-backed (RecentsFavoritesService);
 * the store re-exposes them. group/tab/active state stays in-memory. These tests
 * cover the in-memory portion + delegation; the HTTP write paths are exercised in
 * recents-favorites.service.spec.ts.
 */
describe('ResourceSelectionStore', () => {
  let store: ResourceSelectionStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ResourceSelectionStore,
        RecentsFavoritesService,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    store = TestBed.inject(ResourceSelectionStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('starts on the recents tab with empty lists', () => {
    expect(store.activeTab()).toBe('recents');
    expect(store.recents()).toEqual([]);
    expect(store.favorites()).toEqual([]);
    expect(store.group()).toEqual([]);
    expect(store.activeList()).toEqual([]);
  });

  it('pushRecent() optimistically prepends and reconciles with the server', async () => {
    store.pushRecent(item('A'));
    http
      .expectOne('/api/recents')
      .flush([{ id: 'A', kind: 'resource', label: 'A', color: null, typeKey: null }]);
    await Promise.resolve();
    expect(store.recents().map((x) => x.id)).toEqual(['A']);
  });

  it('toggleFavorite() pins then unpins through the service', async () => {
    store.toggleFavorite(item('F'));
    http
      .expectOne('/api/favorites')
      .flush([{ id: 'F', kind: 'resource', label: 'F', color: null, typeKey: null }]);
    await Promise.resolve();
    expect(store.isFavorite('F')).toBe(true);

    store.toggleFavorite(item('F'));
    http.expectOne('/api/favorites/F').flush([]);
    await Promise.resolve();
    expect(store.isFavorite('F')).toBe(false);
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

  it('activeList() follows the active tab', async () => {
    store.pushRecent(item('R'));
    http
      .expectOne('/api/recents')
      .flush([{ id: 'R', kind: 'resource', label: 'R', color: null, typeKey: null }]);
    store.toggleFavorite(item('F'));
    http
      .expectOne('/api/favorites')
      .flush([{ id: 'F', kind: 'resource', label: 'F', color: null, typeKey: null }]);
    await Promise.resolve();
    store.loadGroup('G', [item('GG')]);

    store.setActiveTab('recents');
    expect(store.activeList().map((x) => x.id)).toEqual(['R']);
    store.setActiveTab('favorites');
    expect(store.activeList().map((x) => x.id)).toEqual(['F']);
    store.setActiveTab('group');
    expect(store.activeList().map((x) => x.id)).toEqual(['GG']);
  });

  it('clearRecents() DELETEs and empties the list', async () => {
    store.pushRecent(item('A'));
    http
      .expectOne('/api/recents')
      .flush([{ id: 'A', kind: 'resource', label: 'A', color: null, typeKey: null }]);
    await Promise.resolve();
    store.clearRecents();
    http.expectOne('/api/recents').flush([]);
    await Promise.resolve();
    expect(store.recents()).toEqual([]);
  });

  it('setActive() tracks the currently shown item', () => {
    expect(store.activeId()).toBeNull();
    store.setActive('C348');
    expect(store.activeId()).toBe('C348');
    store.setActive(null);
    expect(store.activeId()).toBeNull();
  });
});
