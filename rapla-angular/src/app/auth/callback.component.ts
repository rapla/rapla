import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

import { AuthService } from './auth.service';
import { resolveLoginReturnPath } from './login-return-to';

/**
 * OAuth2 redirect landing. The library has already processed the `?code=...`
 * URL params during app bootstrap (see app.config.ts initializer). We just
 * navigate the user onward based on whether a valid access token is present.
 *
 * If the exchange failed (no valid token at this point), increment the
 * failure counter so the /login page knows not to auto-retry — otherwise we
 * end up in an infinite loop: /login → /oauth2/authorize → /callback → /login → …
 * Counter resets on success.
 */
@Component({
  selector: 'app-callback',
  template: `<p style="text-align:center; padding:2rem; font-family:system-ui,sans-serif;">
    Signing in…
  </p>`,
})
export class CallbackComponent implements OnInit {
  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  ngOnInit() {
    // PRD 051 — landing on /callback means a fresh OAuth flow just
    // completed. Any pre-existing impersonation override belongs to
    // the previous session; discard it before continuing or the next
    // outbound request would surprise-impersonate.
    this.auth.endImpersonation();
    if (this.oauth.hasValidAccessToken()) {
      sessionStorage.removeItem('oauthFailures');

      // PRD 035: honour ?returnTo=<key> captured at /app/login. External
      // explorers (GraphiQL, …) sit at non-Angular paths — use a full
      // navigation so the browser leaves the SPA shell.
      const returnTo = sessionStorage.getItem('loginReturnTo');
      sessionStorage.removeItem('loginReturnTo');
      const returnPath = resolveLoginReturnPath(returnTo);
      if (returnPath) {
        window.location.href = returnPath;
        return;
      }

      this.router.navigateByUrl('/reservations');
    } else {
      const prev = Number(sessionStorage.getItem('oauthFailures') ?? '0');
      sessionStorage.setItem('oauthFailures', String(prev + 1));
      this.router.navigateByUrl('/login');
    }
  }
}
