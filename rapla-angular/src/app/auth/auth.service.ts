import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

/**
 * Thin wrapper around angular-oauth2-oidc's OAuthService.
 *
 * The library handles the Authorization Code + PKCE flow against Spring
 * Authorization Server at /oauth2/authorize and /oauth2/token. PRD 031
 * registers /app/auth/callback as a permitted loopback redirect-uri for
 * the `rapla-client` registration in application.yml.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);

  signIn(): void {
    this.oauth.initCodeFlow();
  }

  signOut(): void {
    this.oauth.logOut();
    this.router.navigateByUrl('/login');
  }

  token(): string | null {
    return this.oauth.getAccessToken() || null;
  }

  isLoggedIn(): boolean {
    return this.oauth.hasValidAccessToken();
  }

  identityClaims(): Record<string, unknown> | null {
    return (this.oauth.getIdentityClaims() as Record<string, unknown>) ?? null;
  }
}
