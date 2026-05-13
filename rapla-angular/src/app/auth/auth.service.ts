import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthErrorEvent, OAuthEvent, OAuthService } from 'angular-oauth2-oidc';

/**
 * Thin wrapper around angular-oauth2-oidc's OAuthService.
 *
 * The library handles the Authorization Code + PKCE flow against Spring
 * Authorization Server at /oauth2/authorize and /oauth2/token. PRD 031
 * registers /app/auth/callback as a permitted loopback redirect-uri for
 * the `rapla-client` registration in application.yml.
 *
 * `lastOAuthError` captures the most recent OAuthErrorEvent so the UI can
 * surface what `/oauth2/token` actually said (invalid_grant, redirect_uri
 * mismatch, etc.) rather than just "no token".
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);

  private redirecting = false;

  readonly lastOAuthError = signal<string | null>(null);

  constructor() {
    this.oauth.events.subscribe((evt: OAuthEvent) => {
      if (evt instanceof OAuthErrorEvent) {
        const reason = describeOAuthError(evt);
        this.lastOAuthError.set(reason);
        console.error('[oauth]', evt.type, evt);
      } else if (evt.type === 'token_received' || evt.type === 'token_refreshed') {
        this.lastOAuthError.set(null);
      }
    });
  }

  signIn(): void {
    if (this.redirecting) return;
    this.redirecting = true;
    this.lastOAuthError.set(null);
    this.oauth.initCodeFlow();
  }

  /**
   * Same as signIn() but adds `prompt=login` so Spring Authorization Server
   * forces a fresh credential prompt even if it has a valid session cookie.
   * Use this to break out of "silent re-redirect issues codes that won't
   * exchange" — e.g. stale Spring session.
   */
  signInPromptLogin(): void {
    if (this.redirecting) return;
    this.redirecting = true;
    this.lastOAuthError.set(null);
    this.oauth.initCodeFlow(undefined, { prompt: 'login' });
  }

  /**
   * Explicit user-driven sign-out: hit the IdP's end-session URL if available,
   * then land on /login. Use this for the toolbar "Sign out" button.
   *
   * `oauth.logOut()` clears local-storage tokens and triggers a top-level
   * `window.location` navigation to /connect/logout?...&post_logout_redirect_uri=/app/.
   * Spring AS terminates its session cookie and 302s back to /app/, where the
   * route guard bounces unauthenticated to /login. Do NOT call
   * `router.navigateByUrl('/login')` here — a same-origin SPA route change
   * after `oauth.logOut()` aborts the in-flight cross-origin nav, the AS
   * session survives, and the next /oauth2/authorize silently re-auths.
   */
  signOut(): void {
    this.oauth.logOut();
  }

  /**
   * 401 path: token expired, refresh failed, or the server kicked us out.
   * Clear local state without redirecting to the IdP's end-session endpoint
   * (the session is already dead server-side), then navigate to /login where
   * the auto-redirect to /oauth2/authorize takes over.
   */
  handleUnauthenticated(): void {
    this.oauth.logOut(true);
    this.router.navigateByUrl('/login');
  }

  token(): string | null {
    return this.oauth.getAccessToken() || null;
  }

  isLoggedIn(): boolean {
    return this.oauth.hasValidAccessToken();
  }

  /**
   * True once the app initializer in app.config.ts has called oauth.configure().
   * If the backend has rapla.oauth.enabled=false, this stays false and the
   * /login page falls back to its manual button.
   */
  isOAuthConfigured(): boolean {
    return Boolean(this.oauth.loginUrl);
  }

  identityClaims(): Record<string, unknown> | null {
    return (this.oauth.getIdentityClaims() as Record<string, unknown>) ?? null;
  }
}

function describeOAuthError(evt: OAuthErrorEvent): string {
  const reason = (evt as unknown as { reason?: unknown }).reason;
  let detail = '';
  if (reason && typeof reason === 'object') {
    const r = reason as Record<string, unknown>;
    const err = r['error'] ?? r['error_description'];
    const status = r['status'];
    const url = r['url'];
    if (err) detail += ` ${err}`;
    if (status) detail += ` (HTTP ${status})`;
    if (url) detail += ` from ${url}`;
  } else if (typeof reason === 'string') {
    detail = ` ${reason}`;
  }
  return `${evt.type}${detail}`.trim();
}
