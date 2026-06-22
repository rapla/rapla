import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';

import { GraphqlService } from '../graphql/graphql.service';
import type { SearchAction, SearchResult, SearchResultGroup, SearchResultKind } from './search.types';

/**
 * Omnibox multisearch against the unified {@code search(query, kinds, limit)}
 * resolver (PRD 081, §12-scoped). The server returns kind + data, already
 * bucketed and ranked; ACTIONS are derived here per {@code kind} (the action
 * taxonomy is a client concern — see PRD 081 §"Actions are a CLIENT concern").
 * Phase 1 surfaces RESOURCE + EVENT; OCCURRENCE / GROUP land in later phases
 * and map automatically once the server produces them.
 *
 * <p>The {@code Observable<SearchResultGroup[]>} seam is unchanged from the
 * earlier resources-only fan-out — the omnibox component is untouched.
 */
const SEARCH_QUERY = `
query OmniSuche($query: String!, $limit: Int!) {
  search(query: $query, limit: $limit) {
    groups {
      kind
      heading
      hits {
        __typename
        id
        label
        sublabel
        score
        ... on EventHit { firstOccurrenceStart }
      }
    }
  }
}`.trim();

const SEARCH_LIMIT = 20;

/** Minimum query length before the omnibox hits the server — counted on the RAW
 *  term, spaces included (a too-short query matches almost everything: expensive
 *  and useless). */
export const MIN_QUERY_LENGTH = 3;

/** Server enum value → SPA kind. Unknown kinds are dropped defensively. */
const KIND_MAP: Record<string, SearchResultKind> = {
  RESOURCE: 'resource',
  EVENT: 'event',
  OCCURRENCE: 'occurrence',
  GROUP: 'group',
  SAVED_VIEW: 'savedView',
};

/** Default action buttons per kind — the omnibox renders these. */
const ACTIONS: Record<SearchResultKind, SearchAction[]> = {
  resource: ['filter-replace', 'filter-add'],
  event: ['navigate', 'filter-add', 'edit'],
  occurrence: ['navigate', 'edit'],
  group: ['load-group'],
  savedView: ['navigate'],
};

interface SearchHit {
  __typename: string;
  id: string;
  label: string | null;
  sublabel: string | null;
  score: number;
  count?: number | null;
  firstOccurrenceStart?: string | null;
}
interface SearchGroupDto {
  kind: string;
  heading: string;
  hits: SearchHit[];
}
interface SearchData {
  search: { groups: SearchGroupDto[] } | null;
}

@Injectable({ providedIn: 'root' })
export class SearchService {
  // Constructor injection (not inject()) is deliberate: it lets the tier-5
  // spec construct `new SearchService(fakeGql)` without a TestBed.
  // eslint-disable-next-line @angular-eslint/prefer-inject
  constructor(private readonly gql: GraphqlService) {}

  search(term: string): Observable<SearchResultGroup[]> {
    // Require ≥ MIN_QUERY_LENGTH chars (raw, spaces counted) before hitting the
    // server; a purely-blank term never searches.
    if (!term.trim() || term.length < MIN_QUERY_LENGTH) return of([]);
    const q = term.trim();
    return this.gql
      .query<SearchData>(SEARCH_QUERY, { query: q, limit: SEARCH_LIMIT })
      .pipe(map((res) => toGroups(res.data?.search?.groups ?? [])));
  }
}

function toGroups(groups: SearchGroupDto[]): SearchResultGroup[] {
  const out: SearchResultGroup[] = [];
  for (const g of groups) {
    const kind = KIND_MAP[g.kind];
    if (!kind) continue; // unknown server kind — drop rather than guess
    const results: SearchResult[] = g.hits.map((h) => ({
      id: h.id,
      kind,
      label: h.label ?? '(ohne Name)',
      sublabel: h.sublabel ?? undefined,
      actions: ACTIONS[kind],
      ...(h.count != null ? { count: h.count } : {}),
    }));
    if (results.length) out.push({ kind, heading: g.heading, results });
  }
  return out;
}
