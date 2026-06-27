import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService, Identity } from './auth.service';

/**
 * PRD 072 Phase 4 — cookie-credential AuthService. Identity comes from
 * GET /api/auth/me; the SPA holds NO token (no localStorage, no OAuth lib).
 * These tests assert the wire shape of the calls and the derived signals —
 * they NEVER read a token out of storage because there is none.
 */
describe('AuthService (cookie model)', () => {
  let auth: AuthService;
  let httpMock: HttpTestingController;

  const identity = (over: Partial<Identity> = {}): Identity => ({
    userId: 'u-1',
    username: 'homer',
    name: 'Simpson Homer',
    admin: false,
    roles: [],
    impersonating: false,
    actor: null,
    target: null,
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.unstubAllGlobals();
  });

  /** Drain microtasks so a chained call (e.g. loadIdentity after a switch
   *  POST resolves) registers its request before we assert on it. */
  async function flushMicrotasks() {
    for (let i = 0; i < 5; i++) await Promise.resolve();
  }

  it('NEVER touches localStorage or sessionStorage (H4 closed)', () => {
    // The whole service surface must not read/write web storage. A spy that
    // throws would surface any stray access.
    const ls = vi.spyOn(Storage.prototype, 'getItem');
    const lsSet = vi.spyOn(Storage.prototype, 'setItem');
    auth.identity.set(identity({ admin: true }));
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.isImpersonating()).toBe(false);
    expect(auth.username()).toBe('homer');
    expect(ls).not.toHaveBeenCalled();
    expect(lsSet).not.toHaveBeenCalled();
  });

  it('loadIdentity() GETs /api/auth/me (relative URL) and populates the identity signal', async () => {
    const p = auth.loadIdentity();
    const req = httpMock.expectOne('/api/auth/me');
    expect(req.request.method).toBe('GET');
    // Relative URL — required so Angular's XSRF interceptor stays active.
    expect(req.request.url.startsWith('/api/')).toBe(true);
    req.flush(identity({ username: 'monty', admin: true, roles: ['admin'] }));
    const result = await p;

    expect(result?.username).toBe('monty');
    expect(auth.identity()?.admin).toBe(true);
    expect(auth.isLoggedIn()).toBe(true);
  });

  it('loadIdentity() resolves null and clears identity on 401 (no redirect)', async () => {
    auth.identity.set(identity());
    const p = auth.loadIdentity();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    const result = await p;

    expect(result).toBeNull();
    expect(auth.identity()).toBeNull();
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('impersonation state + actor are derived from /api/auth/me', async () => {
    const p = auth.loadIdentity();
    httpMock
      .expectOne('/api/auth/me')
      .flush(identity({ username: 'alice', impersonating: true, actor: 'admin', target: 'alice' }));
    await p;

    expect(auth.isImpersonating()).toBe(true);
    expect(auth.username()).toBe('alice'); // effective = target
    expect(auth.actorUsername()).toBe('admin');
  });

  it('impersonate(target) POSTs the cookie switch endpoint with target_username, then reloads identity', async () => {
    auth.identity.set(identity({ username: 'admin', admin: true }));

    const p = auth.impersonate('alice');
    const switchReq = httpMock.expectOne(
      (r) => r.url === '/api/auth/impersonate/switch' && r.method === 'POST',
    );
    expect(switchReq.request.params.get('target_username')).toBe('alice');
    switchReq.flush(null);
    await flushMicrotasks();

    // After switch, the service reloads identity.
    httpMock
      .expectOne('/api/auth/me')
      .flush(identity({ username: 'alice', impersonating: true, actor: 'admin', target: 'alice' }));

    expect(await p).toBe(true);
    expect(auth.isImpersonating()).toBe(true);
    expect(auth.username()).toBe('alice');
  });

  it('impersonate(self) when not impersonating short-circuits — no switch call', async () => {
    auth.identity.set(identity({ username: 'admin', admin: true }));
    const ok = await auth.impersonate('admin');
    expect(ok).toBe(true);
    httpMock.expectNone('/api/auth/impersonate/switch');
  });

  it('impersonate() returns false on a failed switch and does NOT reload identity', async () => {
    auth.identity.set(identity({ username: 'admin', admin: true }));
    const p = auth.impersonate('alice');
    httpMock
      .expectOne((r) => r.url === '/api/auth/impersonate/switch')
      .flush(null, { status: 403, statusText: 'Forbidden' });
    expect(await p).toBe(false);
    await flushMicrotasks();
    httpMock.expectNone('/api/auth/me');
  });

  it('endImpersonation() POSTs the cookie end endpoint, then reloads identity', async () => {
    auth.identity.set(
      identity({ username: 'alice', impersonating: true, actor: 'admin', target: 'alice' }),
    );
    const p = auth.endImpersonation();
    httpMock
      .expectOne((r) => r.url === '/api/auth/impersonate/end' && r.method === 'POST')
      .flush(null);
    await flushMicrotasks();
    httpMock.expectOne('/api/auth/me').flush(identity({ username: 'admin', admin: true }));

    expect(await p).toBe(true);
    expect(auth.isImpersonating()).toBe(false);
    expect(auth.username()).toBe('admin');
  });

  it('redirectToLogin() does a full browser navigation to the server /login (relative)', () => {
    const assign = stubLocationAssign();
    auth.redirectToLogin();
    expect(assign).toHaveBeenCalledWith('/login');
  });

  it('signOut() POSTs /api/auth/session/logout, clears identity and navigates to /login?logout', async () => {
    const assign = stubLocationAssign();
    auth.identity.set(identity());

    const done = auth.signOut();
    const req = httpMock.expectOne('/api/auth/session/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    await done;

    expect(auth.identity()).toBeNull();
    // ?logout makes the server /login page re-prompt at the IdP (prompt=login) so
    // this explicit sign-out is not silently undone by the live Keycloak SSO session.
    expect(assign).toHaveBeenCalledWith('/login?logout');
  });

  it('signOut() still clears identity and navigates even if the logout POST fails', async () => {
    const assign = stubLocationAssign();
    auth.identity.set(identity());

    const done = auth.signOut();
    httpMock.expectOne('/api/auth/session/logout').flush(null, { status: 500, statusText: 'err' });
    await done;

    expect(auth.identity()).toBeNull();
    expect(assign).toHaveBeenCalledWith('/login?logout');
  });

  /**
   * jsdom's `window.location.assign` is a non-configurable native method that
   * `vi.spyOn` can't redefine. Replace the whole `location` object (restored
   * automatically by Vitest's `vi.stubGlobal`) with one whose `assign` is a spy.
   */
  function stubLocationAssign(): ReturnType<typeof vi.fn> {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    return assign;
  }
});
