import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { UsersService } from './users.service';
import { AuthService } from './auth.service';

/**
 * PRD 051 — locks in the rule that {@code GET /api/users} is issued with the
 * admin's Bearer rather than the impersonation override, even while the user
 * is actively impersonating. The bug was: while impersonating, clicking the
 * chip to switch targets fetched the impersonated user's admin-scope (usually
 * empty) and the dialog showed no candidates. The fix bypasses {@code HttpClient}
 * (whose interceptor would attach {@code token()} — the override) and uses
 * raw {@code fetch} with {@code adminToken()}.
 */
describe('UsersService', () => {
  let authStub: {
    token: ReturnType<typeof vi.fn>;
    adminToken: ReturnType<typeof vi.fn>;
  };
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    authStub = {
      token: vi.fn(() => 'IMPERSONATION-OVERRIDE'),
      adminToken: vi.fn(() => 'ADMIN-BEARER'),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authStub }],
    });
    fetchSpy = vi.fn(async () =>
      new Response(JSON.stringify([{ username: 'alice', displayName: 'Alice' }]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('uses adminToken() — NOT token() — for the Authorization header', async () => {
    const users = TestBed.inject(UsersService);
    const result = await firstValueFrom(users.list());

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/users');
    expect((init as RequestInit).headers).toMatchObject({
      Authorization: 'Bearer ADMIN-BEARER',
    });
    expect(authStub.adminToken).toHaveBeenCalled();
    // The interceptor-attached token (impersonation override when active)
    // must never be queried by this service — that was the bug.
    expect(authStub.token).not.toHaveBeenCalled();
    expect(result).toEqual([{ username: 'alice', displayName: 'Alice' }]);
  });

  it('returns [] without calling fetch when adminToken() is null', async () => {
    authStub.adminToken.mockReturnValue(null);
    const users = TestBed.inject(UsersService);
    const result = await firstValueFrom(users.list());
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(result).toEqual([]);
  });

  it('returns [] on non-2xx responses (chip stays non-clickable)', async () => {
    fetchSpy.mockResolvedValueOnce(
      new Response('{"error":"forbidden"}', {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const users = TestBed.inject(UsersService);
    const result = await firstValueFrom(users.list());
    expect(result).toEqual([]);
  });

  it('returns [] on network errors (does not surface to caller)', async () => {
    fetchSpy.mockRejectedValueOnce(new TypeError('network down'));
    const users = TestBed.inject(UsersService);
    const result = await firstValueFrom(users.list());
    expect(result).toEqual([]);
  });
});
