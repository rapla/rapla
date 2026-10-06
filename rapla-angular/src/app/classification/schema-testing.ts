import type { HttpTestingController } from '@angular/common/http/testing';

import { ATTRIBUTE_NAMES_QUERY } from './classification-schema';

/** Specs: answer the schema load — the SDL plus the label query (empty → SDL labels, PRD 124 OQ3). */
export function flushSchema(http: HttpTestingController, sdl: string): void {
  http.expectOne('/api/graphql/schema').flush(sdl);
  http
    .expectOne(
      (r) =>
        r.url === '/api/graphql' &&
        (r.body as { query?: string } | null)?.query === ATTRIBUTE_NAMES_QUERY,
    )
    .flush({ data: { types: [] } });
}
