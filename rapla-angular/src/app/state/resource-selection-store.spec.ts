import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ResourceSelectionStore, type ResourceItem } from './resource-selection-store';
import { RecentsFavoritesService } from './recents-favorites.service';
import { AuthService } from '../auth/auth.service';

const item = (id: string): ResourceItem => ({ id, label: id });

const wire = (id: string, name: string, typeKey: string, typeName: string) => ({
  id,
  kind: 'RESOURCE',
  name,
  classification: { typeKey, type: { name: typeName } },
});

/**
 * PRD 089: recents + favorites are server-backed (RecentsFavoritesService); the store re-exposes
 * them. PRD 119 Phase 1: the store loads the lean resource list once and offers chips (Alle,
 * Favoriten, Zuletzt, a transient Gruppe while a group is loaded, one chip per type).
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

  function flushList(rows: ReturnType<typeof wire>[]): void {
    const req = http.expectOne('/api/graphql');
    expect(req.request.body.query).toContain('resources');
    req.flush({ data: { resources: rows } });
  }

  it('starts on Alle with empty lists and loads nothing by itself', () => {
    expect(store.activeChip()).toBe('all');
    expect(store.recents()).toEqual([]);
    expect(store.favorites()).toEqual([]);
    expect(store.activeList()).toEqual([]);
  });

  it('ensureLoaded() fetches the lean list once and maps it to items', () => {
    store.ensureLoaded();
    store.ensureLoaded();
    flushList([
      wire('r1', 'Hörsaal 1', 'room', 'Raum'),
      wire('p1', 'Prof. Lehmann', 'lecturer', 'Dozent'),
    ]);
    expect(store.resources()).toEqual([
      {
        id: 'r1',
        label: 'Hörsaal 1',
        kind: 'resource',
        typeKey: 'room',
        typeName: 'Raum',
        groupPaths: [],
      },
      {
        id: 'p1',
        label: 'Prof. Lehmann',
        kind: 'resource',
        typeKey: 'lecturer',
        typeName: 'Dozent',
        groupPaths: [],
      },
    ]);
  });

  it('reload() fetches the list again', () => {
    store.ensureLoaded();
    flushList([wire('r1', 'Hörsaal 1', 'room', 'Raum')]);
    store.reload();
    flushList([wire('r1', 'Hörsaal 1', 'room', 'Raum'), wire('r2', 'Hörsaal 2', 'room', 'Raum')]);
    expect(store.resources().map((x) => x.id)).toEqual(['r1', 'r2']);
  });

  it('offers Alle, Favoriten, Zuletzt and one chip per type', () => {
    store.ensureLoaded();
    flushList([
      wire('p1', 'Prof. Lehmann', 'lecturer', 'Dozent'),
      wire('r1', 'Hörsaal 1', 'room', 'Raum'),
    ]);
    expect(store.chips().map((c) => c.key)).toEqual([
      'all',
      'favorites',
      'recents',
      'type:lecturer',
      'type:room',
    ]);
  });

  it('Alle lists the whole list A–Z; a type chip lists only that type', () => {
    store.ensureLoaded();
    flushList([
      wire('r2', 'Hörsaal 2', 'room', 'Raum'),
      wire('p1', 'Prof. Lehmann', 'lecturer', 'Dozent'),
      wire('r1', 'Hörsaal 1', 'room', 'Raum'),
    ]);
    expect(store.activeList().map((x) => x.id)).toEqual(['r1', 'r2', 'p1']);
    store.setActiveChip('type:room');
    expect(store.activeList().map((x) => x.id)).toEqual(['r1', 'r2']);
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

  /** PRD 119 D3 — the one search field and the picker share this query. */
  it('holds the shared query', () => {
    expect(store.query()).toBe('');
    store.setQuery('Hör');
    expect(store.query()).toBe('Hör');
  });

  it('skips a resource without a name (no empty row)', () => {
    store.ensureLoaded();
    flushList([
      wire('r1', 'Hörsaal 1', 'room', 'Raum'),
      { ...wire('r2', '', 'room', 'Raum'), name: null as unknown as string },
    ]);
    expect(store.resources().map((x) => x.id)).toEqual(['r1']);
  });

  it('a failed load does not stick: the next ensureLoaded() asks again', () => {
    store.ensureLoaded();
    http.expectOne('/api/graphql').flush('boom', { status: 500, statusText: 'Server Error' });
    store.ensureLoaded();
    flushList([wire('r1', 'Hörsaal 1', 'room', 'Raum')]);
    expect(store.resources().map((x) => x.id)).toEqual(['r1']);
  });

  it('offers no group chip (groups come with the tree, PRD 119 D2/D6)', () => {
    expect(store.chips().map((c) => c.key)).toEqual(['all', 'favorites', 'recents']);
  });

  it('activeList() follows the Favoriten and Zuletzt chips', async () => {
    store.pushRecent(item('R'));
    http
      .expectOne('/api/recents')
      .flush([{ id: 'R', kind: 'resource', label: 'R', color: null, typeKey: null }]);
    store.toggleFavorite(item('F'));
    http
      .expectOne('/api/favorites')
      .flush([{ id: 'F', kind: 'resource', label: 'F', color: null, typeKey: null }]);
    await Promise.resolve();

    store.setActiveChip('recents');
    expect(store.activeList().map((x) => x.id)).toEqual(['R']);
    store.setActiveChip('favorites');
    expect(store.activeList().map((x) => x.id)).toEqual(['F']);
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
