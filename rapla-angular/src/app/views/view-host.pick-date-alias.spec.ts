import { describe, it, expect } from 'vitest';
import { pickDateAlias } from './view-host.component';
import type { ViewColumn } from '../graphql/graphql.service';

const col = (alias: string, type: string, hidden = false): ViewColumn => ({
  alias,
  header: alias,
  type,
  hidden,
});

describe('pickDateAlias', () => {
  it('picks a Date-typed column (the stored Wochenansicht aliases it "date")', () => {
    const cols = [col('date', 'Date'), col('times', 'String'), col('name', 'String')];
    expect(pickDateAlias(cols)).toBe('date');
  });

  it('picks a LocalDateTime column', () => {
    expect(pickDateAlias([col('start', 'LocalDateTime'), col('name', 'String')])).toBe('start');
  });

  it('finds a hidden date column too (grouping/sort key not displayed)', () => {
    expect(pickDateAlias([col('name', 'String'), col('day', 'Date', true)])).toBe('day');
  });

  it('returns "" when no date column exists (→ view not groupable)', () => {
    expect(pickDateAlias([col('name', 'String'), col('times', 'String')])).toBe('');
  });
});
