import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ClassificationSchemaService } from './classification-schema.service';
import { ATTRIBUTE_NAMES_QUERY } from './classification-schema';

/**
 * Tier-6 — PRD 124 OQ3: the SDL gives the SHAPE, labels come from
 * `types { key attributeNames { … } }` (request language), loaded once per page.
 */
const SDL = `
type roomClassification implements ResourceClassification & Classification {
  seats: Int @displayName(value : "Plätze")
  projector: Boolean @displayName(value : "Beamer")
  type: DynamicType!
  typeKey: String!
}
`;

const NAMES = {
  data: {
    types: [{ key: 'room', attributeNames: [{ key: 'seats', name: 'Seats', values: null }] }],
  },
};

describe('ClassificationSchemaService — labels from the query (PRD 124 OQ3)', () => {
  let http: HttpTestingController;
  let service: ClassificationSchemaService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ClassificationSchemaService);
  });

  afterEach(() => http.verify());

  const labels = () =>
    service
      .typeMap()!
      .get('room')!
      .attributes.map((a) => a.label);

  it('labels come from attributeNames, not from @displayName; one load per page', () => {
    service.load();
    service.load();
    http.expectOne('/api/graphql/schema').flush(SDL);
    const req = http.expectOne((r) => r.url === '/api/graphql');
    expect((req.request.body as { query: string }).query).toBe(ATTRIBUTE_NAMES_QUERY);
    req.flush(NAMES);
    expect(labels()).toEqual(['Seats', 'Beamer']);
  });

  it('a failed label query keeps the SDL labels', () => {
    service.load();
    http.expectOne('/api/graphql/schema').flush(SDL);
    http
      .expectOne((r) => r.url === '/api/graphql')
      .flush('boom', { status: 500, statusText: 'Server Error' });
    expect(labels()).toEqual(['Plätze', 'Beamer']);
  });
});
