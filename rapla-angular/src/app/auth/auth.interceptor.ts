import {
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, finalize, from, shareReplay, switchMap, take, throwError } from 'rxjs';

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
 * starts a module-shared refresh {@link Observable}; subsequent 401s reuse the
 * same one and replay on its outcome — so N concurrent expiries trigger exactly
 * one {@code /refresh} call.
 *
 * Crucially the shared refresh is NOT tied to the triggering request's
 * subscription: it is eagerly subscribed (a self-driving worker) and resets
 * itself via {@code finalize} when it completes. If the request that first hit
 * the 401 is torn down mid-refresh (tab suspend during a long idle, route
 * change, switchMap-typeahead cancel), the refresh still completes and the next
 * 401 starts a fresh one — instead of a stuck flag that wedges every later 401
 * until a full page reload (the cookie-refresh regression this guards against).
 *
 * The refresh call goes through {@link HttpClient} (not raw `next`) so Angular's
 * XSRF interceptor attaches {@code X-XSRF-TOKEN}; the {@code REFRESH_URL} guard
 * keeps the refresh request itself out of this 401 handler (no recursion).
 */

const REFRESH_URL = '/api/auth/refresh';

// The single in-flight refresh, shared across all concurrent 401s. null = "no
// refresh running"; the first 401 creates it, finalize clears it on completion
// so the next expiry cycle starts clean. Its lifecycle is independent of any
// request subscription (see class doc) — that is what survives a torn-down owner.
let refresh$: Observable<boolean> | null = null;

const isRefreshRequest = (req: HttpRequest<unknown>): boolean => req.url.includes(REFRESH_URL);
const isApiRequest = (req: HttpRequest<unknown>): boolean => req.url.includes('/api/');

/**
 * The shared refresh worker. {@code shareReplay(1)} multicasts one
 * {@code /refresh} result to every concurrent waiter; the eager
 * {@code .subscribe()} drives it to completion even if every triggering request
 * later unsubscribes; {@code finalize} resets the slot so a future 401 refreshes
 * again. {@code doRefresh} never throws (resolves true/false), so the eager
 * subscription needs no error branch beyond a defensive no-op.
 */
function sharedRefresh(http: HttpClient): Observable<boolean> {
  if (!refresh$) {
    refresh$ = from(doRefresh(http)).pipe(
      finalize(() => {
        refresh$ = null;
      }),
      shareReplay(1),
    );
    refresh$.subscribe({ error: () => {} });
  }
  return refresh$;
}

/**
 * Test-only: reset the module-scoped refresh state between specs. The stampede
 * guard is intentionally module-global (shared across all requests in the
 * running app), so tests must clear it in {@code beforeEach} to avoid leaking an
 * in-flight shared refresh into the next spec.
 */
export function __resetRefreshStateForTest(): void {
  refresh$ = null;
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
  // Reuse the single in-flight refresh (or start one). On success replay the
  // original request; on failure the refresh cookie is dead → bounce to /login.
  return sharedRefresh(http).pipe(
    take(1),
    switchMap((ok) => (ok ? next(req) : bounceToLogin(auth, err))),
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
