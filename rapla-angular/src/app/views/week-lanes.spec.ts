import { describe, expect, it } from 'vitest';

import { layoutWeek, mondayOf, weekDays, weekGridWindow, type LaneOptions } from './week-lanes';

interface Blk {
  start: string;
  end: string;
  allocs?: { id: string; name: string }[];
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

  it('fixed mode reserves a lane per selected resource even on empty days', () => {
    const result = lay([], scoped);
    expect(day(result, '2026-06-08').lanes).toBe(2);
  });

  it('each selected resource keeps ITS lane, stable across days', () => {
    const result = lay(
      [
        // only room B booked on Monday, only room A on Tuesday — no overlap anywhere
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [ROOM_B] },
        { start: '2026-06-09T09:00:00', end: '2026-06-09T10:00:00', allocs: [ROOM_A] },
      ],
      scoped,
    );
    // Raum A sorts before Raum B → lane 0 = A, lane 1 = B on EVERY day
    expect(day(result, '2026-06-08').blocks[0].lane).toBe(1);
    expect(day(result, '2026-06-09').blocks[0].lane).toBe(0);
    expect(day(result, '2026-06-08').lanes).toBe(2);
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

  it('a block matching no selected resource falls back to its own allocatable group', () => {
    const other = { id: 'rX', name: 'Zzz Extern' };
    const result = lay(
      [{ start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00', allocs: [other] }],
      scoped,
    );
    const d = day(result, '2026-06-08');
    // Raum A, Raum B, then Zzz Extern (name-sorted) → lane 2
    expect(d.lanes).toBe(3);
    expect(d.blocks[0].lane).toBe(2);
  });

  it('a block with no allocatables lands in a trailing group', () => {
    const result = lay(
      [{ start: '2026-06-08T09:00:00', end: '2026-06-08T10:00:00' }],
      scoped,
    );
    const d = day(result, '2026-06-08');
    expect(d.lanes).toBe(3);
    expect(d.blocks[0].lane).toBe(2);
  });

  it('container-chip fallback: blocks matching no selected id group per ROOM, packing sequential lectures into one dense lane (Swing building-selection parity)', () => {
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
        // room A hosts two sequential lectures by DIFFERENT lecturers
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:30:00', allocs: [lecturer1, roomA] },
        { start: '2026-06-08T10:45:00', end: '2026-06-08T12:15:00', allocs: [lecturer2, roomA] },
        // room B in parallel
        { start: '2026-06-08T09:00:00', end: '2026-06-08T10:30:00', allocs: [lecturer2, roomB] },
      ],
      building,
    );
    const d = day(result, '2026-06-08');
    // building lane (empty) + room A lane (both lectures stacked) + room B lane
    expect(d.lanes).toBe(3);
    const laneOf = (start: string) => d.blocks.find((b) => (b.row as Blk).start === start)?.lane;
    expect(laneOf('2026-06-08T09:00:00')).toBe(laneOf('2026-06-08T10:45:00')); // room A packs vertically
  });
});
