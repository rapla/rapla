import { describe, expect, it } from 'vitest';

import { groupByWeekday, groupByColumn } from './weekday-grouping';

describe('groupByColumn', () => {
  it('buckets rows by a column value, preserving first-seen + within-group order', () => {
    const rows = [
      { day: 'Montag', n: 1 },
      { day: 'Dienstag', n: 2 },
      { day: 'Montag', n: 3 },
    ];
    const groups = groupByColumn(rows, 'day');
    expect(groups.map((g) => g.label)).toEqual(['Montag', 'Dienstag']);
    expect(groups[0].rows.map((r) => r['n'])).toEqual([1, 3]);
  });

  it('collects missing/empty values into a trailing "—" group', () => {
    const groups = groupByColumn([{ day: 'Montag' }, {}, { day: '' }], 'day');
    expect(groups.map((g) => g.label)).toEqual(['Montag', '—']);
    expect(groups[1].rows.length).toBe(2);
  });
});

describe('groupByWeekday', () => {
  it('buckets rows by weekday in Montag→Sonntag order', () => {
    const rows = [
      { start: '2026-06-17T10:00:00', title: 'wed' }, // Mittwoch
      { start: '2026-06-15T08:00:00', title: 'mon' }, // Montag
      { start: '2026-06-21T14:00:00', title: 'sun' }, // Sonntag
    ];

    const groups = groupByWeekday(rows);

    expect(groups.map((g) => [g.weekday, g.label])).toEqual([
      [1, 'Montag'],
      [3, 'Mittwoch'],
      [7, 'Sonntag'],
    ]);
    expect(groups[0].rows).toEqual([{ start: '2026-06-15T08:00:00', title: 'mon' }]);
    expect(groups[1].rows).toEqual([{ start: '2026-06-17T10:00:00', title: 'wed' }]);
    expect(groups[2].rows).toEqual([{ start: '2026-06-21T14:00:00', title: 'sun' }]);
  });

  it('omits weekdays that have no rows', () => {
    const rows = [{ start: '2026-06-15T08:00:00' }, { start: '2026-06-21T14:00:00' }];

    const groups = groupByWeekday(rows);

    expect(groups.map((g) => g.weekday)).toEqual([1, 7]);
  });

  it('preserves input order of rows within a group (stable)', () => {
    const rows = [
      { start: '2026-06-15T09:00:00', id: 'a' },
      { start: '2026-06-15T08:00:00', id: 'b' },
      { start: '2026-06-15T12:00:00', id: 'c' },
    ];

    const groups = groupByWeekday(rows);

    expect(groups).toHaveLength(1);
    expect(groups[0].rows.map((r) => r['id'])).toEqual(['a', 'b', 'c']);
  });

  it('collects rows with missing/unparseable dates into a trailing "Ohne Datum" group', () => {
    const rows = [
      { start: '2026-06-21T14:00:00', id: 'sun' },
      { start: 'not-a-date', id: 'bad' },
      { id: 'missing' },
      { start: '2026-02-31T10:00:00', id: 'invalid' }, // Feb 31 → rejected
      { start: '2026-06-15T08:00:00', id: 'mon' },
    ];

    const groups = groupByWeekday(rows);

    expect(groups.map((g) => g.weekday)).toEqual([1, 7, 0]);
    const noDate = groups[groups.length - 1];
    expect(noDate.label).toBe('Ohne Datum');
    expect(noDate.rows.map((r) => r['id'])).toEqual(['bad', 'missing', 'invalid']);
  });

  it('omits the "Ohne Datum" group when every row has a valid date', () => {
    const groups = groupByWeekday([{ start: '2026-06-15T08:00:00' }]);

    expect(groups.map((g) => g.weekday)).toEqual([1]);
  });

  it('reads the date from a custom alias', () => {
    const rows = [
      { begin: '2026-06-17T10:00:00', id: 'wed' },
      { begin: '2026-06-15T08:00:00', id: 'mon' },
    ];

    const groups = groupByWeekday(rows, 'begin');

    expect(groups.map((g) => g.weekday)).toEqual([1, 3]);
    expect(groups[0].rows[0]['id']).toBe('mon');
  });
});
