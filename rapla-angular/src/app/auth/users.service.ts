import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * Narrow user DTO returned by {@code GET /api/users}. Mirrors the server-side
 * {@code UserSummary} shape (PRD 051). Only the two fields the SPA's "Switch to
 * user" dialog needs — no email, no groups, no preferences (AGENTS.md §12).
 */
export interface UserSummary {
  username: string;
  displayName: string;
}

/**
 * PRD 072 Phase 4 — cookie-credential client for {@code GET /api/users}.
 *
 * Under the cookie model the SPA holds no token: the request goes through
 * {@link HttpClient}, the browser auto-attaches the {@code access_token} cookie
 * same-origin, and the server filters to the caller's {@code canAdminUser}
 * scope. (Previously this issued a raw {@code fetch} with the admin Bearer to
 * dodge the impersonation override; there is no client-held token anymore.)
 *
 * On any non-2xx the list is empty — the SPA treats "can't fetch" the same as
 * "can't admin": the toolbar chip stays non-clickable.
 */
@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);

  list(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>('/api/users').pipe(
      catchError((err) => {
        console.warn('[users] GET /api/users failed:', err);
        return of([] as UserSummary[]);
      }),
    );
  }
}
