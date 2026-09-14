import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
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
  private readonly snackBar = inject(MatSnackBar);

  instantiate(templateId: string, target: PlacementTarget | null): Observable<EventDraft | null> {
    return this.gql
      .query<{
        reservationsFromTemplate: { id: string }[];
      }>(`query ($id: ID!) { reservationsFromTemplate(templateId: $id) { id } }`, {
        id: templateId,
      })
      .pipe(
        switchMap((resp) => {
          if (resp.errors?.length) {
            throw new Error(resp.errors.map((e) => e.message).join('; '));
          }
          const all = resp.data?.reservationsFromTemplate ?? [];
          // D9 defers multi-instantiation — dropping the rest SILENTLY is the part that is
          // not decided: Swing creates every reservation of the template, so a Semestervorlage
          // would look like it worked while n-1 events never came into being.
          if (all.length > 1) {
            this.snackBar.open(
              `Die Vorlage enthält ${all.length} Veranstaltungen — hier wird nur die erste angelegt (im Swing-Client werden alle angelegt).`,
              undefined,
              { duration: 8000 },
            );
          }
          return all[0] ? this.eventData.load(all[0].id) : of(null);
        }),
        map((loaded) => (loaded ? draftFromTemplate(loaded.draft, target) : null)),
      );
  }
}
