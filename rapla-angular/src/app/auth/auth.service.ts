import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/**
 * PRD 072 Phase 4 — cookie-credential (model A) identity for the SPA.
 *
 * The browser carries the rapla JWT in an HttpOnly {@code access_token} cookie
 * that the server sets at {@code /login} success. JS can NOT read it. The SPA
 * therefore holds NO token: identity and login state come from
 * {@code GET /api/auth/me}, and every API call relies on the cookie being
 * auto-attached same-origin.
 *
 * No {@code angular-oauth2-oidc}, no {@code localStorage}/{@code sessionStorage}
 * token handling — those are gone with H4.
 */
export interface Identity {
  username: string;
  name: string;
  admin: boolean;
  roles: string[];
  impersonating: boolean;
  /** The admin actor's username when {@code impersonating}; otherwise null. */
  actor: string | null;
  /** The impersonation target's username when {@code impersonating}; otherwise null. */
  target: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  /**
   * The current identity, or null when unauthenticated. Populated by
   * {@link loadIdentity} (app initializer + after impersonation changes).
   * `undefined` would mean "not yet loaded"; we collapse that to null —
   * callers treat "no identity" uniformly.
   */
  readonly identity = signal<Identity | null>(null);

  readonly isImpersonating = computed(() => this.identity()?.impersonating ?? false);

  /** Effective username (impersonation target when impersonating, else self). */
  readonly username = computed(() => this.identity()?.username ?? '');

  /** The admin actor's username while impersonating, else ''. */
  readonly actorUsername = computed(() => this.identity()?.actor ?? '');

  /**
   * Fetch the current identity from {@code GET /api/auth/me}. On 401 (no valid
   * cookie) the identity is cleared and the method resolves null — it does NOT
   * redirect; the caller (guard / interceptor) decides whether to bounce to
   * {@code /login}. Resolves the loaded identity (or null) so callers can act
   * on the result without subscribing to the signal.
   */
  async loadIdentity(): Promise<Identity | null> {
    try {
      const me = await firstValueFrom(this.http.get<Identity>('/api/auth/me'));
      this.identity.set(me);
      return me;
    } catch {
      this.identity.set(null);
      return null;
    }
  }

  /** True iff an identity is currently loaded (a valid cookie was seen). */
  isLoggedIn(): boolean {
    return this.identity() !== null;
  }

  /**
   * Redirect the BROWSER to the server-rendered {@code /login} page (combined
   * chooser + optional password form). A full navigation — leaves the SPA
   * shell — so the server can run the OAuth/login flow and set the cookies.
   * Relative URL so the dev proxy keeps it on the proxy origin.
   */
  redirectToLogin(): void {
    window.location.assign('/login');
  }

  /**
   * Explicit user-driven sign-out: POST {@code /api/auth/logout} (cookie-auth;
   * Angular's XSRF interceptor attaches X-XSRF-TOKEN), which EXPIRES both the
   * access_token and refresh_token cookies server-side. Then clear local
   * identity and land on {@code /login}. We deliberately do NOT navigate to
   * Spring's {@code /logout} (a POST-only form-login filter that clears only
   * JSESSIONID, not rapla's stateless auth cookies). The navigation runs even
   * if the POST fails so the user is never stuck on a half-signed-out shell.
   */
  async signOut(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/logout', null));
    } catch {
      // best-effort: still drop local identity and bounce to /login below.
    }
    this.identity.set(null);
    window.location.assign('/login');
  }

  /**
   * PRD 072 — start impersonation via the cookie-shaped endpoint
   * {@code POST /api/auth/impersonate/switch?target_username=…}. The server
   * validates {@code canAdminUser} and swaps the {@code access_token} cookie
   * for an impersonation JWT. On success we reload the identity (now reflecting
   * the target). Returns false on any failure; the caller stays as admin.
   */
  async impersonate(targetUsername: string): Promise<boolean> {
    // Self-target → end impersonation rather than mint a self-as-self token.
    if (this.identity()?.username === targetUsername && !this.isImpersonating()) {
      return true;
    }
    try {
      await firstValueFrom(
        this.http.post('/api/auth/impersonate/switch', null, {
          params: { target_username: targetUsername },
        }),
      );
      await this.loadIdentity();
      return true;
    } catch {
      return false;
    }
  }

  /**
   * PRD 072 — end impersonation via {@code POST /api/auth/impersonate/end}.
   * The server restores the admin's {@code access_token} cookie. We reload the
   * identity (back to the admin). Returns false on failure.
   */
  async endImpersonation(): Promise<boolean> {
    try {
      await firstValueFrom(this.http.post('/api/auth/impersonate/end', null));
      await this.loadIdentity();
      return true;
    } catch {
      return false;
    }
  }
}
