import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * PRD 051 — locks in the rule that every logout-style path on
 * {@link AuthService} clears the impersonation override before the
 * user is bounced back to {@code /login}. Without this, an admin who
 * is impersonating Alice, then loses their refresh token (server
 * 401), would see their session torn down — but the override would
 * survive in memory. On the next login the very first outbound
 * request would still carry Alice's impersonation Bearer, silently
 * acting as Alice in a fresh session. That's the regression this
 * spec exists to catch.
 *
 * <p>Three paths to cover:
 * <ul>
 *   <li>{@code signOut()} — admin clicks "Sign out".</li>
 *   <li>{@code handleUnauthenticated()} — interceptor saw a 401 with
 *       no Bearer attached (session never had one or it was cleared).</li>
 *   <li>{@code handleAuthRejection()} — interceptor saw a 401 with a
 *       Bearer attached (server actively rejected). This path also
 *       bumps {@code oauthFailures}, but the contract under test here
 *       is the override clear.</li>
 * </ul>
 */
describe('AuthService — logout paths clear impersonation override', () => {
  let auth: AuthService;
  let oauthStub: {
    events: Subject<unknown>;
    logOut: ReturnType<typeof vi.fn>;
    hasValidAccessToken: ReturnType<typeof vi.fn>;
    getRefreshToken: ReturnType<typeof vi.fn>;
    getIdentityClaims: ReturnType<typeof vi.fn>;
    getAccessToken: ReturnType<typeof vi.fn>;
    loginUrl: string;
  };
  let routerStub: { navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    oauthStub = {
      events: new Subject<unknown>(),
      logOut: vi.fn(),
      hasValidAccessToken: vi.fn(() => true),
      getRefreshToken: vi.fn(() => null),
      getIdentityClaims: vi.fn(() => ({ sub: 'admin-uuid', preferred_username: 'admin' })),
      getAccessToken: vi.fn(() => 'admin-bearer'),
      loginUrl: 'https://example.test/authorize',
    };
    routerStub = { navigateByUrl: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: OAuthService, useValue: oauthStub },
        { provide: Router, useValue: routerStub },
      ],
    });
    auth = TestBed.inject(AuthService);

    // Simulate an active impersonation override — both the in-memory
    // signal AND the sessionStorage entry, mirroring what
    // {@link AuthService#impersonate} would have written on success.
    auth.impersonationOverride.set({
      accessToken: 'imp-bearer',
      targetUsername: 'alice',
    });
    sessionStorage.setItem(
      'rapla.impersonationOverride',
      JSON.stringify({ accessToken: 'imp-bearer', targetUsername: 'alice' }),
    );
    expect(auth.isImpersonating()).toBe(true);
  });

  afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('signOut() clears the impersonation override', () => {
    auth.signOut();
    expect(auth.impersonationOverride()).toBeNull();
    expect(auth.isImpersonating()).toBe(false);
    expect(sessionStorage.getItem('rapla.impersonationOverride')).toBeNull();
  });

  it('handleUnauthenticated() clears the impersonation override', () => {
    auth.handleUnauthenticated();
    expect(auth.impersonationOverride()).toBeNull();
    expect(auth.isImpersonating()).toBe(false);
    expect(sessionStorage.getItem('rapla.impersonationOverride')).toBeNull();
    // Route to /login is part of the contract; sanity-check it didn't
    // get refactored away from this path.
    expect(routerStub.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('handleAuthRejection() clears the impersonation override AND bumps oauthFailures', () => {
    sessionStorage.setItem('oauthFailures', '2');
    auth.handleAuthRejection();
    expect(auth.impersonationOverride()).toBeNull();
    expect(auth.isImpersonating()).toBe(false);
    expect(sessionStorage.getItem('rapla.impersonationOverride')).toBeNull();
    expect(sessionStorage.getItem('oauthFailures')).toBe('3');
  });

  it('endImpersonation() (explicit "Switch back") clears the override without touching login state', () => {
    auth.endImpersonation();
    expect(auth.impersonationOverride()).toBeNull();
    expect(sessionStorage.getItem('rapla.impersonationOverride')).toBeNull();
    expect(oauthStub.logOut).not.toHaveBeenCalled();
    expect(routerStub.navigateByUrl).not.toHaveBeenCalled();
  });
});

/**
 * PRD 051 — the impersonation override survives a browser reload (F5)
 * via sessionStorage. Without persistence, an admin who switched to
 * Alice sees the toolbar revert to admin on every page reload — every
 * navigation in the SPA becomes a session-loss surprise. Tab-scoped
 * storage matches the design intent (a new tab starts fresh as admin;
 * walking away and closing the tab discards the override).
 *
 * <p>Three guarantees under test:
 * <ul>
 *   <li>{@code impersonate()} persists {accessToken, targetUsername} to
 *       sessionStorage on a successful {@code POST /api/auth/impersonate}.</li>
 *   <li>A fresh {@link AuthService} instance rehydrates from sessionStorage
 *       at construction time — same tab, page reload, override is back.</li>
 *   <li>A corrupt sessionStorage payload (malformed JSON, missing fields)
 *       is treated as no override at all and silently cleared; the admin
 *       lands as themselves rather than a hard error.</li>
 * </ul>
 */
describe('AuthService — impersonation override persists across reload (sessionStorage)', () => {
  const STORAGE_KEY = 'rapla.impersonationOverride';

  let fetchMock: ReturnType<typeof vi.fn>;

  function configureTestBed(): AuthService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {
          provide: OAuthService,
          useValue: {
            events: new Subject<unknown>(),
            logOut: vi.fn(),
            hasValidAccessToken: vi.fn(() => true),
            getRefreshToken: vi.fn(() => null),
            getIdentityClaims: vi.fn(() => ({ sub: 'admin-uuid', preferred_username: 'admin' })),
            getAccessToken: vi.fn(() => 'ADMIN-ACCESS'),
            getIdToken: vi.fn(() => null),
            loginUrl: 'https://example.test/authorize',
          },
        },
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    fetchMock = vi.fn();
    globalThis.fetch = fetchMock as unknown as typeof fetch;
  });

  afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('impersonate() writes the override to sessionStorage on success', async () => {
    const auth = configureTestBed();
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ access_token: 'IMP-TOKEN-FOR-ALICE' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const ok = await auth.impersonate('alice');
    expect(ok).toBe(true);

    const raw = sessionStorage.getItem(STORAGE_KEY);
    expect(raw).not.toBeNull();
    expect(JSON.parse(raw as string)).toEqual({
      accessToken: 'IMP-TOKEN-FOR-ALICE',
      targetUsername: 'alice',
    });
  });

  it('impersonate() does NOT write to sessionStorage on failure', async () => {
    const auth = configureTestBed();
    fetchMock.mockResolvedValueOnce(new Response('', { status: 403 }));

    const ok = await auth.impersonate('alice');
    expect(ok).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('a freshly constructed AuthService rehydrates the override from sessionStorage', () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ accessToken: 'PERSISTED-IMP-TOKEN', targetUsername: 'alice' }),
    );

    const auth = configureTestBed();

    expect(auth.isImpersonating()).toBe(true);
    expect(auth.impersonationOverride()).toEqual({
      accessToken: 'PERSISTED-IMP-TOKEN',
      targetUsername: 'alice',
    });
    // token() returns the persisted override, not the admin Bearer —
    // this is what makes the next outbound request continue as alice.
    expect(auth.token()).toBe('PERSISTED-IMP-TOKEN');
  });

  it('a malformed sessionStorage payload is ignored and cleared (no impersonation, no error)', () => {
    sessionStorage.setItem(STORAGE_KEY, '{not valid json');

    const auth = configureTestBed();

    expect(auth.isImpersonating()).toBe(false);
    expect(auth.impersonationOverride()).toBeNull();
    // Defensive: stale garbage gets evicted so it can't trip up later code.
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('a partial sessionStorage payload (missing fields) is ignored and cleared', () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken: 'orphan' }));

    const auth = configureTestBed();

    expect(auth.isImpersonating()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});

