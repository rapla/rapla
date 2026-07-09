import { describe, expect, it } from 'vitest';

import {
  dayGridWindow,
  layoutWeek,
  mondayOf,
  weekDays,
  weekGridWindow,
  type LaneOptions,
} from './week-lanes';

interface Blk {
  start: string;
  end: string;
  allocs?: { id: string; name: string; isLocation?: boolean }[];
  matchedBy?: { id: string; name: string }[];
}

const startOf = (r: Blk) => r.start;
const endOf = (r: Blk) => r.end;

/** Week under test: Mo 2026-06-08 … So 2026-06-14. */
const ANCHOR = '2026-06-10T00:00:00';

function lay(rows: Blk[], options?: LaneOptions<Blk>) {
  return layoutWeek(rows, ANCHOR, startOf, endOf, options);
}

function day(result: ReturnType<typeof lay>, iso: string) {
  const d = result.days.find((x) => x.day === iso);
  if (!d) throw new Error(`day ${iso} missing`);
  return d;
}

describe('week window math', () => {
  it('mondayOf returns the Monday of the ISO week', () => {
    expect(mondayOf('2026-06-10')).toBe('2026-06-08'); // Wednesday
    expect(mondayOf('2026-06-08')).toBe('2026-06-08'); // Monday itself
    expect(mondayOf('2026-06-14')).toBe('2026-06-08'); // Sunday
  });

  it('mondayOf wraps the year boundary', () => {
    expect(mondayOf('2026-01-01')).toBe('2025-12-29');
  });

  it('weekDays yields Mo–So', () => {
    expect(weekDays('2026-06-10')).toEqual([
      '2026-06-08',
      '2026-06-09',
      '2026-06-10',
      '2026-06-11',
      '2026-06-12',
      '2026-06-13',
      '2026-06-14',
    ]);
  });

  it('weekGridWindow is Monday 00:00 → next Monday 00:00', () => {
    expect(weekGridWindow('2026-12-31T12:00:00')).toEqual({
      from: '2026-12-28T00:00:00',
      to: '2027-01-04T00:00:00',
    });
  });

  it('dayGridWindow is the anchor day 00:00 → next day 00:00', () => {
    expect(dayGridWindow('2026-12-31T12:00:00')).toEqual({
      from: '2026-12-31T00:00:00',
      to: '2027-01-01T00:00:00',
    });
  });
});

describe('day mode (single-day layout)', () => {
  it('renders only the anchor day when given a one-day list', () => {
    const result = layoutWeek(
      [{ start: '2026-06-10T09:00:00', end: '2026-06-10T10:00:00' }],
      '2026-06-10T00:00:00',
      (r) => r.start,
      (r) => r.end,
      undefined,
      ['2026-06-10'], // day mode: just the anchor day, not Mo–So
    );
    expect(result.days.map((d) => d.day)).toEqual(['2026-06-10']);
    expect(result.days[0].blocks).toHaveLength(1);
  });
});

describe('day clipping', () => {
  it('a multi-day block is clipped per day with continuation flags', () => {
    const r = { start: '2026-06-12T22:00:00', end: '2026-06-14T10:00:00' };
    const result = lay([r]);
    const fr = day(result, '2026-06-12').blocks[0];
    expect([fr.startMin, fr.endMin, fr.contLeft, fr.contRight]).toEqual([
      22 * 60,
      24 * 60,
      false,
      true,
    ]);
    const sa = day(result, '2026-06-13').blocks[0];
    expect([sa.startMin, sa.endMin, sa.contLeft, sa.contRight]).toEqual([0, 24 * 60, true, true]);
    const so = day(result, '2026-06-14').blocks[0];
    expect([so.startMin, so.endMin, so.contLeft, so.contRight]).toEqual([0, 10 * 60, true, false]);
  });

  it('a block ending at T00:00:00 does not cover that day', () => {
    const r = { start: '2026-06-12T20:00:00', end: '2026-06-13T00:00:00' };
    const result = lay([r]);
    expect(day(result, '2026-06-13').blocks).toHaveLength(0);
    const fr = day(result, '2026-06-12').blocks[0];
    expect(fr.contRight).toBe(false);
  });
});

describe('lane packing (compact, no scope)', () => {
  it('disjoint blocks share one lane', () => {
    const result = lay([
      { start: '2026-06-10T09:00:00', end: '2026-06-10T10:00:00' },
      { start: '2026-06-10T11:00:00', end: '2026-06-10T12:00:00' },
    ]);
    const d = day(result, '2026-06-10');
    expect(d.lanes).toBe(1);
  });

  it('touching blocks (end == start) share one lane', () => {
    const result = lay([
      { start: '2026-06-10T09:00:00', end: '2026-06-10T10:00:00' },
      { start: '2026-06-10T10:00:00', end: '2026-06-10T11:00:00' },
    ]);
    expect(day(result, '2026-06-10').lanes).toBe(1);
  });

  it('overlapping blocks open lanes for the max overlap', () => {
    const result = lay([
      { start: '2026-06-10T09:00:00', end: '2026-06-10T12:00:00' },
      { start: '2026-06-10T10:00:00', end: '2026-06-10T11:00:00' },
      { start: '2026-06-10T10:30:00', end: '2026-06-10T13:00:00' },
    ]);
    expect(day(result, '2026-06-10').lanes).toBe(3);
  });

  it('5-minute collision floor: two zero-length blocks at the same time collide', () => {
    const result = lay([
      { start: '2026-06-10T09:00:00', end: '2026-06-10T09:00:00' },
      { start: '2026-06-10T09:00:00', end: '2026-06-10T09:00:00' },
    ]);
    expect(day(result, '2026-06-10').lanes).toBe(2);
  });
});

