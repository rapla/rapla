import { describe, it, expect } from 'vitest';
import { buildVariablesByType, scopeOf } from './variable-binder';
import type { ViewVariable } from '../graphql/graphql.service';

const W = { from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' };
const RES = ['a1', 'a2'];

const v = (name: string, type: string): ViewVariable => ({ name, type });

describe('buildVariablesByType', () => {
  it('fills a ReservationFilter with the window (no selection)', () => {
    expect(
      buildVariablesByType([v('filter', 'ReservationFilter!')], { window: W, resourceIds: [] }),
    ).toEqual({ filter: { from: W.from, to: W.to } });
  });

  it('Raumauslastung: BOTH variables get the selection, by type', () => {
    const vars = buildVariablesByType(
      [v('filter', 'ReservationFilter!'), v('resourceFilter', 'ResourceFilter!')],
      { window: W, resourceIds: RES },
    );
    expect(vars).toEqual({
      filter: { from: W.from, to: W.to, resourceMatching: { idIn: RES } },
      resourceFilter: { idIn: RES },
    });
  });

  it('an ResourceFilter variable can be named anything (binds by TYPE, not name)', () => {
    expect(
      buildVariablesByType([v('rooms', 'ResourceFilter!')], { window: W, resourceIds: RES }),
    ).toEqual({ rooms: { idIn: RES } });
  });

  it('an ResourceFilter with no selection is omitted (server default applies)', () => {
    expect(
      buildVariablesByType([v('rooms', 'ResourceFilter!')], { window: W, resourceIds: [] }),
    ).toEqual({});
  });

  it('unknown variable types are left unset (graceful → server default)', () => {
    expect(
      buildVariablesByType([v('userId', 'ID!'), v('filter', 'ReservationFilter!')], {
        window: W,
        resourceIds: RES,
      }),
    ).toEqual({ filter: { from: W.from, to: W.to, resourceMatching: { idIn: RES } } });
  });

  it('null window → ReservationFilter is omitted (first load, server merges defaults)', () => {
    expect(
      buildVariablesByType([v('filter', 'ReservationFilter!')], { window: null, resourceIds: RES }),
    ).toEqual({});
  });

  it('user scopes bind into ReservationFilter.ownerIn ("my events")', () => {
    expect(
      buildVariablesByType([v('filter', 'ReservationFilter!')], {
        window: W,
        resourceIds: [],
        ownerIds: ['u-42'],
      }),
    ).toEqual({ filter: { from: W.from, to: W.to, ownerIn: ['u-42'] } });
  });

  it('user + resource scope are both sent (the server unions them, PRD 123 D10)', () => {
    expect(
      buildVariablesByType([v('filter', 'ReservationFilter!')], {
        window: W,
        resourceIds: RES,
        ownerIds: ['u-42', 'u-7'],
      }),
    ).toEqual({
      filter: { from: W.from, to: W.to, resourceMatching: { idIn: RES }, ownerIn: ['u-42', 'u-7'] },
    });
  });

  it('scopeOf splits the chips: every user chip is an owner, no first-chip pick', () => {
    expect(
      scopeOf([
        { id: 'r1', kind: 'resource', label: 'R1' },
        { id: 'u-42', kind: 'user', label: 'A' },
        { id: 'e1', kind: 'event', label: 'E' },
        { id: 'u-7', kind: 'user', label: 'B' },
      ]),
    ).toEqual({ resourceIds: ['r1'], ownerIds: ['u-42', 'u-7'] });
  });
  it('PRD 128 D1 — a conflict or request chip scopes its resource (deduped)', () => {
    expect(
      scopeOf([
        { id: 'CONFLICT;r1;a1;a2', kind: 'conflict', label: 'K' },
        { id: 'CONFLICT;r1;a3;a4', kind: 'conflict', label: 'K2' },
        { id: 'REQUEST;r2;v1', kind: 'request', label: 'A' },
      ]),
    ).toEqual({ resourceIds: ['r1', 'r2'], ownerIds: [] });
  });

  it('PRD 128 D7 — a ConflictFilter gets the scoped resources and the window; a ResourceRequestFilter the resources', () => {
    expect(
      buildVariablesByType(
        [
          v('filter', 'ReservationFilter!'),
          v('conflicts', 'ConflictFilter'),
          v('requests', 'ResourceRequestFilter'),
        ],
        { window: W, resourceIds: RES },
      ),
    ).toEqual({
      filter: { from: W.from, to: W.to, resourceMatching: { idIn: RES } },
      conflicts: { resourceIdsIn: RES, from: W.from, to: W.to },
      requests: { resourceIdsIn: RES },
    });
  });

  it('without a selection both stay unset (the server default; the view host fires no query anyway)', () => {
    expect(
      buildVariablesByType(
        [v('conflicts', 'ConflictFilter'), v('requests', 'ResourceRequestFilter')],
        {
          window: W,
          resourceIds: [],
        },
      ),
    ).toEqual({});
  });
});
