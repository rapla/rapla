import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { authInterceptor, __resetRefreshStateForTest } from './auth.interceptor';
import { AuthService } from './auth.service';

/**
 * PRD 072 Phase 4 — cookie-era reactive-401 refresh interceptor.
 *
 * The SPA holds no token, so the interceptor attaches NO Authorization header.
 * Its job is: on a 401 from /api, POST /api/auth/session/refresh once, replay the
 * original request; on refresh-401, clear identity + redirect to /login. The
 * stampede guard makes N concurrent 401s share ONE refresh.
 */
describe('authInterceptor (cookie refresh)', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authStub: {
    identity: { set: ReturnType<typeof vi.fn> };
    redirectToLogin: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    __resetRefreshStateForTest();
    authStub = {
      identity: { set: vi.fn() },
      redirectToLogin: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  /** Drain the microtask queue so the Promise-backed refresh resolves before
   *  the replay request reaches the HttpTestingController. */
  async function flushMicrotasks() {
    for (let i = 0; i < 5; i++) await Promise.resolve();
  }

  it('attaches NO Authorization header to /api requests (token lives in the cookie)', () => {
    http.get('/api/reservations').subscribe();
    const req = httpMock.expectOne('/api/reservations');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('on a GraphQL 200 with UNAUTHENTICATED error: refreshes once and replays', async () => {
    // GraphQL auth failures come back as HTTP 200 + errors[code: UNAUTHENTICATED]
    // (e.g. the access cookie expired during a long idle) — must refresh like a 401.
    let observed: unknown = null;
    http.post('/api/graphql', {}).subscribe((res) => (observed = res));
    httpMock
      .expectOne('/api/graphql')
      .flush({ errors: [{ message: 'auth', extensions: { code: 'UNAUTHENTICATED' } }] });
    await flushMicrotasks();

    httpMock.expectOne('/api/auth/session/refresh').flush(null);
    await flushMicrotasks();

    httpMock.expectOne('/api/graphql').flush({ data: { ok: true } });
    await flushMicrotasks();
    expect(observed).toEqual({ data: { ok: true } });
    expect(authStub.redirectToLogin).not.toHaveBeenCalled();
  });

  it('does NOT refresh on a non-auth GraphQL error (e.g. VIEW_NOT_FOUND)', async () => {
    http.post('/api/graphql', {}).subscribe({ next: vi.fn(), error: vi.fn() });
    httpMock
      .expectOne('/api/graphql')
      .flush({ errors: [{ message: 'nope', extensions: { code: 'VIEW_NOT_FOUND' } }] });
    httpMock.expectNone('/api/auth/session/refresh');
  });

  it('on 401 from /api: POSTs /api/auth/session/refresh once, then replays the original request', async () => {
    let observed: unknown = null;
    http.get('/api/reservations').subscribe((res) => (observed = res));

    httpMock
      .expectOne('/api/reservations')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    const refresh = httpMock.expectOne('/api/auth/session/refresh');
    expect(refresh.request.method).toBe('POST');
    refresh.flush(null);
    await flushMicrotasks();

    const replay = httpMock.expectOne('/api/reservations');
    replay.flush({ ok: true });
    await flushMicrotasks();

    expect(observed).toEqual({ ok: true });
    expect(authStub.redirectToLogin).not.toHaveBeenCalled();
  });

  it('on refresh 401: clears identity and redirects to /login (no replay)', async () => {
    http.get('/api/reservations').subscribe({ next: vi.fn(), error: vi.fn() });

    httpMock
      .expectOne('/api/reservations')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    httpMock
      .expectOne('/api/auth/session/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    httpMock.expectNone('/api/reservations'); // no replay
    expect(authStub.identity.set).toHaveBeenCalledWith(null);
    expect(authStub.redirectToLogin).toHaveBeenCalledTimes(1);
  });

  it('does NOT refresh on a 401 from the refresh endpoint itself (no recursion)', async () => {
    http.post('/api/auth/session/refresh', null).subscribe({ next: vi.fn(), error: vi.fn() });
    httpMock
      .expectOne('/api/auth/session/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();
    // No second refresh fired.
    httpMock.expectNone('/api/auth/session/refresh');
  });

  it('does NOT refresh on non-401 errors', () => {
    http.get('/api/reservations').subscribe({ next: vi.fn(), error: vi.fn() });
    httpMock
      .expectOne('/api/reservations')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    httpMock.expectNone('/api/auth/session/refresh');
    expect(authStub.redirectToLogin).not.toHaveBeenCalled();
  });

  it('stampede guard: two concurrent 401s share ONE refresh, then both replay', async () => {
    const observed: unknown[] = [];
    http.get('/api/a').subscribe((r) => observed.push(r));
    http.get('/api/b').subscribe((r) => observed.push(r));

    // Both initial requests 401.
    httpMock.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/b').flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    // Exactly ONE refresh for both.
    const refreshes = httpMock.match('/api/auth/session/refresh');
    expect(refreshes.length).toBe(1);
    refreshes[0].flush(null);
    await flushMicrotasks();

    // Both originals replay.
    httpMock.expectOne('/api/a').flush({ which: 'a' });
    httpMock.expectOne('/api/b').flush({ which: 'b' });
    await flushMicrotasks();

    expect(observed).toContainEqual({ which: 'a' });
    expect(observed).toContainEqual({ which: 'b' });
    expect(authStub.redirectToLogin).not.toHaveBeenCalled();
  });

  it('recovers when the refresh-owning request is torn down mid-refresh (no stuck refresh state)', async () => {
    // Request A 401s and OWNS the refresh, then is unsubscribed before the
    // refresh resolves — exactly what a tab suspend during a long idle, a route
    // change, or a switchMap-typeahead cancel does. The refresh lifecycle must
    // NOT be tied to A's subscription, or the module-global refresh flag stays
    // stuck and every later 401 hangs forever (only a page reload clears it).
    const subA = http.get('/api/a').subscribe({ next: vi.fn(), error: vi.fn() });
    httpMock.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    const firstRefresh = httpMock.expectOne('/api/auth/session/refresh'); // A owns it
    subA.unsubscribe(); // A torn down WHILE the refresh is in flight
    firstRefresh.flush(null); // the in-flight refresh still completes
    await flushMicrotasks();

    // A LATER request 401s. It must be able to refresh again, not hang.
    let observedB: unknown = null;
    http.get('/api/b').subscribe((r) => (observedB = r));
    httpMock.expectOne('/api/b').flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    httpMock.expectOne('/api/auth/session/refresh').flush(null); // BUG: none fired → stuck
    await flushMicrotasks();

    httpMock.expectOne('/api/b').flush({ ok: true });
    await flushMicrotasks();
    expect(observedB).toEqual({ ok: true });
    expect(authStub.redirectToLogin).not.toHaveBeenCalled();
  });

  it('stampede guard: when the shared refresh fails, all waiters redirect to /login', async () => {
    http.get('/api/a').subscribe({ next: vi.fn(), error: vi.fn() });
    http.get('/api/b').subscribe({ next: vi.fn(), error: vi.fn() });

    httpMock.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/b').flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    const refreshes = httpMock.match('/api/auth/session/refresh');
    expect(refreshes.length).toBe(1);
    refreshes[0].flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    httpMock.expectNone('/api/a');
    httpMock.expectNone('/api/b');
    expect(authStub.redirectToLogin).toHaveBeenCalled();
  });
});
