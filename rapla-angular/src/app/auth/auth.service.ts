import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { type Observable, firstValueFrom } from 'rxjs';

import { UnsavedChangesService } from '../shell/unsaved-changes';

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
  /** Opaque user id — used for owner-scoped ("my events") queries (PRD 078 §Scope). */
  userId: string;
  username: string;
  name: string;
  admin: boolean;
  roles: string[];
  impersonating: boolean;
  /** The admin actor's username when {@code impersonating}; otherwise null. */
  actor: string | null;
  /** The impersonation target's username when {@code impersonating}; otherwise null. */
  target: string | null;
  /** PRD 118 D8-8 — banner text of a demo instance ({@code rapla.demo.banner}); absent elsewhere. */
  demoBanner?: string | null;
  /** PRD 124 — display language: login choice (raplaLocale cookie), else preference, else server. */
  language?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly unsaved = inject(UnsavedChangesService);

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
   * Explicit user-driven sign-out: POST {@code /api/auth/session/logout} (cookie-auth;
   * Angular's XSRF interceptor attaches X-XSRF-TOKEN), which EXPIRES both the
   * access_token and refresh_token cookies server-side. Then clear local
   * identity and land on {@code /login?logout}. We deliberately do NOT navigate
   * to Spring's {@code /logout} (a POST-only form-login filter that clears only
   * JSESSIONID, not rapla's stateless auth cookies). The navigation runs even
   * if the POST fails so the user is never stuck on a half-signed-out shell.
   *
   * The {@code ?logout} marker makes the server /login page re-prompt at the IdP
   * (SSO links carry {@code ?prompt=login}) so this explicit sign-out is not
   * silently undone by the still-live Keycloak SSO session on the next login.
   */
  async signOut(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/session/logout', null));
    } catch {
      // best-effort: still drop local identity and bounce to /login below.
    }
    this.identity.set(null);
    window.location.assign('/login?logout');
  }

  /**
   * PRD 072 — start impersonation via the cookie-shaped endpoint
   * {@code POST /api/auth/impersonate/switch?target_username=…}. The server
   * validates {@code canAdminUser} and swaps the {@code access_token} cookie
   * for an impersonation JWT. On success the page reloads (R-22: no per-identity
   * cache survives). Returns false on failure (the caller stays as admin), null
   * when the user kept an open draft instead.
   */
  async impersonate(targetUsername: string): Promise<boolean | null> {
    // Self-target → end impersonation rather than mint a self-as-self token.
    if (this.identity()?.username === targetUsername && !this.isImpersonating()) {
      return true;
    }
    return this.switchIdentity(
      this.http.post('/api/auth/impersonate/switch', null, {
        params: { target_username: targetUsername },
      }),
    );
  }

  /**
   * PRD 072 — end impersonation via {@code POST /api/auth/impersonate/end}.
   * The server restores the admin's {@code access_token} cookie; the page reloads.
   * Returns false on failure, null when the user kept an open draft instead.
   */
  async endImpersonation(): Promise<boolean | null> {
    return this.switchIdentity(this.http.post('/api/auth/impersonate/end', null));
  }

  /** Ask about open drafts BEFORE the cookie changes — a cancel after it would leave the
   *  old page talking as the new user — then switch and reload the whole SPA. */
  private async switchIdentity(request: Observable<unknown>): Promise<boolean | null> {
    if (!this.unsaved.confirmDiscard()) return null;
    try {
      await firstValueFrom(request);
    } catch {
      return false;
    }
    this.unsaved.reload();
    return true;
  }
}
