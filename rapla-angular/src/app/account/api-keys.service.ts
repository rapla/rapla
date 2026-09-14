import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * PRD 043 / 076 — client for the server-minted asymmetric API-key registry
 * ({@code /api/auth/api-keys}). Cookie-auth: the browser auto-attaches the
 * {@code access_token} cookie same-origin, Angular's XSRF interceptor adds
 * {@code X-XSRF-TOKEN} on the mutating calls (all relative URLs).
 *
 * The {@code key} (the bearer JWT) is returned ONLY by {@link create} and
 * {@link rotate} — it is shown once and never retrievable again (the server
 * keeps the public half only). {@link list} returns metadata without it.
 */
export interface ApiKeyMetadata {
  id: string;
  label: string | null;
  alg: string;
  thumbprint: string;
  createdAt: string;
  expiresAt: string | null;
  scopes: string[];
}

/** {@link create}/{@link rotate} response — carries the one-time {@code key}. */
export interface ApiKeyCreated extends ApiKeyMetadata {
  key: string;
}

export interface CreateApiKeyRequest {
  label: string;
  expiresInDays: number | null;
  scopes: string[];
}

/** The scope vocabulary the server accepts (mirrors {@code ApiKeyScopes}). */
export const API_KEY_SCOPES = [
  'read',
  'access_details',
  'write_events',
  'write_resources',
  'write_all',
  'rotate_self',
] as const;
export type ApiKeyScope = (typeof API_KEY_SCOPES)[number];

@Injectable({ providedIn: 'root' })
export class ApiKeysService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/auth/api-keys';

  list(): Observable<ApiKeyMetadata[]> {
    return this.http.get<ApiKeyMetadata[]>(this.base);
  }

  create(req: CreateApiKeyRequest): Observable<ApiKeyCreated> {
    return this.http.post<ApiKeyCreated>(this.base, req);
  }

  /**
   * Rotate a key (PRD 076 Phase 6 / D12): the server mints a fresh same-scope successor and
   * grace-expires the old one. {@code graceMinutes} keeps the old key valid for the overlap
   * window (server default 180, hard cap 2 days → 400). Returns the new key once.
   */
  rotate(id: string, graceMinutes: number): Observable<ApiKeyCreated> {
    return this.http.post<ApiKeyCreated>(`${this.base}/${encodeURIComponent(id)}/rotate`, null, {
      params: { graceMinutes },
    });
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }
}
