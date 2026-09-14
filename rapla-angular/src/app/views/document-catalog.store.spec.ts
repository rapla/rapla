import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DocumentCatalogStore } from './document-catalog.store';
import { DOCUMENT_CATALOG, DocumentsRowMenuProvider } from './document-menu';
import { GraphqlService, type GqlResponse } from '../graphql/graphql.service';
import type { RowContext } from './row-context';

type TypesResponse = { types: { key: string; documents: { name: string; param: string }[] | null }[] };

const ROW: RowContext = {
  primary: { kind: 'reservation', id: 'e1', typeKey: 'event' },
  entities: [],
  subjects: [],
  block: { appointmentId: null, start: null, isException: false },
  rows: [{}],
  viewName: 'v',
};

describe('DocumentCatalogStore warm-up', () => {
  let responses: Subject<GqlResponse<TypesResponse>>;
  let query: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    responses = new Subject<GqlResponse<TypesResponse>>();
    query = vi.fn(() => responses.asObservable());
    TestBed.configureTestingModule({
      providers: [
        { provide: GraphqlService, useValue: { query } },
        { provide: DOCUMENT_CATALOG, useExisting: DocumentCatalogStore },
        DocumentsRowMenuProvider,
      ],
    });
  });

  /** The defect: a lazy fetch on first read leaves the FIRST right-click empty. */
  it('fetches the catalog when the store is constructed, before anything reads it', () => {
    TestBed.inject(DocumentCatalogStore);
    expect(query).toHaveBeenCalledTimes(1);
  });

  it('offers the entry on the FIRST items() call once the fetch resolved', () => {
    TestBed.inject(DocumentCatalogStore); // bootstrap-time warm-up, no read yet
    responses.next({
      data: { types: [{ key: 'event', documents: [{ name: 'Leihschein', param: 'reservationId' }] }] },
    } as GqlResponse<TypesResponse>);

    const provider = TestBed.inject(DocumentsRowMenuProvider);
    const items = provider.items(ROW);

    expect(items.map((i) => i.label)).toEqual(['Leihschein']);
  });

  it('does not re-fetch on every read', () => {
    const store = TestBed.inject(DocumentCatalogStore);
    store.documentsByTypeKey();
    store.documentsByTypeKey();
    expect(query).toHaveBeenCalledTimes(1);
  });
});
