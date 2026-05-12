import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Attach Bearer token to every outgoing request. On 401, sign out and bounce
 * to /login. The OAuthService refreshes tokens silently in the background;
 * we treat a 401 as definitive sign-out.
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
        auth.signOut();
      }
      return throwError(() => err);
    })
  );
};
