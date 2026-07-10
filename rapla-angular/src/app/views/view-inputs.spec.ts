import { describe, it, expect } from 'vitest';
import { resolveAnchorOffset, resolveWindowFromInputs, type ViewInput } from './view-inputs';

// 2026-06-17 is a Wednesday; week (Mon-anchored) starts 2026-06-15; month starts 2026-06-01.
const WED = new Date('2026-06-17T12:00:00Z');

describe('resolveAnchorOffset', () => {
  it('TODAY + 0 = today at 00:00 (zoneless)', () => {
    expect(resolveAnchorOffset({ anchor: 'TODAY', offset: 0, unit: 'DAYS' }, WED)).toBe(
      '2026-06-17T00:00:00',
    );
  });

  it('WEEK_START + 0 = Monday of the current week', () => {
    expect(resolveAnchorOffset({ anchor: 'WEEK_START', offset: 0, unit: 'DAYS' }, WED)).toBe(
      '2026-06-15T00:00:00',
    );
  });

  it('WEEK_START + 6 = Sunday of the current week', () => {
    expect(resolveAnchorOffset({ anchor: 'WEEK_START', offset: 6, unit: 'DAYS' }, WED)).toBe(
      '2026-06-21T00:00:00',
    );
  });

  it('MONTH_START + 0 = first of the current month', () => {
    expect(resolveAnchorOffset({ anchor: 'MONTH_START', offset: 0, unit: 'DAYS' }, WED)).toBe(
      '2026-06-01T00:00:00',
    );
  });

  it('TODAY with negative day offset goes into the past', () => {
    expect(resolveAnchorOffset({ anchor: 'TODAY', offset: -14, unit: 'DAYS' }, WED)).toBe(
      '2026-06-03T00:00:00',
    );
  });

  it('WEEKS unit multiplies the offset by 7 days', () => {
    expect(resolveAnchorOffset({ anchor: 'WEEK_START', offset: 1, unit: 'WEEKS' }, WED)).toBe(
      '2026-06-22T00:00:00',
    );
  });

  it('crosses a month boundary correctly', () => {
    expect(resolveAnchorOffset({ anchor: 'TODAY', offset: 28, unit: 'DAYS' }, WED)).toBe(
      '2026-07-15T00:00:00',
    );
  });
});

describe('resolveWindowFromInputs', () => {
  const INPUTS: ViewInput[] = [
    {
      name: 'filter.from',
      control: 'DATE_RANGE_START',
      default: { anchor: 'WEEK_START', offset: 0, unit: 'DAYS' },
    },
    {
      name: 'filter.to',
      control: 'DATE_RANGE_END',
      default: { anchor: 'WEEK_START', offset: 7, unit: 'DAYS' },
    },
  ];

  it('maps DATE_RANGE_START/END to {from, to}', () => {
    expect(resolveWindowFromInputs(INPUTS, WED)).toEqual({
      from: '2026-06-15T00:00:00',
      to: '2026-06-22T00:00:00',
    });
  });

  it('returns null when the range controls are absent', () => {
    expect(resolveWindowFromInputs([], WED)).toBeNull();
  });
});
