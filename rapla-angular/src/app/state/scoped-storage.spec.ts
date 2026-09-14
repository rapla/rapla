import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { FilterStore } from './filter-store';
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

/**
 * PRD 089 D2 — per-user localStorage namespacing for the remaining client-only
 * view-state keys. The bindPerUser effect must RELOAD the store's signal when
 * the identity flips, so account A and account B on one browser never see each
 * other's scope chips.
 */
describe('per-user namespaced localStorage (PRD 089 D2)', () => {
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FilterStore, AuthService],
    });
    auth = TestBed.inject(AuthService);
  });

  it('isolates rapla.scope per user and reloads on identity change', () => {
    auth.identity.set(identity('alice'));
    const store = TestBed.inject(FilterStore);
    TestBed.tick();

    store.replace({ id: 'r1', kind: 'resource', label: 'Room 1' });
    expect(store.entries().map((e) => e.id)).toEqual(['r1']);
    // The physical key carries alice's suffix; bob's slot is untouched.
    expect(localStorage.getItem('rapla.scope::u=alice')).toContain('r1');
    expect(localStorage.getItem('rapla.scope::u=bob')).toBeNull();

    // Switch to bob → bob sees an empty scope (his slot is empty).
    auth.identity.set(identity('bob'));
    TestBed.tick();
    expect(store.entries()).toEqual([]);

    store.replace({ id: 'r2', kind: 'resource', label: 'Room 2' });
    expect(localStorage.getItem('rapla.scope::u=bob')).toContain('r2');

    // Switch back to alice → her original value returns (bindPerUser reload).
    auth.identity.set(identity('alice'));
    TestBed.tick();
    expect(store.entries().map((e) => e.id)).toEqual(['r1']);
  });

  it('falls back to ::u=anon when unauthenticated', () => {
    const store = TestBed.inject(FilterStore);
    TestBed.tick();
    store.replace({ id: 'r9', kind: 'resource', label: 'Room 9' });
    expect(localStorage.getItem('rapla.scope::u=anon')).toContain('r9');
  });
});
