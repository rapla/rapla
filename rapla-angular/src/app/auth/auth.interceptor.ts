import {
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import {
  BehaviorSubject,
  Observable,
  catchError,
  filter,
  from,
  switchMap,
  take,
  throwError,
} from 'rxjs';

import { AuthService } from './auth.service';

/**
 * PRD 072 Phase 4 — cookie-era reactive-401 refresh interceptor.
 *
 * The SPA holds NO token: the rapla JWT lives in an HttpOnly {@code access_token}
 * cookie the browser auto-sends same-origin. This interceptor therefore attaches
 * NO Authorization header. Its only job is the reactive refresh:
 *
 *   1. on a 401 from {@code /api} (other than the refresh endpoint itself),
 *   2. call {@code POST /api/auth/refresh} ONCE (the refresh cookie is
 *      auto-sent; the server sets a fresh {@code access_token} cookie),
 *   3. replay the original request (cookie now fresh).
 *   4. If refresh itself 401s, the refresh cookie is invalid/expired → clear
 *      identity and redirect the browser to the server {@code /login} page.
 *
 * Stampede guard: concurrent 401s share ONE in-flight refresh. The first 401
 * flips {@code isRefreshing} and starts the refresh; subsequent 401s wait on a
 * shared {@link BehaviorSubject} that emits the outcome once, then replay — so
 * N concurrent expiries trigger exactly one {@code /refresh} call.
 *
 * The refresh call goes through {@link HttpClient} (not raw `next`) so Angular's
 * XSRF interceptor attaches {@code X-XSRF-TOKEN}; the {@code REFRESH_URL} guard
 * keeps the refresh request itself out of this 401 handler (no recursion).
 */

const REFRESH_URL = '/api/auth/refresh';

// Module-scoped so every interceptor invocation (one per request) shares the
// same refresh state — that is what makes the stampede guard work across
// concurrent requests.
let isRefreshing = false;
// Emits the refresh outcome (true = fresh cookie, false = refresh failed) to
// requests that arrived mid-refresh. null = "no refresh has completed this
// cycle"; waiters filter it out and take the first concrete result.
const refreshResult$ = new BehaviorSubject<boolean | null>(null);

const isRefreshRequest = (req: HttpRequest<unknown>): boolean => req.url.includes(REFRESH_URL);
const isApiRequest = (req: HttpRequest<unknown>): boolean => req.url.includes('/api/');

/**
 * Test-only: reset the module-scoped refresh state between specs. The stampede
 * guard is intentionally module-global (shared across all requests in the
 * running app), so tests must clear it in {@code beforeEach} to avoid leaking a
 * stale {@code refreshResult$} value or a stuck {@code isRefreshing} flag.
 */
export function __resetRefreshStateForTest(): void {
  isRefreshing = false;
  refreshResult$.next(null);
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const http = inject(HttpClient);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || !isApiRequest(req) || isRefreshRequest(req)) {
        return throwError(() => err);
      }
      return handle401(req, next, auth, http, err);
    }),
  );
};

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
  http: HttpClient,
  err: HttpErrorResponse,
): Observable<HttpEvent<unknown>> {
  if (isRefreshing) {
    // A refresh is already in flight — wait for its outcome, then replay or
    // bail. Shares the single /refresh call (stampede guard).
    return refreshResult$.pipe(
      filter((result): result is boolean => result !== null),
      take(1),
      switchMap((ok) => (ok ? next(req) : bounceToLogin(auth, err))),
    );
  }

  // First 401 of this cycle: own the refresh.
  isRefreshing = true;
  refreshResult$.next(null);

  return from(doRefresh(http)).pipe(
    switchMap((ok) => {
      isRefreshing = false;
      refreshResult$.next(ok);
      return ok ? next(req) : bounceToLogin(auth, err);
    }),
  );
}

/** POST /api/auth/refresh via HttpClient (so XSRF is attached). Resolves true on success. */
async function doRefresh(http: HttpClient): Promise<boolean> {
  try {
    await new Promise<void>((resolve, reject) => {
      http.post(REFRESH_URL, null).subscribe({ next: () => resolve(), error: reject });
    });
    return true;
  } catch {
    return false;
  }
}

function bounceToLogin(auth: AuthService, err: HttpErrorResponse): Observable<never> {
  auth.identity.set(null);
  auth.redirectToLogin();
  return throwError(() => err);
}
