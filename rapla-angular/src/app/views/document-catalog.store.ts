import { Injectable, inject, signal } from '@angular/core';

import { GraphqlService } from '../graphql/graphql.service';
import type { DocumentCatalog, DocumentRef } from './document-menu';

/**
 * PRD 111 D3 — loads `type.documents` once per session and serves it from memory, so the row menu
 * stays synchronous: no round-trip and no view execution on right-click.
 *
 * The load starts in the CONSTRUCTOR, not on first read: `RowMenuProvider.items()` is synchronous,
 * so a fetch kicked off by the first read returns the still-empty map and the first right-click
 * shows no document entries (the entry would only appear on the second). The store is constructed
 * when the row-menu providers are injected — before any right-click can happen.
 *
 * Kept in its own file so `document-menu.ts` (the pure mapping + the provider) can be unit-tested
 * without dragging HttpClient into a tier-5 spec.
 */
@Injectable({ providedIn: 'root' })
export class DocumentCatalogStore implements DocumentCatalog {
  private readonly gql = inject(GraphqlService);
  private readonly byTypeKey = signal(new Map<string, DocumentRef[]>());

  constructor() {
    this.load();
  }

  documentsByTypeKey(): Map<string, DocumentRef[]> {
    return this.byTypeKey();
  }

  private load(): void {
    this.gql
      .query<{
        types: { key: string; documents: DocumentRef[] | null }[];
      }>('{ types { key documents { name param } } }')
      .subscribe({
        next: (response) => {
          const map = new Map<string, DocumentRef[]>();
          for (const type of response.data?.types ?? []) {
            if (type.documents?.length) map.set(type.key, type.documents);
          }
          this.byTypeKey.set(map);
        },
        // A deployment with the plugin off answers null — no entries, no noise.
        error: () => this.byTypeKey.set(new Map()),
      });
  }
}
