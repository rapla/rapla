import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Attach Bearer token to every outgoing request. On 401 from an application
 * endpoint, clear local tokens and bounce to /login (which re-initiates the
 * OAuth code flow when configured).
 *
 * Skip `/oauth2/*` and `/.well-known/*` — those are the OAuth library's own
 * traffic (code exchange, refresh, discovery). A 401 there means the flow
 * itself failed; redirecting on those would loop us right back into the same
 * failed exchange. The library raises its own OAuthErrorEvent and the
 * callback component's failure counter (see CallbackComponent) handles it.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();

  const authedReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !req.url.includes('/oauth2/') && !req.url.includes('/.well-known/')) {
        auth.handleUnauthenticated();
      }
      return throwError(() => err);
    })
  );
};
