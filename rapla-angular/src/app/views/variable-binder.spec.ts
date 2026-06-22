import { describe, it, expect } from 'vitest';
import { buildVariablesByType } from './variable-binder';
import type { ViewVariable } from '../graphql/graphql.service';

const W = { from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' };
const RES = ['a1', 'a2'];

const v = (name: string, type: string): ViewVariable => ({ name, type });

describe('buildVariablesByType', () => {
  it('fills a ReservationFilter with the window (no selection)', () => {
    expect(buildVariablesByType([v('filter', 'ReservationFilter!')], { window: W, resourceIds: [] })).toEqual(
      { filter: { from: W.from, to: W.to } },
    );
  });

  it('Raumauslastung: BOTH variables get the selection, by type', () => {
    const vars = buildVariablesByType(
      [v('filter', 'ReservationFilter!'), v('allocatableFilter', 'AllocatableFilter!')],
      { window: W, resourceIds: RES },
    );
    expect(vars).toEqual({
      filter: { from: W.from, to: W.to, allocatableMatching: { idIn: RES } },
      allocatableFilter: { idIn: RES },
    });
  });

  it('an AllocatableFilter variable can be named anything (binds by TYPE, not name)', () => {
    expect(buildVariablesByType([v('rooms', 'AllocatableFilter!')], { window: W, resourceIds: RES })).toEqual(
      { rooms: { idIn: RES } },
    );
  });

  it('an AllocatableFilter with no selection is omitted (server default applies)', () => {
    expect(buildVariablesByType([v('rooms', 'AllocatableFilter!')], { window: W, resourceIds: [] })).toEqual(
      {},
    );
  });

  it('unknown variable types are left unset (graceful → server default)', () => {
    expect(
      buildVariablesByType([v('userId', 'ID!'), v('filter', 'ReservationFilter!')], {
        window: W,
        resourceIds: RES,
      }),
    ).toEqual({ filter: { from: W.from, to: W.to, allocatableMatching: { idIn: RES } } });
  });

  it('null window → ReservationFilter is omitted (first load, server merges defaults)', () => {
    expect(buildVariablesByType([v('filter', 'ReservationFilter!')], { window: null, resourceIds: RES })).toEqual(
      {},
    );
  });
});