/**
 * PRD 051 — if the admin "switches to" their own username (whether
 * by typo, by re-selecting themselves from the autocomplete, or by
 * clicking the user chip after a reload), that is semantically
 * "stop impersonating", not "create a self-impersonation token".
 * Otherwise the admin would carry a server-minted JWT whose `sub`
 * is themselves with `act` also themselves — useless at best,
 * confusing at worst, and a wasted round-trip + audit-log entry.
 *
 * <p>Two invariants:
 * <ul>
 *   <li>Calling {@code impersonate('admin')} (admin's own username)
 *       does NOT hit {@code POST /api/auth/impersonate} — it short-
 *       circuits to {@link AuthService#endImpersonation}.</li>
 *   <li>If an impersonation is already active and the admin selects
 *       themselves, the override is cleared.</li>
 * </ul>
 */
describe('AuthService.impersonate() — switching to your own user ends impersonation', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  function configure(): AuthService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {
          provide: OAuthService,
          useValue: {
            events: new Subject<unknown>(),
            logOut: vi.fn(),
            hasValidAccessToken: vi.fn(() => true),
            getRefreshToken: vi.fn(() => null),
            getIdentityClaims: vi.fn(() => ({ sub: 'admin-uuid', preferred_username: 'admin' })),
            getAccessToken: vi.fn(() => 'ADMIN-ACCESS'),
            getIdToken: vi.fn(() => null),
            loginUrl: 'https://example.test/authorize',
          },
        },
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    fetchMock = vi.fn();
    globalThis.fetch = fetchMock as unknown as typeof fetch;
  });

  afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('impersonate(adminUsername) short-circuits — no fetch, returns true, no override set', async () => {
    const auth = configure();
    const ok = await auth.impersonate('admin');
    expect(ok).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(auth.impersonationOverride()).toBeNull();
    expect(auth.isImpersonating()).toBe(false);
  });

  it('impersonate(adminUsername) while an impersonation is active clears the override (= switch back)', async () => {
    const auth = configure();
    // Start by impersonating alice.
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ access_token: 'IMP-FOR-ALICE' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    await auth.impersonate('alice');
    expect(auth.isImpersonating()).toBe(true);

    // Now select admin's own username.
    fetchMock.mockClear();
    const ok = await auth.impersonate('admin');

    expect(ok).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(auth.impersonationOverride()).toBeNull();
    expect(sessionStorage.getItem('rapla.impersonationOverride')).toBeNull();
  });
});

