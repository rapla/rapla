import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * PRD 078 — the SPA's entire GraphQL transport. A plain {@link HttpClient}
 * POST to {@code /api/graphql}; the cookie-credential model (PRD 072 Phase 4)
 * means the browser auto-attaches the HttpOnly {@code access_token} cookie
 * same-origin, so there is NO {@code Authorization} header here and the
 * existing {@code auth.interceptor} handles 401→refresh→replay. No Apollo.
 */

/** A single GraphQL error entry (per the spec's {@code errors[]} shape). */
export interface GqlError {
  message: string;
  path?: (string | number)[];
  extensions?: { code?: string; [k: string]: unknown };
}

/**
 * PRD 074 render-meta carried on {@code extensions.view}. Emitted by the
 * server iff the executed query carries the {@code @view} directive. Until
 * that lands the renderer falls back to a code-shipped {@link ViewMeta}
 * constant — so this type is the forward contract, hand-written because
 * runtime {@code extensions} are not part of the GraphQL schema (no codegen).
 */
export interface ViewColumn {
  alias: string;
  header: string;
  /** GraphQL-ish type hint driving formatting: {@code String}, {@code LocalDateTime}, {@code Allocatable}, … */
  type?: string;
  /** Explicit column position (server-emitted). When present, columns sort by it; ties keep array order. */
  order?: number;
  sort?: 'ASC' | 'DESC';
  /** List-cell join separator; defaults to {@code ', '}. */
  join?: string;
  hidden?: boolean;
  /** Grouping bucket key (e.g. {@code DAY}) — Phase 3. */
  group?: string;
}

export interface ViewMeta {
  key: string;
  title?: string;
  columns: ViewColumn[];
}

export interface GqlResponse<T> {
  data?: T;
  errors?: GqlError[];
  extensions?: { view?: ViewMeta; [k: string]: unknown };
}

@Injectable({ providedIn: 'root' })
export class GraphqlService {
  private readonly http = inject(HttpClient);

  /**
   * Execute a GraphQL document with variables. Returns the raw envelope
   * ({@code data} + {@code errors} + {@code extensions}) — callers decide how
   * to treat partial {@code errors}; a transport failure (non-2xx) surfaces as
   * the Observable's error channel, handled by the refresh interceptor.
   */
  query<T>(document: string, variables: Record<string, unknown> = {}): Observable<GqlResponse<T>> {
    return this.http.post<GqlResponse<T>>('/api/graphql', { query: document, variables });
  }
}
