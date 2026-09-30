import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import {
  provideHttpClient,
  withInterceptors,
  withXhr,
  withXsrfConfiguration,
} from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';

import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { buildCheckInterceptor } from './shell/build-check';
import { AuthService } from './auth/auth.service';
import { I18nService, localeId } from './i18n/i18n.service';
import { RaplaDatepickerIntl } from './i18n/datepicker-intl';
import { ROW_MENU_PROVIDERS, EventRowMenuProvider } from './views/row-menu';
import { DOCUMENT_CATALOG, DocumentsRowMenuProvider } from './views/document-menu';
import { DocumentCatalogStore } from './views/document-catalog.store';

export const appConfig: ApplicationConfig = {
  providers: [
    // PRD 124 — the user's language from the server catalogue (de-DE until/unless loaded).
    // Evaluated once, after the initializers: inject nothing that reads LOCALE_ID before
    // i18n.load(), or it freezes de-DE. A language switch needs a reload.
    { provide: LOCALE_ID, useFactory: localeId },
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    // mat-datepicker/mat-timepicker date plumbing; with LOCALE_ID de-DE this
    // yields d.M.yyyy, Monday-first calendars, and a 24h timepicker list.
    provideNativeDateAdapter(),
    { provide: MatDatepickerIntl, useClass: RaplaDatepickerIntl },
    // withComponentInputBinding — binds route params (e.g. :viewName) straight to
    // component input signals, so the generic ViewHost reads its view name as an input.
    provideRouter(routes, withComponentInputBinding()),
    // PRD 094 D3 — row menu providers (the ObjectMenuFactory analog): each
    // provider dispatches on the typed row subject (D4). Add further providers
    // (loan transitions, request workflow) as additional multi entries.
    { provide: ROW_MENU_PROVIDERS, useClass: EventRowMenuProvider, multi: true },
    // PRD 111 D4 — the documents feature contributes its own row entries.
    { provide: DOCUMENT_CATALOG, useExisting: DocumentCatalogStore },
    { provide: ROW_MENU_PROVIDERS, useClass: DocumentsRowMenuProvider, multi: true },
    // PRD 072 Phase 4 — cookie-credential model A. CSRF: the server materializes
    // a JS-readable XSRF-TOKEN cookie on GETs; mutating cookie-auth requests must
    // echo it back as X-XSRF-TOKEN. Angular's built-in XSRF interceptor does this
    // automatically — but ONLY for relative / same-origin URLs (it no-ops on
    // absolute cross-origin URLs), so all /api calls use relative paths.
    provideHttpClient(
      withXhr(),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([buildCheckInterceptor, authInterceptor]),
    ),
    // Load the current identity once at startup from GET /api/auth/me (the SPA's
    // source of login state — replaces the old client-side JWT decode). On a
    // valid access_token cookie this populates AuthService.identity; on 401 it
    // resolves null and the authGuard bounces to the server /login page.
    provideAppInitializer(async () => {
      const auth = inject(AuthService);
      const i18n = inject(I18nService);
      if (await auth.loadIdentity()) await i18n.load();
    }),
  ],
};
