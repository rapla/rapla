import { describe, expect, it } from 'vitest';

import type { RepeatingRule } from './event-draft';
import {
  defaultRule,
  endModeOf,
  raplaWeekday,
  ruleSummary,
  toggleException,
  toggleWeekday,
  withCount,
  withEndMode,
  withInterval,
  withUntil,
} from './repeating-edit';

// 2026-07-07 is a Tuesday → rapla weekday 3 (1=So … 7=Sa).
const START = '2026-07-07T10:00:00';

describe('repeating-edit (PRD 091 Phase 4.2, pure)', () => {
  it('raplaWeekday uses the core 1=Sunday…7=Saturday convention', () => {
    expect(raplaWeekday('2026-07-05T00:00:00')).toBe(1); // Sunday
    expect(raplaWeekday(START)).toBe(3); // Tuesday
    expect(raplaWeekday('2026-07-11T00:00:00')).toBe(7); // Saturday
  });

  it('defaultRule WEEKLY seeds the start weekday, FOREVER ending', () => {
    const rule = defaultRule('WEEKLY', START);
    expect(rule).toEqual({
      type: 'WEEKLY',
      interval: 1,
      end: null,
      count: null,
      weekdays: [3],
      exceptions: [],
    });
    expect(endModeOf(rule)).toBe('FOREVER');
  });

  it('defaultRule resets type-specific fields on switch (savedRepeatingType analog)', () => {
    expect(defaultRule('DAILY', START).weekdays).toBeNull();
    expect(defaultRule('MONTHLY', START).exceptions).toEqual([]);
  });

  it('withEndMode seeds the active field and clears the other', () => {
    const rule = defaultRule('WEEKLY', START);
    const until = withEndMode(rule, 'UNTIL', START);
    expect(until.end).toBe('2026-10-05'); // start + 90 days
    expect(until.count).toBeNull();
    const count = withEndMode(until, 'COUNT', START);
    expect(count.count).toBe(10);
    expect(count.end).toBeNull();
    const forever = withEndMode(count, 'FOREVER', START);
    expect(forever.end).toBeNull();
    expect(forever.count).toBeNull();
  });

  it('endModeOf discriminates on end/count presence', () => {
    const rule = defaultRule('DAILY', START);
    expect(endModeOf(withUntil(rule, '2026-12-24'))).toBe('UNTIL');
    expect(endModeOf(withCount(rule, 5))).toBe('COUNT');
  });

  it('withInterval clamps to ≥1 integers', () => {
    const rule = defaultRule('DAILY', START);
    expect(withInterval(rule, 3).interval).toBe(3);
    expect(withInterval(rule, 0).interval).toBe(1);
    expect(withInterval(rule, NaN).interval).toBe(1);
  });

  it('toggleWeekday adds sorted / removes, empty set stays representable (OQ7)', () => {
    const rule = defaultRule('WEEKLY', START);
    const both = toggleWeekday(rule, 5); // + Thursday
    expect(both.weekdays).toEqual([3, 5]);
    const none = toggleWeekday(toggleWeekday(both, 3), 5);
    expect(none.weekdays).toEqual([]);
  });

  it('toggleException adds sorted / removes day keys', () => {
    const rule = defaultRule('WEEKLY', START);
    const skipped = toggleException(rule, '2026-07-14');
    expect(skipped.exceptions).toEqual(['2026-07-14']);
    expect(toggleException(skipped, '2026-07-14').exceptions).toEqual([]);
    expect(toggleException(skipped, '2026-07-07').exceptions).toEqual(['2026-07-07', '2026-07-14']);
  });

  it('transforms return fresh arrays (history snapshots must not share refs)', () => {
    const rule = defaultRule('WEEKLY', START);
    const next = toggleException(rule, '2026-07-14');
    expect(next).not.toBe(rule);
    expect(next.weekdays).not.toBe(rule.weekdays);
    expect(rule.exceptions).toEqual([]);
  });

  it('ruleSummary renders pattern + ending', () => {
    const weekly = toggleWeekday(defaultRule('WEEKLY', START), 5);
    expect(ruleSummary(withCount(weekly, 10), START)).toBe('Wöchentlich am Di + Do · 10 Termine');
    expect(ruleSummary(withInterval(defaultRule('DAILY', START), 2), START)).toBe(
      'Alle 2 Tage · endet nie',
    );
    expect(ruleSummary(defaultRule('MONTHLY', START), START)).toBe(
      'Monatlich am 1. Di · endet nie',
    );
    const yearly = withUntil(defaultRule('YEARLY', START), '2030-07-07');
    expect(ruleSummary(yearly, START)).toBe('Jährlich am 7.7. · bis 2030-07-07');
  });

  it('round-trip shape: a rule built here is a valid wire RepeatingRule', () => {
    const rule: RepeatingRule = withCount(toggleWeekday(defaultRule('WEEKLY', START), 5), 10);
    expect(rule).toEqual({
      type: 'WEEKLY',
      interval: 1,
      end: null,
      count: 10,
      weekdays: [3, 5],
      exceptions: [],
    });
  });
});
