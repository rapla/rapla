import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { OAuthService, provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { BASE_PATH } from './api/variables';
import { authInterceptor } from './auth/auth.interceptor';

interface OAuthEndpoints {
  enabled: boolean;
  clientId: string;
  issuer: string;
  authorizeUrl: string;
  tokenUrl: string;
  refreshUrl: string;
  logoutUrl: string;
  jwksUrl: string;
  userinfoUrl: string;
  endSessionUrl: string;
  scopes: string[];
}

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
      // Persist tokens across page reloads (default is sessionStorage which
      // clears on tab close; localStorage survives until explicit logout).
      oauth.setStorage(localStorage);
      // Endpoint set comes from /api/auth/oauth/config so the SPA is auth-server
      // agnostic — swapping the bundled Spring Authorization Server for Keycloak
      // / Auth0 / etc. is a server-side property change (rapla.oauth.*-url), no
      // SPA rebuild needed. In dev (`ng serve` on :4200 proxying to :8051), the
      // server returns :4200 URLs thanks to server.forward-headers-strategy=FRAMEWORK
      // + the proxy's xfwd: true, so the OAuth library's calls stay same-origin
      // and flow through the proxy. In prod or with external IdP, URLs are returned
      // as-is and the library reaches them directly.
      const origin = window.location.origin;
      return fetch(origin + '/api/auth/oauth/config')
        .then(r => r.ok ? r.json() : Promise.reject(`oauth config http ${r.status}`))
        .then((cfg: OAuthEndpoints) => {
          if (!cfg.enabled) return undefined;
          oauth.configure({
            issuer: cfg.issuer,
            clientId: cfg.clientId,
            redirectUri: origin + '/app/auth/callback',
            responseType: 'code',
            scope: (cfg.scopes ?? ['openid', 'profile', 'offline_access']).join(' '),
            loginUrl: cfg.authorizeUrl,
            tokenEndpoint: cfg.tokenUrl,
            userinfoEndpoint: cfg.userinfoUrl,
            logoutUrl: cfg.endSessionUrl ?? cfg.logoutUrl,
            postLogoutRedirectUri: origin + '/app/',
            showDebugInformation: false,
            skipIssuerCheck: true,
            strictDiscoveryDocumentValidation: false,
          });
          return fetch(cfg.jwksUrl)
            .then(r => r.ok ? r.json() : null)
            .then(jwks => { if (jwks) (oauth as unknown as { jwks: unknown }).jwks = jwks; })
            .then(() => oauth.tryLoginCodeFlow());
        })
        .catch(err => {
          console.warn('OAuth init failed; SPA will require manual auth via /login:', err);
        });
    }),
  ]
};
