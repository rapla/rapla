import { describe, it, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, firstValueFrom, type Observable } from 'rxjs';

import { ViewCatalogService, orderViews, type ViewInfo } from './view-catalog.service';
import { GraphqlService, type GqlResponse } from '../graphql/graphql.service';

interface ViewRow extends ViewInfo {
  valid: boolean;
}

function fakeGql(rows: ViewRow[]): GraphqlService {
  return {
    query: <T>(): Observable<GqlResponse<T>> => of({ data: { listViews: rows } as unknown as T }),
  } as unknown as GraphqlService;
}

const view = (name: string, source: 'BUILTIN' | 'CUSTOM', valid = true): ViewRow => ({
  name,
  title: name,
  source,
  valid,
});

describe('orderViews', () => {
  it('puts planning views first, CUSTOM next, Prüfen views last, stable within each group', () => {
    const ordered = orderViews([
      { ...view('rapla_conflicts', 'BUILTIN'), selection: 'CONFLICTS' },
      view('rapla_appointments', 'BUILTIN'),
      view('Wochenansicht', 'CUSTOM'),
      view('rapla_reservations', 'BUILTIN'),
    ]);
    expect(ordered.map((v) => v.name)).toEqual([
      'rapla_appointments',
      'rapla_reservations',
      'Wochenansicht',
      'rapla_conflicts',
    ]);
  });
});

describe('ViewCatalogService', () => {
  it('maps listViews and drops invalid views', async () => {
    TestBed.configureTestingModule({
      providers: [
        ViewCatalogService,
        {
          provide: GraphqlService,
          useValue: fakeGql([
            view('Wochenansicht', 'CUSTOM'),
            view('broken', 'CUSTOM', false),
            view('rapla_appointments', 'BUILTIN'),
          ]),
        },
      ],
    });
    const svc = TestBed.inject(ViewCatalogService);
    const views = await firstValueFrom(svc.listViews());
    expect(views.map((v) => v.name)).toEqual(['rapla_appointments', 'Wochenansicht']);
  });

  it('PRD 128 D7 — asks for the selection source and keeps it', async () => {
    const queries: string[] = [];
    TestBed.configureTestingModule({
      providers: [
        ViewCatalogService,
        {
          provide: GraphqlService,
          useValue: {
            query: <T>(doc: string): Observable<GqlResponse<T>> => {
              queries.push(doc);
              return of({
                data: {
                  listViews: [{ ...view('rapla_conflicts', 'BUILTIN'), selection: 'CONFLICTS' }],
                } as unknown as T,
              });
            },
          },
        },
      ],
    });
    const views = await firstValueFrom(TestBed.inject(ViewCatalogService).listViews());
    expect(queries[0]).toContain('selection');
    expect(views[0].selection).toBe('CONFLICTS');
  });
});
