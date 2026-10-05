import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { GraphqlService, type ViewRenderMode } from '../graphql/graphql.service';

export type ViewSource = 'BUILTIN' | 'CUSTOM';

export interface ViewInfo {
  name: string;
  title: string | null;
  source: ViewSource;
  /** PRD 128 D7 — what the left pane picks from (`@view(selection:)`); absent = the resource picker. */
  selection?: 'CONFLICTS' | 'REQUESTS' | null;
  /** PRD 128 D7 — the mode the view opens in until one is chosen there. */
  defaultRenderMode?: ViewRenderMode | null;
}

const LIST_VIEWS_QUERY = `{ listViews { name title source valid selection defaultRenderMode } }`;

/**
 * Reads the view catalog from the server (PRD 074 {@code listViews}) — the
 * source the shell nav builds from. Replaces the hardcoded NAV_ITEMS seam: views
 * are now stored server-side (BUILTIN + permitted CUSTOM), §12-scoped.
 */
@Injectable({ providedIn: 'root' })
export class ViewCatalogService {
  private readonly gql = inject(GraphqlService);

  listViews(): Observable<ViewInfo[]> {
    return this.gql
      .query<{ listViews: (ViewInfo & { valid: boolean })[] }>(LIST_VIEWS_QUERY)
      .pipe(map((res) => orderViews((res.data?.listViews ?? []).filter((v) => v.valid))));
  }
}

/** Planning views first, then the stored (CUSTOM) ones, the Prüfen views (conflicts / requests) last; stable within. */
export function orderViews(views: ViewInfo[]): ViewInfo[] {
  const rank = (v: ViewInfo) => (v.selection ? 2 : v.source === 'BUILTIN' ? 0 : 1);
  return [...views].sort((a, b) => rank(a) - rank(b));
}

/** PRD 128 D7 — the left pane's source for a view: the resource picker, or the conflict / request tree. */
export type SelectionSource = 'resources' | 'conflicts' | 'requests';

export function selectionOf(view: ViewInfo | undefined): SelectionSource {
  return view?.selection === 'CONFLICTS'
    ? 'conflicts'
    : view?.selection === 'REQUESTS'
      ? 'requests'
      : 'resources';
}
