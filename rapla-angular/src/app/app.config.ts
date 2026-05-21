import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { OAuthService, provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { BASE_PATH } from './api/variables';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService, OAuthDiscovery } from './auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    // BASE_PATH stays empty: SpringDoc emits absolute /api/... paths via the
    // WebMvcConfigurer prefix, so the generated client + interceptor pair
    // produces correct same-origin URLs without extra prefixing.
    { provide: BASE_PATH, useValue: '' },
    provideOAuthClient(),
    provideAppInitializer(() => {
      const oauth = inject(OAuthService);
      const authService = inject(AuthService);
      // Persist tokens across page reloads (default is sessionStorage which
      // clears on tab close; localStorage survives until explicit logout).
      oauth.setStorage(localStorage);
      // Endpoint set comes from /api/auth/oauth/config so the SPA is auth-server
      // agnostic. The `providers[]` + `picker` fields (PRD 036) let the SPA
      // render a multi-IdP picker on /login when more than one provider is
      // enabled. Top-level flat fields always reflect the rapla embedded SAS.
      const origin = window.location.origin;
      return fetch(origin + '/api/auth/oauth/config')
        .then((r) => (r.ok ? r.json() : Promise.reject(`oauth config http ${r.status}`)))
        .then((cfg: OAuthDiscovery) => {
          if (!cfg.enabled) return undefined;
          authService.setDiscovery(cfg);
          // Configure OAuthService with the user's most recent picker choice
          // (persisted in localStorage), falling back to the primary provider.
          // This is critical for the post-callback reload: when the browser
          // comes back to /app/auth/callback?code=… after the IdP redirect,
          // tryLoginCodeFlow() must exchange the code at the SAME token
          // endpoint the authorize was issued against, not the default one.
          const active = authService.activeProvider();
          authService.applyProviderToOAuthService(active, cfg);
          // Load the JWKS for the active provider so id_token signature
          // verification uses the right keys.
          const jwksUrl = active?.jwksUrl ?? cfg.jwksUrl;
          return fetch(jwksUrl)
            .then((r) => (r.ok ? r.json() : null))
            .then((jwks) => {
              if (jwks) (oauth as unknown as { jwks: unknown }).jwks = jwks;
            })
            .then(() => oauth.tryLoginCodeFlow())
            .then(() => {
              // Proactive refresh: if a valid token is present (either fresh
              // from this callback or carried over from a prior tab),
              // schedule the library's auto-silent-refresh so subsequent
              // expiries are handled invisibly. No-op when no token is
              // present (e.g. user hasn't signed in yet).
              if (oauth.hasValidAccessToken()) {
                authService.enableAutomaticSilentRefresh();
              }
            });
        })
        .catch((err) => {
          console.warn('OAuth init failed; SPA will require manual auth via /login:', err);
        });
    }),
  ],
};