describe('group-by-selected-resource (Swing GroupAllocatablesStrategy)', () => {
  const ROOM_A = { id: 'rA', name: 'Raum A' };
  const ROOM_B = { id: 'rB', name: 'Raum B' };
  const scoped: LaneOptions<Blk> = {
    selected: [ROOM_A, ROOM_B],
    mode: 'fixed',
    allocsOf: (r) => r.allocs ?? [],
  };

  it('an empty week collapses to a single empty lane (no per-resource phantom reservation)', () => {
    const result = lay([], scoped);
    expect(day(result, '2026-06-08').lanes).toBe(1);
  });

  it('a co-selected resource with NO blocks all week gets no lane (matchedBy groups elsewhere)', () => {
    const building = { id: 'building-1', name: 'Schloss 2' };
    const person = { id: 'p1', name: 'Kohlhaas' };
    const roomA = { id: 'roomA', name: 'S2/1160', isLocation: true };
    // building + person both selected; every block binds to the building via matchedBy
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [roomA, person], matchedBy: [building] },
        { start: '2026-06-08T11:00:00', end: '2026-06-08T12:00:00', allocs: [roomA, person], matchedBy: [building] },
      ],
      {
        selected: [building, person],
        mode: 'fixed',
        allocsOf: (r) => r.allocs ?? [],
        matchedByOf: (r) => r.matchedBy ?? [],
      },
    );
    // one building lane (non-colliding blocks stack in it); NO empty person lane
    expect(day(result, '2026-06-08').lanes).toBe(1);
  });

  it('each day packs only its own booked resources — no reserved empty lane for absent ones', () => {
    const result = lay(
      [
        // only room B booked on Monday, only room A on Tuesday — no overlap anywhere
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [ROOM_B] },
        { start: '2026-06-09T09:00:00', end: '2026-06-09T10:00:00', allocs: [ROOM_A] },
      ],
      scoped,
    );
    // Monday has only B, Tuesday only A → each day a SINGLE lane (the absent room
    // reserves nothing — no dead column). Lane stability is not kept (the grid has no
    // per-lane labels, so it has no visual value and would only add empty columns).
    expect(day(result, '2026-06-08').lanes).toBe(1);
    expect(day(result, '2026-06-08').blocks[0].lane).toBe(0);
    expect(day(result, '2026-06-09').lanes).toBe(1);
    expect(day(result, '2026-06-09').blocks[0].lane).toBe(0);
  });

  it('a conflict within one resource opens an extra lane right after it', () => {
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T12:00:00', allocs: [ROOM_A] },
        { start: '2026-06-08T10:00:00', end: '2026-06-08T11:00:00', allocs: [ROOM_A] },
        { start: '2026-06-08T09:30:00', end: '2026-06-08T09:45:00', allocs: [ROOM_B] },
      ],
      scoped,
    );
    const d = day(result, '2026-06-08');
    expect(d.lanes).toBe(3); // A, A-overflow, B
    const laneOf = (start: string) =>
      d.blocks.find((b) => (b.row as Blk).start === start)?.lane;
    expect(laneOf('2026-06-08T09:00:00')).toBe(0); // first A block
    expect(laneOf('2026-06-08T10:00:00')).toBe(1); // colliding A block → inserted lane
    expect(laneOf('2026-06-08T09:30:00')).toBe(2); // Room B pushed right by the conflict lane
  });

  it('compact mode merges non-colliding lanes (Swing mergeSlots)', () => {
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [ROOM_A] },
        { start: '2026-06-08T11:00:00', end: '2026-06-08T12:00:00', allocs: [ROOM_B] },
      ],
      { ...scoped, mode: 'compact' },
    );
    expect(day(result, '2026-06-08').lanes).toBe(1);
  });

  it('a block matching no selected resource falls back to its own allocatable group (others DO match)', () => {
    const other = { id: 'rX', name: 'Zzz Extern' };
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [ROOM_A] },
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [other] },
      ],
      scoped,
    );
    const d = day(result, '2026-06-08');
    // Raum A (has a block), then Zzz Extern (name-sorted) — Raum B is selected but has NO
    // block this week, so it gets no phantom lane → 2 lanes, rX at lane 1.
    expect(d.lanes).toBe(2);
    const laneOf = (start: string, alloc: string) =>
      d.blocks.find((b) => (b.row as Blk).allocs?.[0].id === alloc)?.lane;
    expect(laneOf('2026-06-08T09:00:00', 'rX')).toBe(1);
  });

  it('a block with no allocatables lands in a trailing group (others DO match)', () => {
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [ROOM_A] },
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00' },
      ],
      scoped,
    );
    const d = day(result, '2026-06-08');
    // Raum A + the no-allocatable trailing group; Raum B (empty) reserves no lane → 2.
    expect(d.lanes).toBe(2);
    expect(d.blocks.find((b) => !(b.row as Blk).allocs)?.lane).toBe(1);
  });

  it('container chip (no block matches any selected id) → COMPACT dense packing (Swing: empty builder allocatables ⇒ compactColumns)', () => {
    const building: LaneOptions<Blk> = {
      selected: [{ id: 'building-1', name: 'Schloss 2' }], // container — no block carries it
      mode: 'fixed',
      allocsOf: (r) => r.allocs ?? [],
    };
    const roomA = { id: 'roomA', name: 'S2/1160 Seminarraum', isLocation: true };
    const roomB = { id: 'roomB', name: 'S2/2190 Seminarraum', isLocation: true };
    const lecturer1 = { id: 'p1', name: 'Appel, Jürgen' };
    const lecturer2 = { id: 'p2', name: 'Gillig, Thomas' };
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:30:00', allocs: [lecturer1, roomA] },
        { start: '2026-06-08T10:45:00', end: '2026-06-08T12:15:00', allocs: [lecturer2, roomA] },
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:30:00', allocs: [lecturer2, roomB] },
      ],
      building,
    );
    const d = day(result, '2026-06-08');
    // greedy time packing: max overlap is 2 — NO reserved container lane,
    // NO per-room/per-lecturer lanes; the 10:45 block stacks under a 09:00 one
    expect(d.lanes).toBe(2);
    const laneOf = (start: string) => d.blocks.find((b) => (b.row as Blk).start === start)?.lane;
    expect(laneOf('2026-06-08T10:45:00')).toBe(0);
  });

  it('server matchedBy is authoritative: a building groups ALL its rooms into ONE lane', () => {
    // The dhbw case: a BUILDING is scoped; the server resolves belongsTo and stamps every
    // block with matchedBy=[building]. The client can't derive this from row cells (blocks
    // carry rooms/lecturers, not the building) — matchedBy[0] is the grouping key.
    const building = { id: 'building-1', name: 'Schloss 2' };
    const roomA = { id: 'roomA', name: 'S2/1160', isLocation: true };
    const roomB = { id: 'roomB', name: 'S2/2190', isLocation: true };
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:30:00', allocs: [roomA], matchedBy: [building] },
        { start: '2026-06-08T11:00:00', end: '2026-06-08T12:00:00', allocs: [roomB], matchedBy: [building] },
      ],
      { selected: [], mode: 'fixed', allocsOf: (r) => r.allocs ?? [], matchedByOf: (r) => r.matchedBy ?? [] },
    );
    const d = day(result, '2026-06-08');
    // one group (the building), non-colliding → still one lane in fixed mode
    expect(d.lanes).toBe(1);
  });

  it('matchedBy per-room: distinct provenance keeps a lane per matched room', () => {
    const roomA = { id: 'roomA', name: 'Raum A' };
    const roomB = { id: 'roomB', name: 'Raum B' };
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [roomA], matchedBy: [roomA] },
        { start: '2026-06-08T11:00:00', end: '2026-06-08T12:00:00', allocs: [roomB], matchedBy: [roomB] },
      ],
      { selected: [roomA, roomB], mode: 'fixed', allocsOf: (r) => r.allocs ?? [], matchedByOf: (r) => r.matchedBy ?? [] },
    );
    // two distinct matchedBy keys → two fixed lanes despite no time overlap
    expect(day(result, '2026-06-08').lanes).toBe(2);
  });

  it('empty matchedBy everywhere → compact (Swing: admitted by a non-resource criterion)', () => {
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', matchedBy: [] },
        { start: '2026-06-08T11:00:00', end: '2026-06-08T12:00:00', matchedBy: [] },
      ],
      { selected: [], mode: 'fixed', allocsOf: (r) => r.allocs ?? [], matchedByOf: (r) => r.matchedBy ?? [] },
    );
    expect(day(result, '2026-06-08').lanes).toBe(1);
  });

  it('a partial match keeps fixed grouping (selected room matched by some blocks)', () => {
    const roomA = { id: 'roomA', name: 'Raum A', isLocation: true };
    const result = lay(
      [
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [roomA] },
        { start: '2026-06-08T11:00:00', end: '2026-06-08T12:00:00', allocs: [{ id: 'rX', name: 'Zzz', isLocation: true }] },
      ],
      { selected: [roomA], mode: 'fixed', allocsOf: (r) => r.allocs ?? [] },
    );
    const d = day(result, '2026-06-08');
    expect(d.lanes).toBe(2); // Raum A lane + Zzz fallback lane, despite no overlap
  });
});
