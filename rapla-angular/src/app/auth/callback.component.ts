import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

/**
 * OAuth2 redirect landing. The library has already processed the `?code=...`
 * URL params during app bootstrap (see app.config.ts initializer). We just
 * navigate the user onward based on whether a valid access token is present.
 */
@Component({
  selector: 'app-callback',
  template: `<p style="text-align:center; padding:2rem; font-family:system-ui,sans-serif;">Signing in…</p>`
})
export class CallbackComponent implements OnInit {
  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);

  ngOnInit() {
    if (this.oauth.hasValidAccessToken()) {
      this.router.navigateByUrl('/reservations');
    } else {
      this.router.navigateByUrl('/login');
    }
  }
}
