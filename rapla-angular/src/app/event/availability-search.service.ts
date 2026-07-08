import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
import type { DraftAppointment } from './event-draft';

/**
 * PRD 091 Phase 2.6 — the add-mode search: one `resourceAvailability` call
 * with a filter (search hits, ranking from the allocatables resolver) plus
 * one with ids (pins + assigned rows need statuses too; `CandidateInput` is
 * `@oneOf`, so ids can't ride in the filter call). Both are the CHEAP
 * display path from Phase 1 — §12-scoped server-side.
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
    allocatable: { id: string; name: string | null };
    status: AllocationStatus;
    conflictingAppointmentIds: string[];
  }[];
}

const QUERY = `
  query ($input: AvailabilityInput!) {
    resourceAvailability(input: $input) {
      allocatable { id name }
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

  search(
    appointments: DraftAppointment[],
    searchText: string,
    ids: string[],
    ignoreReservationId: string | null,
  ): Observable<SearchResult> {
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
    const base = (candidates: Record<string, unknown>) => ({
      appointments: appointmentInputs,
      candidates,
      ignoreReservationIds: ignoreReservationId ? [ignoreReservationId] : [],
    });
    // Never swallow errors[] silently (PRD 091 Phase 4.5 side-finding: the
    // repeating-UNSUPPORTED gap hid behind empty rows) — log, keep the
    // pipeline alive with an empty result.
    const rows = (resp: { data?: AvailabilityWire; errors?: unknown[] }): AvailabilityRow[] => {
      if (resp.errors?.length) {
        console.warn('[availability] resourceAvailability errors:', resp.errors);
      }
      return (resp.data?.resourceAvailability ?? []).map((r) => ({
        id: r.allocatable.id,
        name: r.allocatable.name ?? r.allocatable.id,
        status: r.status,
        conflictingAppointmentIds: r.conflictingAppointmentIds,
      }));
    };

    const hits$ =
      searchText.trim().length > 0
        ? this.gql
            .query<AvailabilityWire>(QUERY, {
              input: base({ filter: { searchText: searchText.trim(), limit: 50 } }),
            })
            .pipe(map(rows))
        : of([] as AvailabilityRow[]);
    const byId$ =
      ids.length > 0
        ? this.gql
            .query<AvailabilityWire>(QUERY, { input: base({ ids }) })
            .pipe(map(rows))
        : of([] as AvailabilityRow[]);

    return forkJoin({ hits: hits$, idRows: byId$ }).pipe(
      map(({ hits, idRows }) => ({
        hits,
        byId: new Map(idRows.map((r) => [r.id, r])),
      })),
    );
  }
}
