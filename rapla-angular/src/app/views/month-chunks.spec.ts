import { describe, expect, it } from 'vitest';
import {
  chunkWeek,
  ITEM_GAP,
  ITEM_H,
  monthGridDays,
  monthGridWindow,
  TOP0,
  WeekChunk,
} from './month-chunks';

interface Row {
  id: string;
  start: string;
  end: string;
}

const row = (id: string, start: string, end = start): Row => ({ id, start, end });

const chunk = (rows: Row[], weekDays: string[]): WeekChunk<Row>[] =>
  chunkWeek(
    rows,
    weekDays,
    (r) => r.start,
    (r) => r.end,
  );

const byId = (chunks: WeekChunk<Row>[], id: string): WeekChunk<Row> => {
  const found = chunks.find((c) => c.row.id === id);
  if (!found) {
    throw new Error(`no chunk for ${id}`);
  }
  return found;
};

const WEEK = [
  '2026-07-06',
  '2026-07-07',
  '2026-07-08',
  '2026-07-09',
  '2026-07-10',
  '2026-07-11',
  '2026-07-12',
];

describe('monthGridWindow', () => {
  it('spans Monday before the 1st to the day after the grid Sunday for July 2026', () => {
    expect(monthGridWindow('2026-07-15')).toEqual({
      from: '2026-06-29T00:00:00',
      to: '2026-08-03T00:00:00',
    });
  });

  it('wraps the year for January 2027', () => {
    expect(monthGridWindow('2027-01-10T12:30:00')).toEqual({
      from: '2026-12-28T00:00:00',
      to: '2027-02-01T00:00:00',
    });
  });

  it('has no leading fill for a month starting on Monday (June 2026)', () => {
    expect(monthGridWindow('2026-06-01')).toEqual({
      from: '2026-06-01T00:00:00',
      to: '2026-07-06T00:00:00',
    });
  });
});

describe('monthGridDays', () => {
  it('yields 5 Monday-first weeks of 7 days for July 2026', () => {
    const weeks = monthGridDays('2026-07-15');
    expect(weeks).toHaveLength(5);
    for (const week of weeks) {
      expect(week).toHaveLength(7);
    }
    expect(weeks[0][0]).toBe('2026-06-29');
    expect(weeks[0][6]).toBe('2026-07-05');
    expect(weeks[4][0]).toBe('2026-07-27');
    expect(weeks[4][6]).toBe('2026-08-02');
  });
});

describe('chunkWeek', () => {
  it('stacks single-day rows downward in one column', () => {
    const chunks = chunk([row('a', '2026-07-07'), row('b', '2026-07-07')], WEEK);
    expect(byId(chunks, 'a')).toMatchObject({ col: 1, span: 1, top: TOP0, bottom: TOP0 + ITEM_H });
    expect(byId(chunks, 'b')).toMatchObject({
      col: 1,
      span: 1,
      top: TOP0 + ITEM_H + ITEM_GAP,
      bottom: TOP0 + 2 * ITEM_H + ITEM_GAP,
    });
  });

  it('splits a Fr→Di row into two chunks across weeks with continuation flags', () => {
    const rows = [row('x', '2026-07-10', '2026-07-14')];
    const first = chunk(rows, WEEK);
    expect(first).toHaveLength(1);
    expect(first[0]).toMatchObject({ col: 4, span: 3, contLeft: false, contRight: true });
    const nextWeek = [
      '2026-07-13',
      '2026-07-14',
      '2026-07-15',
      '2026-07-16',
      '2026-07-17',
      '2026-07-18',
      '2026-07-19',
    ];
    const second = chunk(rows, nextWeek);
    expect(second).toHaveLength(1);
    expect(second[0]).toMatchObject({ col: 0, span: 2, contLeft: true, contRight: false });
  });

  it('pushes a colliding Mi–Do bar below a Mo–Fr bar', () => {
    const chunks = chunk(
      [row('short', '2026-07-08', '2026-07-09'), row('long', '2026-07-06', '2026-07-10')],
      WEEK,
    );
    expect(byId(chunks, 'long')).toMatchObject({ col: 0, span: 5, top: TOP0 });
    expect(byId(chunks, 'short')).toMatchObject({
      col: 2,
      span: 2,
      top: TOP0 + ITEM_H + ITEM_GAP,
      bottom: TOP0 + 2 * ITEM_H + ITEM_GAP,
    });
  });

  it('lands single-day rows in covered columns below passing bars', () => {
    const chunks = chunk([row('bar', '2026-07-06', '2026-07-10'), row('s', '2026-07-07')], WEEK);
    expect(byId(chunks, 'bar').top).toBe(TOP0);
    expect(byId(chunks, 's')).toMatchObject({ col: 1, span: 1, top: TOP0 + ITEM_H + ITEM_GAP });
  });

  it('ignores rows outside the week', () => {
    const chunks = chunk(
      [row('before', '2026-06-30', '2026-07-05'), row('after', '2026-07-13', '2026-07-20')],
      WEEK,
    );
    expect(chunks).toHaveLength(0);
  });

  it('treats the end date as inclusive (start==end==weekDays[0] → col 0 span 1)', () => {
    const chunks = chunk([row('a', '2026-07-06')], WEEK);
    expect(chunks).toHaveLength(1);
    expect(chunks[0]).toMatchObject({
      col: 0,
      span: 1,
      contLeft: false,
      contRight: false,
      top: TOP0,
      bottom: TOP0 + ITEM_H,
    });
  });
});