/**
 * PRD 051 — adminToken() must return the admin's Bearer even while
 * an impersonation override is active. {@link UsersService} and
 * {@link AuthService#impersonate} both rely on this to send the right
 * Bearer to {@code GET /api/users} and {@code POST /api/auth/impersonate}
 * (see {@code docs/authentication.md} § "Switching from one target to
 * another mid-impersonation").
 */
describe('AuthService.adminToken() — admin Bearer survives impersonation', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {
          provide: OAuthService,
          useValue: {
            events: new Subject<unknown>(),
            logOut: vi.fn(),
            hasValidAccessToken: vi.fn(() => true),
            getRefreshToken: vi.fn(() => null),
            getIdentityClaims: vi.fn(() => ({
              sub: 'admin-uuid',
              preferred_username: 'admin',
            })),
            getAccessToken: vi.fn(() => 'ADMIN-ACCESS'),
            getIdToken: vi.fn(() => null),
            loginUrl: 'https://example.test/authorize',
          },
        },
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
  });

  it('returns the admin Bearer even when an impersonation override is active', () => {
    const auth = TestBed.inject(AuthService);
    auth.impersonationOverride.set({
      accessToken: 'IMPERSONATION-OVERRIDE',
      targetUsername: 'alice',
    });

    // token() — what the interceptor attaches — must be the override
    expect(auth.token()).toBe('IMPERSONATION-OVERRIDE');
    // adminToken() — what UsersService + AuthService.impersonate use —
    // must be the admin's actual Bearer, regardless of override state.
    expect(auth.adminToken()).toBe('ADMIN-ACCESS');
  });
});
