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

    // Simulate an active impersonation override.
    auth.impersonationOverride.set({
      accessToken: 'imp-bearer',
      targetUsername: 'alice',
    });
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
  });

  it('handleUnauthenticated() clears the impersonation override', () => {
    auth.handleUnauthenticated();
    expect(auth.impersonationOverride()).toBeNull();
    expect(auth.isImpersonating()).toBe(false);
    // Route to /login is part of the contract; sanity-check it didn't
    // get refactored away from this path.
    expect(routerStub.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('handleAuthRejection() clears the impersonation override AND bumps oauthFailures', () => {
    sessionStorage.setItem('oauthFailures', '2');
    auth.handleAuthRejection();
    expect(auth.impersonationOverride()).toBeNull();
    expect(auth.isImpersonating()).toBe(false);
    expect(sessionStorage.getItem('oauthFailures')).toBe('3');
  });

  it('endImpersonation() (explicit "Switch back") clears the override without touching login state', () => {
    auth.endImpersonation();
    expect(auth.impersonationOverride()).toBeNull();
    expect(oauthStub.logOut).not.toHaveBeenCalled();
    expect(routerStub.navigateByUrl).not.toHaveBeenCalled();
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
