import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { RecentsFavoritesService } from './recents-favorites.service';
import { ResourceSelectionStore } from './resource-selection-store';
import { AuthService, type Identity } from '../auth/auth.service';

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

describe('RecentsFavoritesService (PRD 089)', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        RecentsFavoritesService,
        ResourceSelectionStore,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads recents + favorites when an identity appears and maps DTO → ResourceItem', async () => {
    const svc = TestBed.inject(RecentsFavoritesService);
    auth.identity.set(identity('u1'));
    // The identity effect runs on the next change-detection tick.
    TestBed.tick();

    http
      .expectOne('/api/recents')
      .flush([{ id: 'r1', kind: 'resource', label: 'Room 1', color: '#abc', typeKey: 'Raum' }]);
    http
      .expectOne('/api/favorites')
      .flush([{ id: 'u9', kind: 'user', label: 'Alice', color: null, typeKey: null }]);

    await Promise.resolve();
    await Promise.resolve();

    expect(svc.recents()).toEqual([
      { id: 'r1', label: 'Room 1', color: '#abc', kind: 'resource', typeKey: 'Raum' },
    ]);
    expect(svc.favorites()).toEqual([
      { id: 'u9', label: 'Alice', color: undefined, kind: 'user', typeKey: undefined },
    ]);
  });

  it('exposes the service lists through the ResourceSelectionStore', async () => {
    const svc = TestBed.inject(RecentsFavoritesService);
    const store = TestBed.inject(ResourceSelectionStore);
    auth.identity.set(identity('u1'));
    TestBed.tick();

    http
      .expectOne('/api/recents')
      .flush([{ id: 'r1', kind: 'resource', label: 'Room 1', color: null, typeKey: 'Raum' }]);
    http.expectOne('/api/favorites').flush([]);
    await Promise.resolve();
    await Promise.resolve();

    expect(store.recents()).toEqual(svc.recents());
    expect(store.recents().map((x) => x.id)).toEqual(['r1']);
    expect(store.favorites()).toEqual([]);
  });

  it('pushRecent POSTs and reconciles with the server-returned list', async () => {
    const store = TestBed.inject(ResourceSelectionStore);
    auth.identity.set(identity('u1'));
    TestBed.tick();
    http.expectOne('/api/recents').flush([]);
    http.expectOne('/api/favorites').flush([]);
    await Promise.resolve();
    await Promise.resolve();

    store.pushRecent({ id: 'r5', label: 'Room 5', kind: 'resource' });
    const post = http.expectOne('/api/recents');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ id: 'r5', kind: 'resource' });
    post.flush([{ id: 'r5', kind: 'resource', label: 'Room 5 (server)', color: null, typeKey: null }]);
    await Promise.resolve();

    expect(store.recents().map((x) => x.label)).toEqual(['Room 5 (server)']);
  });

  it('clearRecents DELETEs /api/recents', async () => {
    const store = TestBed.inject(ResourceSelectionStore);
    auth.identity.set(identity('u1'));
    TestBed.tick();
    http.expectOne('/api/recents').flush([{ id: 'r1', kind: 'resource', label: 'R1', color: null, typeKey: null }]);
    http.expectOne('/api/favorites').flush([]);
    await Promise.resolve();
    await Promise.resolve();

    store.clearRecents();
    const del = http.expectOne('/api/recents');
    expect(del.request.method).toBe('DELETE');
    del.flush([]);
    await Promise.resolve();
    expect(store.recents()).toEqual([]);
  });

  it('toggleFavorite POSTs to pin, DELETEs /{id} to unpin', async () => {
    const store = TestBed.inject(ResourceSelectionStore);
    auth.identity.set(identity('u1'));
    TestBed.tick();
    http.expectOne('/api/recents').flush([]);
    http.expectOne('/api/favorites').flush([]);
    await Promise.resolve();
    await Promise.resolve();

    store.toggleFavorite({ id: 'r7', label: 'Room 7', kind: 'resource' });
    const post = http.expectOne('/api/favorites');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ id: 'r7', kind: 'resource' });
    post.flush([{ id: 'r7', kind: 'resource', label: 'Room 7', color: null, typeKey: null }]);
    await Promise.resolve();
    expect(store.isFavorite('r7')).toBe(true);

    store.toggleFavorite({ id: 'r7', label: 'Room 7', kind: 'resource' });
    const del = http.expectOne('/api/favorites/r7');
    expect(del.request.method).toBe('DELETE');
    del.flush([]);
    await Promise.resolve();
    expect(store.isFavorite('r7')).toBe(false);
  });

  it('clears the lists when the identity disappears (logout), no bleed', async () => {
    const svc = TestBed.inject(RecentsFavoritesService);
    auth.identity.set(identity('u1'));
    TestBed.tick();
    http.expectOne('/api/recents').flush([{ id: 'r1', kind: 'resource', label: 'R1', color: null, typeKey: null }]);
    http.expectOne('/api/favorites').flush([]);
    await Promise.resolve();
    await Promise.resolve();
    expect(svc.recents().length).toBe(1);

    auth.identity.set(null);
    TestBed.tick();
    expect(svc.recents()).toEqual([]);
    expect(svc.favorites()).toEqual([]);
  });
});
