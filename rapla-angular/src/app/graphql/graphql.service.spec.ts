import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { GraphqlService, GqlResponse } from './graphql.service';

describe('GraphqlService', () => {
  let service: GraphqlService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [GraphqlService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GraphqlService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('POSTs { query, variables } to /api/graphql', () => {
    const doc = 'query Q($filter: ReservationFilter!){ appointmentBlocks(filter:$filter){ start } }';
    const vars = { filter: { from: '2026-01-01T00:00:00', to: '2026-12-31T00:00:00' } };

    service.query(doc, vars).subscribe();

    const req = httpMock.expectOne('/api/graphql');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ query: doc, variables: vars });
    // No Authorization header — cookie-credential model (PRD 072).
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ data: { appointmentBlocks: [] } });
  });

  it('passes through data + extensions.view', () => {
    let result: GqlResponse<{ appointmentBlocks: unknown[] }> | undefined;
    service
      .query<{ appointmentBlocks: unknown[] }>('query{ x }')
      .subscribe((r) => (result = r));

    httpMock.expectOne('/api/graphql').flush({
      data: { appointmentBlocks: [{ start: '2026-06-15T08:00:00' }] },
      extensions: { view: { key: 'appointments', columns: [{ alias: 'start', header: 'Beginn' }] } },
    });

    expect(result?.data?.appointmentBlocks).toHaveLength(1);
    expect(result?.extensions?.view?.key).toBe('appointments');
  });

  it('defaults variables to an empty object', () => {
    service.query('query{ x }').subscribe();
    const req = httpMock.expectOne('/api/graphql');
    expect(req.request.body).toEqual({ query: 'query{ x }', variables: {} });
    req.flush({ data: {} });
  });

  it('surfaces a transport error on the error channel', () => {
    let errored = false;
    service.query('query{ x }').subscribe({ error: () => (errored = true) });
    httpMock.expectOne('/api/graphql').flush('boom', { status: 500, statusText: 'Server Error' });
    expect(errored).toBe(true);
  });
});
