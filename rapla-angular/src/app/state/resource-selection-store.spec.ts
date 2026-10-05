import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ResourceSelectionStore, type ResourceItem } from './resource-selection-store';
import { RecentsFavoritesService } from './recents-favorites.service';
import { AuthService } from '../auth/auth.service';
import { FilterStore } from './filter-store';

const item = (id: string): ResourceItem => ({ id, label: id });

const wire = (id: string, name: string, typeKey: string, typeName: string, kind = 'RESOURCE') => ({
  id,
  kind,
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
    localStorage.clear();
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

  function flushList(rows: (ReturnType<typeof wire> & { parents?: { id: string }[] })[]): void {
    const req = http.expectOne('/api/graphql');
    expect(req.request.body.query).toContain('resources');
    req.flush({ data: { resources: rows } });
  }

  it('starts on Ressourcen with empty lists and loads nothing by itself (PRD 127 D1)', () => {
    expect(store.activeChip()).toBe('resources');
    expect(store.recents()).toEqual([]);
    expect(store.favorites()).toEqual([]);
    expect(store.listFor('resources')).toEqual([]);
  });

  it('a restored filter drops chips that no longer resolve and relabels the rest from the current list (user ruling 2026-10-04)', () => {
    const filter = TestBed.inject(FilterStore);
    filter.setAll([
      { id: 'r1', kind: 'resource', label: 'Old name', color: '#f00' },
      { id: 'gone', kind: 'resource', label: 'Deleted room' },
      { id: 'u1', kind: 'user', label: 'Old user' },
      { id: 'u-gone', kind: 'user', label: 'Hidden user' },
      { id: 'e1', kind: 'event', label: 'Some event' },
    ]);
    store.ensureLoaded();
    const req = http.expectOne('/api/graphql');
    req.flush({
      data: {
        resources: [wire('r1', 'New name', 'room', 'Raum')],
        users: [{ id: 'u1', username: 'u1', name: 'New user' }],
      },
    });
    expect(filter.entries()).toEqual([
      { id: 'r1', kind: 'resource', label: 'New name', color: '#f00' },
      { id: 'u1', kind: 'user', label: 'New user' },
      { id: 'e1', kind: 'event', label: 'Some event' },
    ]);
  });

  it('ensureLoaded() fetches the lean list once and maps it to items', () => {
    store.ensureLoaded();
    store.ensureLoaded();
    flushList([
      { ...wire('r1', 'Hörsaal 1', 'room', 'Raum'), parents: [{ id: 'b1' }] },
      wire('p1', 'Prof. Lehmann', 'lecturer', 'Dozent'),
    ]);
    expect(store.resources()).toEqual([
      {
        id: 'r1',
        label: 'Hörsaal 1',
        kind: 'resource',
        typeKey: 'room',
        typeName: 'Raum',
        classificationType: 'RESOURCE',
        groupPaths: [],
        parentIds: ['b1'],
      },
      {
        id: 'p1',
        label: 'Prof. Lehmann',
        kind: 'resource',
        typeKey: 'lecturer',
        typeName: 'Dozent',
        classificationType: 'RESOURCE',
        groupPaths: [],
        parentIds: [],
      },
    ]);
  });

  it('shows no Benutzer chip before the lean list is loaded (no "meine" flash)', () => {
    TestBed.inject(AuthService).identity.set({
      userId: 'U-ME',
      username: 'me',
      name: 'me',
      admin: false,
      roles: [],
      impersonating: false,
      actor: null,
      target: null,
    });
    expect(store.usersChip()).toBeNull();
  });

  it('an identity switch drops the lean list and reloads it for the new user (R-22)', () => {
    const who = (userId: string, admin: boolean) => ({
      userId,
      username: userId,
      name: userId,
      admin,
      roles: [],
      impersonating: false,
      actor: null,
      target: null,
    });
    const answer = (rows: ReturnType<typeof wire>[], users: { id: string }[]) => {
      for (const req of http.match(() => true)) {
        const lean = String(req.request.body?.query ?? '').includes('resources {');
        req.flush(
          lean
            ? {
                data: {
                  resources: rows,
                  users: users.map((u) => ({ ...u, username: u.id, name: u.id })),
                },
              }
            : {},
        );
      }
    };
    const auth = TestBed.inject(AuthService);
    auth.identity.set(who('U-ADMIN', true));
    TestBed.tick();
    store.ensureLoaded();
    answer([wire('r1', 'Hörsaal 1', 'room', 'Raum')], [{ id: 'U-ADMIN' }, { id: 'U-WOLF' }]);
    expect(store.usersChip()).toEqual({ mine: false });

    auth.identity.set(who('U-WOLF', false));
    TestBed.tick();
    expect(store.resources()).toEqual([]);
    expect(store.users()).toEqual([]);
    answer([wire('r9', 'Labor', 'room', 'Raum')], [{ id: 'U-WOLF' }]);
    expect(store.resources().map((r) => r.id)).toEqual(['r9']);
    expect(store.users().map((u) => u.id)).toEqual(['U-WOLF']);
    expect(store.usersChip()).toEqual({ mine: true });
  });

  it('reload() fetches the list again', () => {
    store.ensureLoaded();
    flushList([wire('r1', 'Hörsaal 1', 'room', 'Raum')]);
    store.reload();
    flushList([wire('r1', 'Hörsaal 1', 'room', 'Raum'), wire('r2', 'Hörsaal 2', 'room', 'Raum')]);
    expect(store.resources().map((x) => x.id)).toEqual(['r1', 'r2']);
  });

  it('Ressourcen and Personen list their kind in server order (PRD 127 D6)', () => {
    store.ensureLoaded();
    flushList([
      wire('r2', 'Hörsaal 2', 'room', 'Raum'),
      wire('p1', 'Prof. Lehmann', 'lecturer', 'Dozent', 'PERSON'),
      wire('r1', 'Hörsaal 1', 'room', 'Raum'),
    ]);
    expect(store.listFor('resources').map((x) => x.id)).toEqual(['r2', 'r1']);
    expect(store.listFor('persons').map((x) => x.id)).toEqual(['p1']);
  });

  it('counts the search hits over resources and users (omnibox count row)', () => {
    store.ensureLoaded();
    http.expectOne('/api/graphql').flush({
      data: {
        resources: [wire('r1', 'Hörsaal 1', 'room', 'Raum'), wire('r2', 'Labor', 'room', 'Raum')],
        users: [{ id: 'u1', username: 'hoermann', name: 'Hörmann' }],
      },
    });
    expect(store.matchCountFor('hör')).toBe(2);
  });

  it('hands the filter the tree order: resources in server order, then users A–Z (PRD 127 OQ6)', () => {
    const filter = TestBed.inject(FilterStore);
    store.ensureLoaded();
    http.expectOne('/api/graphql').flush({
      data: {
        resources: [wire('r2', 'Zelt', 'room', 'Raum'), wire('r1', 'Aula', 'room', 'Raum')],
        users: [
          { id: 'uZ', username: 'zed', name: 'Zed' },
          { id: 'uA', username: 'abe', name: 'Abe' },
        ],
      },
    });
    filter.setAll([
      { id: 'uZ', kind: 'user', label: 'Zed' },
      { id: 'r1', kind: 'resource', label: 'Aula' },
      { id: 'uA', kind: 'user', label: 'Abe' },
      { id: 'r2', kind: 'resource', label: 'Zelt' },
    ]);
    expect(filter.entries().map((e) => e.id)).toEqual(['r2', 'r1', 'uA', 'uZ']);
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

  it('PRD 119 D13 — counts the hits for another term with the picker matcher, without touching the query', () => {
    store.ensureLoaded();
    flushList([
      wire('r1', 'Hörsaal 1', 'room', 'Raum'),
      wire('r2', 'Hörsaal 2', 'room', 'Raum'),
      wire('r3', 'Labor', 'room', 'Raum'),
    ]);
    store.setQuery('Lab');
    expect(store.matchCountFor('Hör')).toBe(2);
    expect(store.query()).toBe('Lab');
  });

  /** PRD 119 D13 — the picker field's query, persisted (PRD 123 D5). */
  it('holds the picker query', () => {
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

  it('Favoriten and Zuletzt open once they have entries', async () => {
    store.pushRecent(item('R'));
    http
      .expectOne('/api/recents')
      .flush([{ id: 'R', kind: 'resource', label: 'R', color: null, typeKey: null }]);
    store.toggleFavorite(item('F'));
    http
      .expectOne('/api/favorites')
      .flush([{ id: 'F', kind: 'resource', label: 'F', color: null, typeKey: null }]);
    await Promise.resolve();

    expect(store.activeChip()).toBe('favorites');
    store.setActiveChip('recents');
    expect(store.activeChip()).toBe('recents');
    expect(store.listFor('recents').map((x) => x.id)).toEqual(['R']);
    expect(store.listFor('favorites').map((x) => x.id)).toEqual(['F']);
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
  // PRD 123 D5 / PRD 127 D1 — open section, query and active row survive a reload (per user)
  it('persists the open section, query and active id and restores them in a fresh store', () => {
    store.ensureLoaded();
    flushList([wire('r1', 'Hörsaal 1', 'room', 'Raum')]);
    store.setActiveChip('persons');
    store.setQuery('Hör');
    store.setActive('r1');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ResourceSelectionStore,
        RecentsFavoritesService,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    const fresh = TestBed.inject(ResourceSelectionStore);
    http = TestBed.inject(HttpTestingController);
    fresh.ensureLoaded();
    http.expectOne('/api/graphql').flush({
      data: { resources: [wire('p1', 'Prof. Lehmann', 'lecturer', 'Dozent', 'PERSON')] },
    });
    expect(fresh.activeChip()).toBe('persons');
    expect(fresh.query()).toBe('Hör');
    expect(fresh.activeId()).toBe('r1');
  });

  it('PRD 128 D7 — opening a section leaves the chip context alone (the view switches it)', () => {
    const filter = TestBed.inject(FilterStore);
    filter.setContext('conflicts');
    store.setActiveChip('recents');
    expect(filter.context()).toBe('conflicts');
  });

  it('maps an old saved type chip to Ressourcen (PRD 127 D1)', () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    const key = 'rapla.picker::u=anon';
    localStorage.setItem(key, JSON.stringify({ chip: 'type:room', query: '', activeId: null }));
    TestBed.configureTestingModule({
      providers: [
        ResourceSelectionStore,
        RecentsFavoritesService,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    const fresh = TestBed.inject(ResourceSelectionStore);
    http = TestBed.inject(HttpTestingController);
    expect(fresh.activeChip()).toBe('resources');
  });

  it('a closed accordion survives a reload (PRD 127 D1)', () => {
    store.setActiveChip('none');
    expect(store.activeChip()).toBeNull();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ResourceSelectionStore,
        RecentsFavoritesService,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    const fresh = TestBed.inject(ResourceSelectionStore);
    http = TestBed.inject(HttpTestingController);
    expect(fresh.activeChip()).toBeNull();
  });
});
