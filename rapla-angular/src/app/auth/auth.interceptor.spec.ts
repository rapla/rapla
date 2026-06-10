import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authStub: {
    token: ReturnType<typeof vi.fn>;
    adminToken: ReturnType<typeof vi.fn>;
    refreshAccessToken: ReturnType<typeof vi.fn>;
    handleUnauthenticated: ReturnType<typeof vi.fn>;
    handleAuthRejection: ReturnType<typeof vi.fn>;
    isImpersonating: ReturnType<typeof vi.fn>;
    renewImpersonation: ReturnType<typeof vi.fn>;
    endImpersonation: ReturnType<typeof vi.fn>;
  };
  let dialogStub: { open: ReturnType<typeof vi.fn> };
  let dialogRefAfterClosed: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    authStub = {
      token: vi.fn(() => 'access-1'),
      adminToken: vi.fn(() => 'access-1'),
      refreshAccessToken: vi.fn(async () => false),
      handleUnauthenticated: vi.fn(),
      handleAuthRejection: vi.fn(),
      isImpersonating: vi.fn(() => false),
      renewImpersonation: vi.fn(async () => false),
      endImpersonation: vi.fn(),
    };

    dialogRefAfterClosed = vi.fn(() => of(undefined));
    dialogStub = {
      open: vi.fn(() => ({ afterClosed: dialogRefAfterClosed })),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
        { provide: MatDialog, useValue: dialogStub },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('attaches Bearer to regular API requests', () => {
    http.get('/api/reservations').subscribe();
    const req = httpMock.expectOne('/api/reservations');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush({});
  });

  it('does NOT attach Bearer to /api/auth/oauth/exchange/*', () => {
    http.post('/api/auth/oauth/exchange/keycloak', 'code=abc').subscribe();
    const req = httpMock.expectOne('/api/auth/oauth/exchange/keycloak');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ access_token: 'fresh' });
  });

  it('does NOT attach Bearer to /api/auth/oauth/config', () => {
    http.get('/api/auth/oauth/config').subscribe();
    const req = httpMock.expectOne('/api/auth/oauth/config');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does NOT attach Bearer to /oauth2/*', () => {
    http.post('/oauth2/token', 'grant_type=password').subscribe();
    const req = httpMock.expectOne('/oauth2/token');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does NOT open dialog or trigger handleUnauthenticated on 401 from a pre-auth path', () => {
    http.post('/api/auth/oauth/exchange/keycloak', 'code=abc').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });
    httpMock
      .expectOne('/api/auth/oauth/exchange/keycloak')
      .flush({ error: 'invalid_grant' }, { status: 401, statusText: 'Unauthorized' });
    expect(authStub.refreshAccessToken).not.toHaveBeenCalled();
    expect(dialogStub.open).not.toHaveBeenCalled();
    expect(authStub.handleUnauthenticated).not.toHaveBeenCalled();
    expect(authStub.handleAuthRejection).not.toHaveBeenCalled();
  });

  it('routes to /login silently (no dialog, no failure counter bump) on 401 when no Bearer was attached', () => {
    authStub.token.mockReturnValue(null);

    http.get('/api/reservations').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });
    httpMock
      .expectOne('/api/reservations')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authStub.refreshAccessToken).not.toHaveBeenCalled();
    expect(dialogStub.open).not.toHaveBeenCalled();
    expect(authStub.handleUnauthenticated).toHaveBeenCalledTimes(1);
    expect(authStub.handleAuthRejection).not.toHaveBeenCalled();
  });

  /**
   * The refresh path goes through a Promise; tests must drain the
   * microtask queue after flushing the initial 401 before the replay
   * request hits the HttpTestingController. A single await of a
   * pre-resolved promise is enough — Vitest doesn't ship fakeAsync.
   */
  async function flushMicrotasks() {
    for (let i = 0; i < 5; i++) await Promise.resolve();
  }

  it('on 401 with Bearer: tries refresh, replays request with fresh token, no dialog if 200', async () => {
    authStub.refreshAccessToken.mockImplementation(async () => {
      authStub.token.mockReturnValue('access-2'); // simulate library swap
      return true;
    });

    let observed: unknown = null;
    http.get('/api/reservations').subscribe((res) => (observed = res));

    httpMock
      .expectOne((r) => r.url === '/api/reservations' && r.headers.get('Authorization') === 'Bearer access-1')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    const replay = httpMock.expectOne(
      (r) => r.url === '/api/reservations' && r.headers.get('Authorization') === 'Bearer access-2',
    );
    replay.flush({ ok: true });
    await flushMicrotasks();

    expect(authStub.refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(dialogStub.open).not.toHaveBeenCalled();
    expect(authStub.handleUnauthenticated).not.toHaveBeenCalled();
    expect(observed).toEqual({ ok: true });
  });

  it('on 401 with Bearer: refresh succeeds but replay 401s → dialog shown, no second refresh', async () => {
    authStub.refreshAccessToken.mockImplementation(async () => {
      authStub.token.mockReturnValue('access-2');
      return true;
    });

    http.get('/api/reservations').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });

    httpMock
      .expectOne((r) => r.headers.get('Authorization') === 'Bearer access-1')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    httpMock
      .expectOne((r) => r.headers.get('Authorization') === 'Bearer access-2')
      .flush(
        { error_description: 'name already taken' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await flushMicrotasks();

    expect(authStub.refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(dialogStub.open).toHaveBeenCalledTimes(1);
    expect(dialogStub.open.mock.calls[0][1].data.message).toBe('name already taken');
    expect(authStub.handleAuthRejection).toHaveBeenCalledTimes(1);
    expect(authStub.handleUnauthenticated).not.toHaveBeenCalled();
  });

  it('on 401 with Bearer + refresh returns false: dialog shown immediately, no replay', async () => {
    authStub.refreshAccessToken.mockResolvedValue(false);

    http.get('/api/reservations').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });

    httpMock
      .expectOne('/api/reservations')
      .flush(
        { error_description: 'name already taken' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await flushMicrotasks();

    expect(authStub.refreshAccessToken).toHaveBeenCalledTimes(1);
    httpMock.expectNone('/api/reservations');
    expect(dialogStub.open).toHaveBeenCalledTimes(1);
    expect(authStub.handleAuthRejection).toHaveBeenCalledTimes(1);
    expect(authStub.handleUnauthenticated).not.toHaveBeenCalled();
  });

  it('falls back to WWW-Authenticate error_description when response body is empty', async () => {
    authStub.refreshAccessToken.mockResolvedValue(false);

    http.get('/api/reservations').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });
    httpMock.expectOne('/api/reservations').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
      headers: {
        'WWW-Authenticate':
          'Bearer error="invalid_token", error_description="Signature mismatch"',
      },
    });
    await flushMicrotasks();
    expect(dialogStub.open).toHaveBeenCalledTimes(1);
    expect(dialogStub.open.mock.calls[0][1].data.message).toBe('Signature mismatch');
  });

  it('shows the generic explanation when neither body nor WWW-Authenticate carry an error_description', async () => {
    authStub.refreshAccessToken.mockResolvedValue(false);

    http.get('/api/reservations').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });
    httpMock.expectOne('/api/reservations').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });
    await flushMicrotasks();
    expect(dialogStub.open).toHaveBeenCalledTimes(1);
    expect(dialogStub.open.mock.calls[0][1].data.message).toContain(
      'could not link it to a user account',
    );
  });

  it('PRD 051: on 401 with impersonation Bearer attached, renews impersonation first, then replays', async () => {
    // Simulate active impersonation: token() returns the override, adminToken() returns admin's.
    authStub.token.mockReturnValue('imp-1');
    authStub.adminToken.mockReturnValue('admin-1');
    authStub.isImpersonating.mockReturnValue(true);
    authStub.renewImpersonation.mockImplementation(async () => {
      authStub.token.mockReturnValue('imp-2'); // fresh override
      return true;
    });

    let observed: unknown = null;
    http.get('/api/reservations').subscribe((res) => (observed = res));

    httpMock
      .expectOne((r) => r.headers.get('Authorization') === 'Bearer imp-1')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    const replay = httpMock.expectOne(
      (r) => r.headers.get('Authorization') === 'Bearer imp-2',
    );
    replay.flush({ ok: true });
    await flushMicrotasks();

    expect(authStub.renewImpersonation).toHaveBeenCalledTimes(1);
    expect(authStub.refreshAccessToken).not.toHaveBeenCalled();
    expect(dialogStub.open).not.toHaveBeenCalled();
    expect(observed).toEqual({ ok: true });
  });

  it('PRD 051: on 401 with impersonation Bearer + admin also stale, refreshes admin then re-renews', async () => {
    authStub.token.mockReturnValue('imp-1');
    authStub.adminToken.mockReturnValue('admin-1');
    authStub.isImpersonating.mockReturnValue(true);
    // First renewImpersonation fails (admin Bearer stale), then admin refresh
    // succeeds, then second renewImpersonation succeeds with new token.
    let renewCallCount = 0;
    authStub.renewImpersonation.mockImplementation(async () => {
      renewCallCount++;
      if (renewCallCount === 1) return false;
      authStub.token.mockReturnValue('imp-2');
      return true;
    });
    authStub.refreshAccessToken.mockResolvedValue(true);

    http.get('/api/reservations').subscribe();

    httpMock
      .expectOne((r) => r.headers.get('Authorization') === 'Bearer imp-1')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    const replay = httpMock.expectOne(
      (r) => r.headers.get('Authorization') === 'Bearer imp-2',
    );
    replay.flush({ ok: true });
    await flushMicrotasks();

    expect(authStub.renewImpersonation).toHaveBeenCalledTimes(2);
    expect(authStub.refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(dialogStub.open).not.toHaveBeenCalled();
  });

  it('PRD 051: on 401 with impersonation Bearer + both renewals fail → dialog', async () => {
    authStub.token.mockReturnValue('imp-1');
    authStub.adminToken.mockReturnValue('admin-1');
    authStub.isImpersonating.mockReturnValue(true);
    authStub.renewImpersonation.mockResolvedValue(false);
    authStub.refreshAccessToken.mockResolvedValue(false);

    http.get('/api/reservations').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });

    httpMock
      .expectOne((r) => r.headers.get('Authorization') === 'Bearer imp-1')
      .flush(
        { error_description: 'name already taken' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await flushMicrotasks();

    expect(authStub.renewImpersonation).toHaveBeenCalledTimes(1);
    expect(authStub.refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(dialogStub.open).toHaveBeenCalledTimes(1);
    expect(authStub.handleAuthRejection).toHaveBeenCalledTimes(1);
  });

  it('does not fire dialog/redirect on non-401 errors', () => {
    http.get('/api/reservations').subscribe({
      next: vi.fn(),
      error: vi.fn(),
    });
    httpMock
      .expectOne('/api/reservations')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    expect(authStub.refreshAccessToken).not.toHaveBeenCalled();
    expect(dialogStub.open).not.toHaveBeenCalled();
    expect(authStub.handleUnauthenticated).not.toHaveBeenCalled();
    expect(authStub.handleAuthRejection).not.toHaveBeenCalled();
  });
});
