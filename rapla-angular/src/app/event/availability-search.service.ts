import { Injectable, inject } from '@angular/core';
import { Observable, map, of } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
import type { DraftAppointment } from './event-draft';

/**
 * PRD 091 Phase 2.6 / PRD 123 D2 — availability by ids for the rows the sheet renders (assigned
 * rows and the picker's visible rows). The CHEAP display path from Phase 1 — §12-scoped
 * server-side. The former searchText branch went with the sheet's own search field (PRD 123).
 */

export type AllocationStatus = 'AVAILABLE' | 'PARTIAL' | 'CONFLICT' | 'REQUEST_ONLY' | 'FORBIDDEN';

export interface AvailabilityRow {
  id: string;
  name: string;
  status: AllocationStatus;
  conflictingAppointmentIds: string[];
}

interface AvailabilityWire {
  resourceAvailability: {
    resource: { id: string; name: string | null };
    status: AllocationStatus;
    conflictingAppointmentIds: string[];
  }[];
}

const QUERY = `
  query ($input: AvailabilityInput!) {
    resourceAvailability(input: $input) {
      resource { id name }
      status
      conflictingAppointmentIds
    }
  }`;

export interface SearchResult {
  /** Ranked search hits (server order), excluding nothing — caller filters. */
  hits: AvailabilityRow[];
  /** Status for every requested id (pins + assigned). */
  byId: Map<string, AvailabilityRow>;
}

@Injectable({ providedIn: 'root' })
export class AvailabilitySearchService {
  private readonly gql = inject(GraphqlService);

  statuses(
    appointments: DraftAppointment[],
    ids: string[],
    ignoreReservationId: string | null,
  ): Observable<Map<string, AvailabilityRow>> {
    if (ids.length === 0) return of(new Map());
    const appointmentInputs = appointments.map((a) => {
      const input: Record<string, unknown> = {
        id: a.id,
        start: a.start,
        end: a.end,
        allDay: a.allDay,
      };
      if (a.repeating) input['repeating'] = a.repeating;
      return input;
    });
    const input = {
      appointments: appointmentInputs,
      candidates: { ids },
      ignoreReservationIds: ignoreReservationId ? [ignoreReservationId] : [],
    };
    return this.gql.query<AvailabilityWire>(QUERY, { input }).pipe(
      map((resp) => {
        // Never swallow errors[] silently (PRD 091 Phase 4.5 side-finding: the
        // repeating-UNSUPPORTED gap hid behind empty rows) — log, keep the
        // pipeline alive with an empty result.
        if (resp.errors?.length) {
          console.warn('[availability] resourceAvailability errors:', resp.errors);
        }
        return new Map(
          (resp.data?.resourceAvailability ?? []).map((r) => [
            r.resource.id,
            {
              id: r.resource.id,
              name: r.resource.name ?? r.resource.id,
              status: r.status,
              conflictingAppointmentIds: r.conflictingAppointmentIds,
            },
          ]),
        );
      }),
    );
  }
}
