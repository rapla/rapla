import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, shareReplay, tap } from 'rxjs';

import { GraphqlService } from '../graphql/graphql.service';

/** Mirrors the server `NewEventOptions.eventTypes` entry (PRD 099 Phase 5). */
export interface NewEventType {
  key: string;
  name: string;
}

/**
 * Mirrors the server `EventTemplate` (PRD 104). `path` is the server-computed
 * grouping path (root first, [] = ungrouped) — a snapshot of the fetched list,
 * never cacheable across differently-filtered lists.
 */
export interface EventTemplate {
  id: string;
  name: string;
  path: string[];
}

export interface NewEventOptions {
  eventTypes: NewEventType[];
  templates: EventTemplate[];
}

const EMPTY: NewEventOptions = { eventTypes: [], templates: [] };

/**
 * PRD 104 Phase 2 — the caller's "Neu" options, fetched once per session and
 * shared by every create entry point (toolbar menu, drag-create, template
 * picker). `eventTypes` is already canCreate-filtered and plugin-gated
 * server-side (PRD 099 Phase 5); empty means the caller cannot create events
 * via the type path at all.
 */
@Injectable({ providedIn: 'root' })
export class NewEventOptionsService {
  private readonly gql = inject(GraphqlService);

  readonly eventTypes = signal<NewEventType[]>([]);
  readonly templates = signal<EventTemplate[]>([]);

  private load$?: Observable<NewEventOptions>;

  /** Shared session fetch — concurrent and repeated callers reuse one request. */
  ensureLoaded(): Observable<NewEventOptions> {
    if (!this.load$) {
      this.load$ = this.gql
        .query<{ newEventOptions: NewEventOptions }>(
          `query { newEventOptions { eventTypes { key name } templates { id name path } } }`,
        )
        .pipe(
          map((resp) => resp.data?.newEventOptions ?? EMPTY),
          tap((options) => {
            this.eventTypes.set(options.eventTypes);
            this.templates.set(options.templates);
          }),
          catchError(() => {
            this.load$ = undefined; // allow a retry on the next entry-point use
            return of(EMPTY);
          }),
          shareReplay(1),
        );
    }
    return this.load$;
  }
}
