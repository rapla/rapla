import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';

registerLocaleData(localeDe);
import { provideRouter, withComponentInputBinding } from '@angular/router';
import {
  provideHttpClient,
  withInterceptors,
  withXhr,
  withXsrfConfiguration,
} from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter } from '@angular/material/core';

import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';
import { ROW_MENU_PROVIDERS, EventRowMenuProvider } from './views/row-menu';

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'de-DE' },
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    // mat-datepicker/mat-timepicker date plumbing; with LOCALE_ID de-DE this
    // yields d.M.yyyy, Monday-first calendars, and a 24h timepicker list.
    provideNativeDateAdapter(),
    // withComponentInputBinding — binds route params (e.g. :viewName) straight to
    // component input signals, so the generic ViewHost reads its view name as an input.
    provideRouter(routes, withComponentInputBinding()),
    // PRD 094 D3 — row menu providers (the ObjectMenuFactory analog): each
    // provider dispatches on the typed row subject (D4). Add further providers
    // (loan transitions, request workflow) as additional multi entries.
    { provide: ROW_MENU_PROVIDERS, useClass: EventRowMenuProvider, multi: true },
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
