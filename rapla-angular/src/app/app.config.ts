import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  provideHttpClient,
  withInterceptors,
  withXhr,
  withXsrfConfiguration,
} from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    provideRouter(routes),
    // PRD 072 Phase 4 — cookie-credential model A. CSRF: the server materializes
    // a JS-readable XSRF-TOKEN cookie on GETs; mutating cookie-auth requests must
    // echo it back as X-XSRF-TOKEN. Angular's built-in XSRF interceptor does this
    // automatically — but ONLY for relative / same-origin URLs (it no-ops on
    // absolute cross-origin URLs), so all /api calls use relative paths.
    provideHttpClient(
      withXhr(),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([authInterceptor]),
    ),
    // Load the current identity once at startup from GET /api/auth/me (the SPA's
    // source of login state — replaces the old client-side JWT decode). On a
    // valid access_token cookie this populates AuthService.identity; on 401 it
    // resolves null and the authGuard bounces to the server /login page.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      return auth.loadIdentity();
    }),
  ],
};
