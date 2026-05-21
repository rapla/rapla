import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { catchError, from, Observable, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import {
  AuthErrorDialogComponent,
  AuthErrorDialogData,
} from './auth-error-dialog.component';

/**
 * Attach Bearer token to every outgoing request EXCEPT pre-authentication
 * paths. On 401 with a Bearer attached:
 *   1. attempt a refresh_token grant (Keycloak offline_access path)
 *   2. on success, replay the original request with the new token
 *   3. on failure (or no refresh_token available), show a modal dialog
 *      explaining the rejection, then route to /login when dismissed
 *
 * The dialog deliberately blocks so the user never silently lands on /login
 * without knowing why their session ended.
 *
 * Pre-authentication paths — Bearer is NOT attached and a 401 there does NOT
 * trigger any of the above:
 *   - /oauth2/*            — Spring AS endpoints (authorize/token/jwks)
 *   - /.well-known/*       — OIDC discovery
 *   - /api/auth/oauth/*    — rapla OAuth helpers: BFF token-exchange and
 *                            /config discovery. These endpoints are where a
 *                            token is obtained; attaching a stale Bearer
 *                            causes the resource-server JWT filter to reject
 *                            the request with 401 before the controller runs
 *                            ("stale-JWT-blocks-OAuth-login" regression,
 *                            2026-05-21).
 *
 * The library raises its own OAuthErrorEvent for OAuth-flow failures and the
 * callback component's failure counter (see CallbackComponent) handles them.
 */
const isPreAuthRequest = (req: HttpRequest<unknown>): boolean =>
  req.url.includes('/oauth2/') ||
  req.url.includes('/.well-known/') ||
  req.url.includes('/api/auth/oauth/');

/**
 * Build a user-facing dialog payload from a 401 response. Tries (in order):
 *   1. JSON body's `error_description` / `message` / `error`
 *   2. WWW-Authenticate header's `error_description`
 *   3. Plain status text
 * The raw body is exposed as `detail` so the user / support can copy it.
 */
function buildDialogData(err: HttpErrorResponse): AuthErrorDialogData {
  const bodyError =
    err.error && typeof err.error === 'object'
      ? err.error.error_description || err.error.message || err.error.error
      : null;

  let wwwAuth: string | null = null;
  const wwwAuthHeader = err.headers?.get?.('WWW-Authenticate');
  if (wwwAuthHeader) {
    const m = /error_description="([^"]+)"/.exec(wwwAuthHeader);
    if (m) wwwAuth = m[1];
  }

  const message =
    bodyError ||
    wwwAuth ||
    'Your sign-in was accepted by the identity provider, but rapla could not link it to a user account. Contact your administrator.';

  let detail: string | undefined;
  if (typeof err.error === 'string' && err.error.trim().length > 0) {
    detail = err.error;
  } else if (err.error && typeof err.error === 'object') {
    try {
      detail = JSON.stringify(err.error, null, 2);
    } catch {
      /* ignore */
    }
  }

  return {
    title: 'Sign-in rejected',
    message,
    detail,
  };
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const dialog = inject(MatDialog);
  const token = auth.token();
  const preAuth = isPreAuthRequest(req);
  const bearerAttached = !!(token && !preAuth);

  const authedReq = bearerAttached
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  const openDialogAndBounce = (err: HttpErrorResponse): Observable<never> => {
    const data = buildDialogData(err);
    const ref = dialog.open(AuthErrorDialogComponent, {
      data,
      disableClose: false,
      autoFocus: true,
      width: '480px',
    });
    return ref.afterClosed().pipe(
      switchMap(() => {
        auth.handleAuthRejection();
        return throwError(() => err);
      }),
    );
  };

  // Capture impersonation state at the time the request was built so
  // the catchError below can tell "was this 401 for the impersonation
  // Bearer or for the admin's own?" — the renewal path is different.
  const wasImpersonating = auth.isImpersonating();

  return next(authedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || preAuth) {
        return throwError(() => err);
      }
      if (!bearerAttached) {
        // 401 on a request with no Bearer attached → the user isn't logged
        // in yet (or token was already cleared). Quietly route to /login.
        auth.handleUnauthenticated();
        return throwError(() => err);
      }

      // PRD 051 — 401 with the impersonation Bearer attached. Try to
      // renew the impersonation token first (it's the short-lived one
      // most likely to have expired). On success, replay. On failure
      // (admin's Bearer also stale), fall into the admin-refresh chain,
      // then re-attempt the impersonation renewal, then replay.
      if (wasImpersonating) {
        return from(auth.renewImpersonation()).pipe(
          switchMap((renewed) => {
            if (renewed) {
              return replayWith(auth.token());
            }
            // Renewal failed — admin's access token is probably also
            // stale. Refresh admin, then re-attempt impersonation, then
            // replay (or surface the failure via dialog).
            return from(auth.refreshAccessToken()).pipe(
              switchMap((adminRefreshed) => {
                if (!adminRefreshed) return openDialogAndBounce(err);
                return from(auth.renewImpersonation()).pipe(
                  switchMap((retryRenewed) => {
                    if (!retryRenewed) return openDialogAndBounce(err);
                    return replayWith(auth.token());
                  }),
                );
              }),
            );
          }),
        );
      }

      // Standard 401 with admin Bearer attached: refresh then replay.
      // The replay is called via `next(...)` directly — it does NOT
      // re-enter this interceptor — so a 401 on the replay is caught
      // by the inner catchError, never a second refresh.
      return from(auth.refreshAccessToken()).pipe(
        switchMap((refreshed) => {
          if (!refreshed) {
            return openDialogAndBounce(err);
          }
          return replayWith(auth.token());
        }),
      );

      function replayWith(token: string | null): Observable<HttpEvent<unknown>> {
        const replay = token
          ? authedReq.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
          : authedReq;
        return next(replay).pipe(
          catchError((replayErr: HttpErrorResponse) => {
            if (replayErr.status === 401) {
              return openDialogAndBounce(replayErr);
            }
            return throwError(() => replayErr);
          }),
        );
      }
    }),
  );
};
