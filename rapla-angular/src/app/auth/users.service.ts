import { Injectable, inject } from '@angular/core';
import { Observable, from, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from './auth.service';

/**
 * Narrow user DTO returned by {@code GET /api/users}. Mirrors the
 * server-side {@code UserSummary} shape (PRD 051). Only the two
 * fields needed by the SPA's "Switch to user" dialog — no email,
 * no groups, no preferences (per AGENTS.md §12).
 */
export interface UserSummary {
  username: string;
  displayName: string;
}

/**
 * PRD 051 — typed client for {@code GET /api/users}. Hand-rolled (in
 * `src/app/auth/`, not `src/app/api/`) so it can ship before the
 * OpenAPI codegen catches up; once the regenerated client lands the
 * call sites can swap to it without touching the dialog component.
 *
 * <p><b>Uses the admin's Bearer, NOT the impersonation override.</b>
 * The server-side filter runs {@code canAdminUser} against the
 * authenticated caller; if we sent the impersonation Bearer while
 * the admin was acting as alice, the server would compute "users
 * alice can admin" (typically empty) instead of "users the admin can
 * admin". Documented contract: impersonation tokens cannot themselves
 * see the impersonate list — only the original admin Bearer can.
 * See {@code docs/authentication.md} § "Admin impersonation".
 *
 * <p>That's why this service issues raw {@code fetch} calls with an
 * explicit {@code Authorization} header rather than going through
 * {@code HttpClient} (which would let the auth interceptor attach
 * {@code auth.token()} — i.e. the impersonation override when active).
 */
@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly auth = inject(AuthService);

  /**
   * Returns users the caller can {@code canAdminUser} over, including
   * self. Always uses the admin Bearer, so the result is the admin's
   * admin-scope regardless of any active impersonation. Empty array
   * = the admin has no admin authority (or there's no admin Bearer
   * available). On any non-2xx response, returns empty rather than
   * rejecting — the SPA treats "can't fetch" the same as "can't
   * admin": chip stays non-clickable. Errors logged for diagnostics.
   */
  list(): Observable<UserSummary[]> {
    return from(this.fetchAdminScope()).pipe(
      catchError((err) => {
        console.warn('[users] GET /api/users failed:', err);
        return of([] as UserSummary[]);
      }),
    );
  }

  private async fetchAdminScope(): Promise<UserSummary[]> {
    const adminBearer = this.auth.adminToken();
    if (!adminBearer) return [];
    const resp = await fetch('/api/users', {
      method: 'GET',
      headers: {
        Authorization: 'Bearer ' + adminBearer,
        Accept: 'application/json',
      },
    });
    if (!resp.ok) return [];
    return (await resp.json()) as UserSummary[];
  }
}
