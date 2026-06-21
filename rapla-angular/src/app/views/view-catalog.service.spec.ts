import { describe, it, expect } from 'vitest';
import { of, firstValueFrom, type Observable } from 'rxjs';

import { ViewCatalogService, orderViews, type ViewInfo } from './view-catalog.service';
import type { GraphqlService, GqlResponse } from '../graphql/graphql.service';

interface ViewRow extends ViewInfo {
  valid: boolean;
}

function fakeGql(rows: ViewRow[]): GraphqlService {
  return {
    query: <T>(): Observable<GqlResponse<T>> =>
      of({ data: { listViews: rows } as unknown as T }),
  } as unknown as GraphqlService;
}

const view = (name: string, source: 'BUILTIN' | 'CUSTOM', valid = true): ViewRow => ({
  name,
  title: name,
  source,
  valid,
});

describe('orderViews', () => {
  it('puts CUSTOM views before BUILTIN, stable within each group', () => {
    const ordered = orderViews([
      view('rapla_appointments', 'BUILTIN'),
      view('Wochenansicht', 'CUSTOM'),
      view('rapla_reservations', 'BUILTIN'),
    ]);
    expect(ordered.map((v) => v.name)).toEqual([
      'Wochenansicht',
      'rapla_appointments',
      'rapla_reservations',
    ]);
  });
});

describe('ViewCatalogService', () => {
  it('maps listViews and drops invalid views', async () => {
    const svc = new ViewCatalogService(
      fakeGql([
        view('Wochenansicht', 'CUSTOM'),
        view('broken', 'CUSTOM', false),
        view('rapla_appointments', 'BUILTIN'),
      ]),
    );
    const views = await firstValueFrom(svc.listViews());
    expect(views.map((v) => v.name)).toEqual(['Wochenansicht', 'rapla_appointments']);
  });
});
