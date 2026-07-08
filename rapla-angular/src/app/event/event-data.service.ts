import { Injectable, inject } from '@angular/core';
import { Observable, map, of, switchMap } from 'rxjs';

import {
  normalizeClassificationValues,
  valueSelections,
} from '../classification/classification-schema';
import { ClassificationSchemaService } from '../classification/classification-schema.service';
import { GraphqlService } from '../graphql/graphql.service';
import { toLocalDateTime } from '../graphql/local-date-time';
import type { MutationResult } from '../graphql/mutation-result';
import {
  fromReservation,
  toReservationInput,
  type EventDraft,
  type ReservationWire,
  type RepeatingRule,
} from './event-draft';

/**
 * PRD 091 Phase 2.3 — load/save for the event sheet.
 *
 * Loading is two-step because classification attributes are generated
 * per-DynamicType GraphQL fields (PRD 035): first the shell (type key,
 * appointments, allocations), then a value fragment built from the parsed
 * SDL descriptors (ClassificationSchemaService, PRD 096) — the 2.0b
 * pass-through needs EVERY attribute value, not just the ones the sheet
 * renders. Object-valued attributes (tree CATEGORY / ALLOCATABLE) are
 * normalized to their ids so the values echo cleanly into the
 * classification input variant.
 */

interface ShellWire {
  reservation: {
    id: string;
    lastModifiedAt: string | null;
    canModify: boolean;
    classification: { typeKey: string };
    appointments: {
      id: string;
      start: string;
      end: string;
      allDay: boolean;
      repeating: RepeatingRule | null;
    }[];
    allocations: { allocatable: { id: string; name: string | null }; appointmentIds: string[] | null }[];
  } | null;
}

export interface LoadedEvent {
  draft: EventDraft;
  canModify: boolean;
}

const SHELL_QUERY = `
  query ($id: ID!) {
    reservation(id: $id) {
      id
      lastModifiedAt
      canModify
      classification { typeKey }
      appointments { id start end allDay repeating { type interval end count weekdays exceptions } }
      allocations { allocatable { id name } appointmentIds }
    }
  }`;


@Injectable({ providedIn: 'root' })
export class EventDataService {
  private readonly gql = inject(GraphqlService);
  private readonly schema = inject(ClassificationSchemaService);

  load(id: string): Observable<LoadedEvent | null> {
    return this.gql.query<ShellWire>(SHELL_QUERY, { id }).pipe(
      switchMap((resp) => {
        const shell = resp.data?.reservation;
        if (!shell) return of(null);
        return this.classificationValues(id, shell.classification.typeKey).pipe(
          map((values) => {
            const wire: ReservationWire = {
              id: shell.id,
              lastChanged: toLocalDateTime(shell.lastModifiedAt),
              classification: { typeKey: shell.classification.typeKey, ...values },
              appointments: shell.appointments,
              allocations: shell.allocations,
            };
            return { draft: fromReservation(wire), canModify: shell.canModify };
          }),
        );
      }),
    );
  }

  /** SDL-descriptor-driven full-value read (PRD 096 Phase 1.2). */
  private classificationValues(id: string, typeKey: string): Observable<Record<string, unknown>> {
    return this.schema.load().pipe(
      switchMap((types) => {
        const type = types.get(typeKey);
        if (!type || type.attributes.length === 0) return of({});
        const fragment = `... on ${typeKey}Classification { ${valueSelections(type.attributes)} }`;
        const doc = `query ($id: ID!) { reservation(id: $id) { classification { ${fragment} } } }`;
        return this.gql
          .query<{ reservation: { classification: Record<string, unknown> } | null }>(doc, { id })
          .pipe(
            map((resp) =>
              normalizeClassificationValues(resp.data?.reservation?.classification ?? {}),
            ),
          );
      }),
    );
  }

  save(draft: EventDraft): Observable<MutationResult<{ id: string }>> {
    const input = toReservationInput(draft);
    if (draft.persisted) {
      return this.gql
        .mutate<{ updateReservation: { id: string } }>(
          `mutation ($id: ID!, $input: UpdateReservationInput!, $expected: LocalDateTime) {
             updateReservation(id: $id, input: $input, expectedLastChanged: $expected) { id }
           }`,
          { id: draft.id, input, expected: draft.lastChanged },
        )
        .pipe(map((r) => (r.kind === 'ok' ? { kind: 'ok', data: r.data.updateReservation } : r)));
    }
    return this.gql
      .mutate<{ createReservation: { id: string } }>(
        `mutation ($input: CreateReservationInput!) {
           createReservation(input: $input) { id }
         }`,
        { input: { id: draft.id, ...input } },
      )
      .pipe(map((r) => (r.kind === 'ok' ? { kind: 'ok', data: r.data.createReservation } : r)));
  }
}
