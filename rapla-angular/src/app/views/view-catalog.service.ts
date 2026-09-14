import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { GraphqlService } from '../graphql/graphql.service';

export type ViewSource = 'BUILTIN' | 'CUSTOM';

export interface ViewInfo {
  name: string;
  title: string | null;
  source: ViewSource;
}

const LIST_VIEWS_QUERY = `{ listViews { name title source valid } }`;

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

/** CUSTOM views first (the hand-authored showcase, e.g. Wochenansicht), then BUILTIN; stable within. */
export function orderViews(views: ViewInfo[]): ViewInfo[] {
  return [...views].sort(
    (a, b) => (a.source === 'CUSTOM' ? 0 : 1) - (b.source === 'CUSTOM' ? 0 : 1),
  );
}
