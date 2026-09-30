import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, shareReplay, switchMap } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
import {
  categoryTreeSelection,
  flattenCategoryTree,
  normalizeClassificationValues,
  parseClassificationSdl,
  valueSelections,
  type CategoryNode,
  type CategoryRow,
  type ClassificationType,
} from './classification-schema';

/**
 * PRD 096 Phase 1 — one SDL fetch per session, parsed once, cached.
 * The schema hot-swaps on DynamicType admin changes (~10 s server-side);
 * a stale descriptor surfaces as a server-side validation error on save,
 * same acceptance as PRD 035 §4 ("stale descriptors surface as L2
 * rejection") — no polling client-side.
 */
@Injectable({ providedIn: 'root' })
export class ClassificationSchemaService {
  private readonly http = inject(HttpClient);
  private readonly gql = inject(GraphqlService);

  private types$: Observable<Map<string, ClassificationType>> | null = null;
  private readonly prototypes = new Map<string, Observable<Record<string, unknown> | null>>();
  private readonly trees = new Map<string, Observable<CategoryRow[]>>();

  /** PRD 096 Phase 5 — sync view of the loaded tree-category subtrees, keyed by root path. */
  readonly categoryRows = signal<ReadonlyMap<string, CategoryRow[]>>(new Map());

  /** Sync view of the parsed schema; null until the first load completes. */
  readonly typeMap = signal<Map<string, ClassificationType> | null>(null);

  load(): Observable<Map<string, ClassificationType>> {
    if (!this.types$) {
      this.types$ = this.http.get('/api/graphql/schema', { responseType: 'text' }).pipe(
        map((sdl) => parseClassificationSdl(sdl)),
        shareReplay(1),
      );
      this.types$.subscribe((m) => this.typeMap.set(m));
    }
    return this.types$;
  }

  /**
   * PRD 099 / PRD 122 — the server-computed birth values of a new entity of this
   * type (`reservationPrototype` / `resourcePrototype` query by kind: attribute defaults via
   * `newClassification()`, nothing persisted). Cached per typeKey with the
   * SDL cache's lifetime — one consistent schema snapshot per JS context.
   * Emits null for unknown/non-creatable types, types without attributes and
   * on transport errors (errors are NOT cached).
   */
  prototype(typeKey: string): Observable<Record<string, unknown> | null> {
    let p$ = this.prototypes.get(typeKey);
    if (!p$) {
      p$ = this.load().pipe(
        switchMap((types) => {
          const type = types.get(typeKey);
          if (!type || type.attributes.length === 0) {
            return of(null);
          }
          const field = type.kind === 'RESERVATION' ? 'reservationPrototype' : 'resourcePrototype';
          const fragment = `... on ${typeKey}Classification { ${valueSelections(type.attributes)} }`;
          return this.gql
            .query<
              Record<string, { classification: Record<string, unknown> } | null>
            >(`query ($k: String!) { ${field}(typeKey: $k) { classification { ${fragment} } } }`, { k: typeKey })
            .pipe(
              map((resp) => {
                const cls = resp.data?.[field]?.classification;
                return cls ? normalizeClassificationValues(cls) : null;
              }),
            );
        }),
        catchError(() => {
          this.prototypes.delete(typeKey);
          return of(null);
        }),
        shareReplay(1),
      );
      this.prototypes.set(typeKey, p$);
    }
    return p$;
  }

  /** PRD 096 Phase 5 — the subtree below a `@rootCategory` path, flattened; loaded once per path. */
  categoryTree(rootPath: string): Observable<CategoryRow[]> {
    let t$ = this.trees.get(rootPath);
    if (!t$) {
      t$ = this.gql
        .query<{
          category: { children: CategoryNode[] } | null;
        }>(`query ($p: String!) { category(path: $p) { ${categoryTreeSelection()} } }`, {
          p: rootPath,
        })
        .pipe(
          map((resp) => flattenCategoryTree(resp.data?.category?.children ?? [])),
          catchError(() => {
            this.trees.delete(rootPath);
            return of([]);
          }),
          shareReplay(1),
        );
      this.trees.set(rootPath, t$);
      t$.subscribe((rows) => this.categoryRows.update((m) => new Map(m).set(rootPath, rows)));
    }
    return t$;
  }
}
