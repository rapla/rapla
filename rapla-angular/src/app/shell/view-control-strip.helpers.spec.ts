import { describe, it, expect } from 'vitest';
import {
  shiftLocalDateTime,
  datePart,
  weekStartLocalDateTime,
  daysBetween,
  todayWindow,
} from './view-control-strip.component';

describe('shiftLocalDateTime', () => {
  it('shifts by whole days, preserving the time part', () => {
    expect(shiftLocalDateTime('2026-06-15T08:30:00', 7)).toBe('2026-06-22T08:30:00');
    expect(shiftLocalDateTime('2026-06-01T00:00:00', -1)).toBe('2026-05-31T00:00:00');
  });

  it('crosses year boundaries', () => {
    expect(shiftLocalDateTime('2026-12-31T00:00:00', 1)).toBe('2027-01-01T00:00:00');
  });
});

describe('datePart', () => {
  it('returns the date prefix', () => {
    expect(datePart('2026-06-15T08:30:00')).toBe('2026-06-15');
  });
});

describe('weekStartLocalDateTime', () => {
  it('returns Monday 00:00 of the given week', () => {
    expect(weekStartLocalDateTime(new Date('2026-06-17T12:00:00Z'))).toBe('2026-06-15T00:00:00'); // Wed → Mon
    expect(weekStartLocalDateTime(new Date('2026-06-15T23:00:00Z'))).toBe('2026-06-15T00:00:00'); // Mon → Mon
    expect(weekStartLocalDateTime(new Date('2026-06-21T01:00:00Z'))).toBe('2026-06-15T00:00:00'); // Sun → Mon
  });
});

describe('daysBetween', () => {
  it('counts whole days between date parts', () => {
    expect(daysBetween('2026-06-15T00:00:00', '2026-06-22T00:00:00')).toBe(7);
    expect(daysBetween('2025-06-21T00:00:00', '2027-06-21T00:00:00')).toBe(730);
  });
});

describe('todayWindow', () => {
  it('re-anchors a 7-day window to this week (Mon→next Mon)', () => {
    const w = todayWindow(
      { from: '2026-01-05T00:00:00', to: '2026-01-12T00:00:00' },
      new Date('2026-06-17T12:00:00Z'),
    );
    expect(w).toEqual({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
  });

  it('preserves the span of a wide window', () => {
    const w = todayWindow(
      { from: '2025-01-01T00:00:00', to: '2025-01-31T00:00:00' }, // 30 days
      new Date('2026-06-17T12:00:00Z'),
    );
    expect(daysBetween(w.from, w.to)).toBe(30);
    expect(w.from).toBe('2026-06-15T00:00:00');
  });
});
