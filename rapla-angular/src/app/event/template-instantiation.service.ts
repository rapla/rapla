import { Injectable, inject } from '@angular/core';
import { Observable, map, of, switchMap } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
import { EventDataService } from './event-data.service';
import type { EventDraft } from './event-draft';
import { draftFromTemplate, type PlacementTarget } from './new-event-picker-model';

/**
 * PRD 104 Phase 4 (D8) — instantiate a template: fetch the template's
 * reservation ids (`reservationsFromTemplate`, §12 empty when unknown/
 * unreadable), load the FIRST one through the sheet's existing
 * `reservation(id:)` path, and turn it into a fresh draft (re-keyed ids,
 * date-shifted onto the target, nothing persisted). null = template empty or
 * not visible — the caller shows the hint. v1 uses the first reservation only
 * (multi-reservation templates: PRD 104 D9, provided for, not built).
 */
@Injectable({ providedIn: 'root' })
export class TemplateInstantiationService {
  private readonly gql = inject(GraphqlService);
  private readonly eventData = inject(EventDataService);

  instantiate(templateId: string, target: PlacementTarget | null): Observable<EventDraft | null> {
    return this.gql
      .query<{ reservationsFromTemplate: { id: string }[] }>(
        `query ($id: ID!) { reservationsFromTemplate(templateId: $id) { id } }`,
        { id: templateId },
      )
      .pipe(
        switchMap((resp) => {
          if (resp.errors?.length) {
            throw new Error(resp.errors.map((e) => e.message).join('; '));
          }
          const first = resp.data?.reservationsFromTemplate?.[0];
          return first ? this.eventData.load(first.id) : of(null);
        }),
        map((loaded) => (loaded ? draftFromTemplate(loaded.draft, target) : null)),
      );
  }
}
