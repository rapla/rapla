import { InjectionToken, Injectable, inject } from '@angular/core';

import type { RowContext } from './row-context';
import type { RowMenuItem, RowMenuProvider } from './row-menu';

/**
 * PRD 111 — the documents feature's own SPA contribution (D4): it decides where its entries go,
 * there is no generic server-side "actions for entity" query.
 *
 * A dynamic type carries a `documents` annotation; the server resolves it per caller into
 * {@link DocumentRef}s. A row whose subject is of that type offers one direct entry per document,
 * in annotation order, opening the rendered document in a new tab.
 */
export interface DocumentRef {
  name: string;
  param: string;
}

export interface DocumentEntry {
  label: string;
  url: string;
}

/** The catalog seam — a map typeKey → its documents. Empty until the fetch lands. */
export interface DocumentCatalog {
  documentsByTypeKey(): Map<string, DocumentRef[]>;
}

/** Bound to `DocumentCatalogStore` in app.config; a token keeps this file HttpClient-free. */
export const DOCUMENT_CATALOG = new InjectionToken<DocumentCatalog>('DOCUMENT_CATALOG');

/**
 * kind + typeKey → the type's documents → one entry each. Pure, so it carries the rules:
 * multi-select offers nothing (a document takes exactly one id), and a subject without a typeKey
 * offers nothing (the view did not select `classification { typeKey }` — fail closed).
 */
export function documentEntries(
  ctx: RowContext,
  byTypeKey: Map<string, DocumentRef[]>,
): DocumentEntry[] {
  if (ctx.rows.length > 1) return [];
  const subject = ctx.primary;
  if (!subject?.typeKey) return [];
  const documents = byTypeKey.get(subject.typeKey) ?? [];
  return documents.map((doc) => ({
    label: doc.name,
    url:
      '/api/documents/' +
      encodeURIComponent(doc.name) +
      '?' +
      new URLSearchParams({ [doc.param]: subject.id }).toString(),
  }));
}

/** The entries as menu items. Pure — the provider is the DI shell around this. */
export function documentMenuItems(
  ctx: RowContext,
  catalog: DocumentCatalog,
  open: (url: string) => void,
): RowMenuItem[] {
  return documentEntries(ctx, catalog.documentsByTypeKey()).map((entry) => ({
    id: 'document:' + entry.label,
    label: entry.label,
    run: () => open(entry.url),
  }));
}

@Injectable()
export class DocumentsRowMenuProvider implements RowMenuProvider {
  private readonly catalog = inject(DOCUMENT_CATALOG);

  items(ctx: RowContext): RowMenuItem[] {
    return documentMenuItems(ctx, this.catalog, (url) => window.open(url, '_blank'));
  }
}
