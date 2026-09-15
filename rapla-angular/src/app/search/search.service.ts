import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';

import { GraphqlService } from '../graphql/graphql.service';
import type { SearchResult, SearchResultGroup, SearchResultKind } from './search.types';

/**
 * Omnibox multisearch against the unified {@code search(query, kinds, limit)}
 * resolver (PRD 081, §12-scoped). PRD 119 D4: the dropdown asks for EVENT hits
 * only — resources and users are rows of the picker, filtered in the browser.
 * The server returns the events already ranked; the omnibox opens a hit's sheet.
 */
const SEARCH_QUERY = `
query OmniSuche($query: String!, $kinds: [SearchKind!], $limit: Int!) {
  search(query: $query, kinds: $kinds, limit: $limit) {
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
  USER: 'user',
  OCCURRENCE: 'occurrence',
  GROUP: 'group',
  SAVED_VIEW: 'savedView',
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
      .query<SearchData>(SEARCH_QUERY, { query: q, kinds: ['EVENT'], limit: SEARCH_LIMIT })
      .pipe(map((res) => toGroups(res.data?.search?.groups ?? [])));
  }
}

function toGroups(groups: SearchGroupDto[]): SearchResultGroup[] {
  const out: SearchResultGroup[] = [];
  for (const g of groups) {
    const kind = KIND_MAP[g.kind];
    if (kind !== 'event') continue; // PRD 119 D4 — resources/users are picker rows; unknown kinds dropped
    const results: SearchResult[] = g.hits.map((h) => ({
      id: h.id,
      kind,
      label: h.label ?? '(ohne Name)',
      sublabel: h.sublabel ?? undefined,
      ...(h.firstOccurrenceStart ? { start: h.firstOccurrenceStart } : {}),
      ...(h.count != null ? { count: h.count } : {}),
    }));
    if (results.length) out.push({ kind, heading: g.heading, results });
  }
  return out;
}
