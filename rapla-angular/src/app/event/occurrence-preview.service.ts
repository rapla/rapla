import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';
import type { DraftAppointment } from './event-draft';

/**
 * PRD 091 Phase 4.1/4.3 — the recurrence editor's occurrence preview.
 * Server-owned expansion (`expandOccurrences` wraps Appointment.createBlocks)
 * — the MONTHLY weekday-in-nth-week semantic and exception-skip rules are
 * never reimplemented client-side. Excepted occurrences arrive flagged, not
 * dropped (the panel strikes them through, click restores).
 */

export interface OccurrenceRow {
  /** ISO LocalDateTime */
  start: string;
  end: string;
  exception: boolean;
}

interface OccurrenceWire {
  expandOccurrences: OccurrenceRow[];
}

const QUERY = `
  query ($appointment: AppointmentInput!, $limit: Int) {
    expandOccurrences(appointment: $appointment, limit: $limit) {
      start
      end
      exception
    }
  }`;

@Injectable({ providedIn: 'root' })
export class OccurrencePreviewService {
  private readonly gql = inject(GraphqlService);

  expand(appointment: DraftAppointment, limit = 30): Observable<OccurrenceRow[]> {
    const input: Record<string, unknown> = {
      id: appointment.id,
      start: appointment.start,
      end: appointment.end,
      allDay: appointment.allDay,
    };
    if (appointment.repeating) input['repeating'] = appointment.repeating;
    return this.gql.query<OccurrenceWire>(QUERY, { appointment: input, limit }).pipe(
      map((resp) => {
        if (resp.errors?.length) {
          console.warn('[occurrence-preview] expandOccurrences errors:', resp.errors);
        }
        return resp.data?.expandOccurrences ?? [];
      }),
    );
  }
}
