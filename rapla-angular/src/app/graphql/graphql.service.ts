import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import type { ViewInput } from '../views/view-inputs';

/**
 * PRD 078 — the SPA's entire GraphQL transport. A plain {@link HttpClient}
 * POST to {@code /api/graphql}; the cookie-credential model (PRD 072 Phase 4)
 * means the browser auto-attaches the HttpOnly {@code access_token} cookie
 * same-origin, so there is NO {@code Authorization} header here and the
 * existing {@code auth.interceptor} handles 401→refresh→replay. No Apollo.
 */

/** Mirrors the server {@code ViewRenderMode} enum — the set of modes a view can switch between. */
export type ViewRenderMode = 'table' | 'week' | 'month' | 'day' | 'program';

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
  /** Either the boolean grouping marker (flat views) OR the group alias an
   *  {@code entity} column belongs to (aggregation/pivot views). */
  group?: boolean | string;
  /** Opaque, client-interpreted date-format token (e.g. {@code "EE dd.MM"}). */
  format?: string;
  /** Aggregation column kind: {@code group} | {@code entity} | {@code value} | {@code count}. */
  kind?: string;
  /** Entity attribute path within the group entity (e.g. {@code "Gebaeude.name"}). */
  path?: string;
  /** Aggregate function for a {@code value} column ({@code SUM} | {@code COUNT} | …). */
  fn?: string;
}

/** One operation variable of the stored view — the binding contract: the GUI
 *  fills each variable from ambient state BY TYPE (ReservationFilter ← window +
 *  selection, AllocatableFilter ← selection, …). */
export interface ViewVariable {
  name: string;
  /** GraphQL type name, e.g. {@code "ReservationFilter!"}, {@code "AllocatableFilter!"}. */
  type: string;
}

export interface ViewMeta {
  key: string;
  title?: string;
  columns: ViewColumn[];
  /** Alias of the column to group rows by (server render-info); absent → flat table. */
  groupBy?: string;
  /** Opaque date-format token for the GROUP header (client-interpreted, e.g. {@code "EE dd.MM"}). */
  groupFormat?: string;
  /** PRD 074 — input-control metadata (date-range anchor/offset defaults). */
  inputs?: ViewInput[];
  /** Singular|plural label for the row count line, e.g. "Termin|Termine". Falls back to "Eintrag|Einträge". */
  rowLabel?: string;
  /** Singular|plural label for the group count, e.g. "Tag|Tage". When set, a secondary "· N Tag(e)" is shown. */
  groupLabel?: string;
  /** Render modes supported by this view — mirrors the server {@code ViewRenderMode} enum. Default: {@code ["table"]}. */
  renderModes?: ViewRenderMode[];
  /** The operation's variable signature — the type-driven binding contract. */
  variables?: ViewVariable[];
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

  /**
   * PRD 074/078 consumer path — execute a STORED view by name. The client holds
   * NO query text: the {@code storedView} extension flag + {@code operationName}
   * tell the server's StoredViewInterceptor to swap in the stored query and
   * merge variable defaults. The dummy {@code query} satisfies GraphQL's
   * required field; the interceptor replaces it.
   */
  executeView<T>(
    viewName: string,
    variables: Record<string, unknown> = {},
  ): Observable<GqlResponse<T>> {
    return this.http.post<GqlResponse<T>>('/api/graphql', {
      operationName: viewName,
      query: '{ __typename }',
      variables,
      extensions: { storedView: true },
    });
  }
}
