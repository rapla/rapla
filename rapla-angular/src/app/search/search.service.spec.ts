import { describe, it, expect } from 'vitest';
import { of, firstValueFrom, type Observable } from 'rxjs';

import { SearchService } from './search.service';
import type { GraphqlService, GqlResponse } from '../graphql/graphql.service';

interface SearchHit {
  __typename: string;
  id: string;
  label: string | null;
  sublabel: string | null;
  score: number;
  firstOccurrenceStart?: string | null;
}
interface SearchGroupDto {
  kind: string;
  heading: string;
  hits: SearchHit[];
}

function fakeGql(
  groups: SearchGroupDto[],
  onVars?: (v: Record<string, unknown>) => void,
): GraphqlService {
  return {
    query: <T>(_doc: string, vars: Record<string, unknown>): Observable<GqlResponse<T>> => {
      onVars?.(vars);
      return of({ data: { search: { groups } } as unknown as T });
    },
  } as unknown as GraphqlService;
}

const resourceHit = (id: string, label: string, sublabel = 'Raum'): SearchHit => ({
  __typename: 'ResourceHit',
  id,
  label,
  sublabel,
  score: 1,
});
const eventHit = (id: string, label: string, sublabel = 'event'): SearchHit => ({
  __typename: 'EventHit',
  id,
  label,
  sublabel,
  score: 1,
  firstOccurrenceStart: '2026-03-01T10:00:00',
});
const userHit = (id: string, label: string, sublabel: string | null = null): SearchHit => ({
  __typename: 'UserHit',
  id,
  label,
  sublabel,
  score: 1,
});

describe('SearchService', () => {
  it('returns empty for a blank term WITHOUT querying', async () => {
    let called = false;
    const gql = fakeGql([], () => (called = true));
    const groups = await firstValueFrom(new SearchService(gql).search('   '));
    expect(groups).toEqual([]);
    expect(called).toBe(false);
  });

  it('does NOT query below 3 characters (spaces counted)', async () => {
    let called = false;
    const gql = fakeGql([], () => (called = true));
    for (const term of ['C', 'C3', 'ab']) {
      const groups = await firstValueFrom(new SearchService(gql).search(term));
      expect(groups).toEqual([]);
    }
    expect(called).toBe(false);
  });

  it('counts spaces toward the minimum — a trailing space reaches 3 and searches', async () => {
    let called = false;
    const gql = fakeGql(
      [{ kind: 'EVENT', heading: 'Veranstaltungen', hits: [eventHit('e1', 'C3')] }],
      () => (called = true),
    );
    const groups = await firstValueFrom(new SearchService(gql).search('C3 '));
    expect(called).toBe(true); // raw "C3 " is 3 chars
    expect(groups.length).toBe(1);
  });

  /** PRD 119 D4 — resources and users live in the picker; the dropdown asks for events only. */
  it('asks the server for EVENT hits only', async () => {
    let vars: Record<string, unknown> | undefined;
    const gql = fakeGql([], (v) => (vars = v));
    await firstValueFrom(new SearchService(gql).search('Mathe'));
    expect(vars?.['kinds']).toEqual(['EVENT']);
  });

  it('maps an EVENT bucket to event results carrying the first occurrence start', async () => {
    const gql = fakeGql([
      { kind: 'EVENT', heading: 'Veranstaltungen', hits: [eventHit('e1', 'Mathe 1')] },
    ]);
    const groups = await firstValueFrom(new SearchService(gql).search('Mathe'));
    expect(groups.length).toBe(1);
    expect(groups[0].kind).toBe('event');
    expect(groups[0].results[0]).toMatchObject({
      id: 'e1',
      kind: 'event',
      label: 'Mathe 1',
      start: '2026-03-01T10:00:00',
    });
  });

  it('drops RESOURCE and USER buckets (they are rows of the picker now)', async () => {
    const gql = fakeGql([
      { kind: 'RESOURCE', heading: 'Ressourcen', hits: [resourceHit('r1', 'Raum 1')] },
      { kind: 'EVENT', heading: 'Veranstaltungen', hits: [eventHit('e1', 'Event 1')] },
      { kind: 'USER', heading: 'Benutzer', hits: [userHit('u1', 'Monty', 'monty')] },
    ]);
    const groups = await firstValueFrom(new SearchService(gql).search('123'));
    expect(groups.map((g) => g.kind)).toEqual(['event']);
  });

  it('passes the trimmed query term + a positive limit to the resolver', async () => {
    let vars: Record<string, unknown> | undefined;
    const gql = fakeGql([], (v) => (vars = v));
    await firstValueFrom(new SearchService(gql).search('  Mathe '));
    expect(vars?.['query']).toBe('Mathe');
    expect(vars?.['limit'] as number).toBeGreaterThan(0);
  });

  it('drops unknown server kinds rather than guessing', async () => {
    const gql = fakeGql([
      { kind: 'WHO_KNOWS', heading: '?', hits: [eventHit('x', 'x')] },
      { kind: 'EVENT', heading: 'Veranstaltungen', hits: [eventHit('e1', 'Event 1')] },
    ]);
    const groups = await firstValueFrom(new SearchService(gql).search('xxx'));
    expect(groups.map((g) => g.kind)).toEqual(['event']);
  });

  it('returns no groups when nothing matches', async () => {
    const groups = await firstValueFrom(new SearchService(fakeGql([])).search('zzz'));
    expect(groups).toEqual([]);
  });

  it('falls back to a placeholder label when label is null', async () => {
    const gql = fakeGql([
      {
        kind: 'EVENT',
        heading: 'Veranstaltungen',
        hits: [{ __typename: 'EventHit', id: 'x', label: null, sublabel: null, score: 1 }],
      },
    ]);
    const groups = await firstValueFrom(new SearchService(gql).search('xxx'));
    expect(groups[0].results[0].label).toBe('(ohne Name)');
    expect(groups[0].results[0].sublabel).toBeUndefined();
  });
});
