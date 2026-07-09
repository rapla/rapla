import { describe, expect, it, vi } from 'vitest';
import { of, type Observable } from 'rxjs';

import {
  buildMoveScopeCommand,
  moveBlockFacts,
  moveScopeOptions,
  type MoveBlockFacts,
  type MoveGesture,
} from './move-scope';
import type { MutationResult } from '../graphql/mutation-result';
import type { GraphqlService } from '../graphql/graphql.service';

const ok: MutationResult<unknown> = { kind: 'ok', data: {} };

function row(over: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    start: '2031-03-05T10:00:00',
    end: '2031-03-05T11:00:00',
    name: 'Physik',
    reservation: { id: 'r-1', canModify: true, appointmentCount: 1 },
    appointment: { id: 'a-1', repeating: null },
    isException: false,
    ...over,
  };
}

function facts(over: Partial<MoveBlockFacts> = {}): MoveBlockFacts {
  return {
    reservationId: 'r-1',
    appointmentId: 'a-1',
    occurrence: '2031-03-05T10:00:00',
    repeating: false,
    multi: false,
    isException: false,
    canModify: true,
    name: 'Physik',
    ...over,
  };
}

function captureGql(): { gql: GraphqlService; calls: { query: string; vars: unknown }[] } {
  const calls: { query: string; vars: unknown }[] = [];
  const mutate = vi.fn((query: string, vars: unknown): Observable<MutationResult<unknown>> => {
    calls.push({ query, vars });
    return of(ok);
  });
  return { gql: { mutate } as unknown as GraphqlService, calls };
}

describe('moveBlockFacts', () => {
  it('reads reservation/appointment/occurrence facts off a wire row', () => {
    const f = moveBlockFacts(
      row({
        reservation: { id: 'r-9', canModify: true, appointmentCount: 3 },
        appointment: { id: 'a-9', repeating: { type: 'WEEKLY' } },
      }),
    );
    expect(f).toEqual({
      reservationId: 'r-9',
      appointmentId: 'a-9',
      occurrence: '2031-03-05T10:00:00',
      repeating: true,
      multi: true,
      isException: false,
      canModify: true,
      name: 'Physik',
    });
  });

  it('returns null when the subject/date facts are missing (custom view)', () => {
    expect(moveBlockFacts({ start: '2031-03-05T10:00:00' })).toBeNull();
    expect(moveBlockFacts(row({ reservation: { canModify: true } }))).toBeNull();
  });
});

describe('moveScopeOptions', () => {
  const move: MoveGesture = { kind: 'move', totalMinutes: 60 };
  const resize: MoveGesture = { kind: 'resize', newEnd: 'x', oldEnd: 'y' };

  it('MOVE simple (single, non-repeating) → EVENT only (no dialog)', () => {
    expect(moveScopeOptions(facts(), move).map((o) => o.scope)).toEqual(['event']);
  });

  it('MOVE repeating single → EVENT + SINGLE (SERIE ≡ EVENT so omitted)', () => {
    expect(moveScopeOptions(facts({ repeating: true }), move).map((o) => o.scope)).toEqual([
      'event',
      'single',
    ]);
  });

  it('MOVE non-repeating multi → EVENT + SINGLE', () => {
    expect(moveScopeOptions(facts({ multi: true }), move).map((o) => o.scope)).toEqual([
      'event',
      'single',
    ]);
  });

  it('MOVE repeating multi → EVENT + SERIE + SINGLE', () => {
    expect(
      moveScopeOptions(facts({ repeating: true, multi: true }), move).map((o) => o.scope),
    ).toEqual(['event', 'serie', 'single']);
  });

  it('RESIZE non-repeating → single implicit SERIE (no EVENT, no dialog)', () => {
    expect(moveScopeOptions(facts(), resize).map((o) => o.scope)).toEqual(['serie']);
  });

  it('RESIZE repeating → SERIE + SINGLE (never EVENT)', () => {
    expect(moveScopeOptions(facts({ repeating: true }), resize).map((o) => o.scope)).toEqual([
      'serie',
      'single',
    ]);
  });
});

describe('buildMoveScopeCommand — verb selection', () => {
  const move: MoveGesture = { kind: 'move', totalMinutes: 1440 + 90 }; // +1d +1:30h

  it('MOVE / EVENT → moveReservations against the fixed pivot', () => {
    const { gql, calls } = captureGql();
    buildMoveScopeCommand(gql, facts(), 'event', move).execute().subscribe();
    expect(calls[0].query).toContain('moveReservations');
    expect(calls[0].vars).toEqual({
      ids: ['r-1'],
      ref: '2000-01-01T00:00:00',
      target: { dateTime: '2000-01-02T01:30:00' },
    });
  });

  it('MOVE / SERIE → moveAppointment(occurrence, target.dateTime.start = shifted)', () => {
    const { gql, calls } = captureGql();
    buildMoveScopeCommand(gql, facts({ repeating: true, multi: true }), 'serie', move)
      .execute()
      .subscribe();
    expect(calls[0].query).toContain('moveAppointment');
    expect(calls[0].vars).toEqual({
      id: 'a-1',
      occ: '2031-03-05T10:00:00',
      target: { dateTime: { start: '2031-03-06T11:30:00' } },
    });
  });

  it('MOVE / SINGLE repeating → splitOccurrence(occurrence, shifted start)', () => {
    const { gql, calls } = captureGql();
    buildMoveScopeCommand(gql, facts({ repeating: true }), 'single', move).execute().subscribe();
    expect(calls[0].query).toContain('splitOccurrence');
    expect(calls[0].vars).toEqual({
      id: 'a-1',
      occ: '2031-03-05T10:00:00',
      target: { dateTime: { start: '2031-03-06T11:30:00' } },
    });
  });

  it('MOVE / SINGLE non-repeating → moveAppointment (nothing to split)', () => {
    const { gql, calls } = captureGql();
    buildMoveScopeCommand(gql, facts({ multi: true }), 'single', move).execute().subscribe();
    expect(calls[0].query).toContain('moveAppointment');
  });

  it('RESIZE / SERIE → moveAppointment(start = occurrence, end = newEnd)', () => {
    const { gql, calls } = captureGql();
    const resize: MoveGesture = {
      kind: 'resize',
      newEnd: '2031-03-05T11:30:00',
      oldEnd: '2031-03-05T11:00:00',
    };
    buildMoveScopeCommand(gql, facts({ repeating: true }), 'serie', resize).execute().subscribe();
    expect(calls[0].query).toContain('moveAppointment');
    expect(calls[0].vars).toEqual({
      id: 'a-1',
      occ: '2031-03-05T10:00:00',
      target: { dateTime: { start: '2031-03-05T10:00:00', end: '2031-03-05T11:30:00' } },
    });
  });

  it('RESIZE / SINGLE repeating → splitOccurrence(start = occurrence, end = newEnd)', () => {
    const { gql, calls } = captureGql();
    const resize: MoveGesture = {
      kind: 'resize',
      newEnd: '2031-03-05T11:30:00',
      oldEnd: '2031-03-05T11:00:00',
    };
    buildMoveScopeCommand(gql, facts({ repeating: true }), 'single', resize).execute().subscribe();
    expect(calls[0].query).toContain('splitOccurrence');
    expect((calls[0].vars as { target: unknown }).target).toEqual({
      dateTime: { start: '2031-03-05T10:00:00', end: '2031-03-05T11:30:00' },
    });
  });
});
