import { describe, it, expect } from 'vitest';

import { printMode, PRINT_PAGE_PX, PRINT_MIN_LANE_PX } from './week-lanes';

describe('printMode — landscape grid vs stacked days (PRD 077 print)', () => {
  it('a calm week (1 lane per day) prints as a landscape grid', () => {
    expect(printMode([1, 1, 1, 1, 1, 1, 1])).toBe('grid');
  });

  it('2 lanes per day still fits the landscape page', () => {
    expect(printMode([2, 2, 2, 2, 2, 2, 2])).toBe('grid');
  });

  it('3+ lanes per day squeeze below the readable minimum → stacked', () => {
    expect(printMode([3, 3, 3, 3, 3, 3, 3])).toBe('stacked');
  });

  it('a busy dhbw-style week (many parallel lanes) stacks', () => {
    expect(printMode([10, 9, 11, 10, 8])).toBe('stacked');
  });

  it('one heavy day among calm ones follows the total width, not the max', () => {
    // 6+1+1+1+1+1+1 = 12 lanes over 7 days → ~61px lanes → still readable grid
    expect(printMode([6, 1, 1, 1, 1, 1, 1])).toBe('grid');
  });

  it('a single day (day mode) always prints as a grid — stacking one day changes nothing', () => {
    expect(printMode([30])).toBe('grid');
  });

  it('an empty layout prints as a grid', () => {
    expect(printMode([])).toBe('grid');
  });

  it('the boundary follows usable width / total lanes vs the minimum lane width', () => {
    // 7 days: usable = PAGE − 48 gutter − 7·16 day gutters; grid while usable/lanes ≥ MIN
    const usable = PRINT_PAGE_PX - 48 - 7 * 16;
    const maxGridLanes = Math.floor(usable / PRINT_MIN_LANE_PX);
    const even = (n: number) => {
      const counts = [0, 0, 0, 0, 0, 0, 0].map(() => 0);
      for (let i = 0; i < n; i++) counts[i % 7]++;
      return counts;
    };
    expect(printMode(even(maxGridLanes))).toBe('grid');
    expect(printMode(even(maxGridLanes + 1))).toBe('stacked');
  });
});
