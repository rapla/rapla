import { describe, it, expect } from 'vitest';
import { defaultWindow, APPOINTMENTS_VIEW_FALLBACK } from './appointments-view';

describe('defaultWindow', () => {
  it('spans today ±1 year as zoneless LocalDateTime strings', () => {
    const { from, to } = defaultWindow(new Date('2026-06-21T12:00:00Z'));
    expect(from).toBe('2025-06-21T00:00:00');
    expect(to).toBe('2027-06-21T00:00:00');
  });

  it('emits no timezone suffix (ReservationFilter wants LocalDateTime)', () => {
    const { from, to } = defaultWindow();
    expect(from).not.toMatch(/[Zz]$/);
    expect(to).not.toMatch(/[Zz]$/);
    expect(from).toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00$/);
  });
});

describe('APPOINTMENTS_VIEW_FALLBACK', () => {
  it('declares the dhbw columns in order, matching the @view query', () => {
    expect(APPOINTMENTS_VIEW_FALLBACK.columns.map((c) => c.alias)).toEqual([
      'name',
      'start',
      'duration',
      'persons',
      'resources',
    ]);
  });
});
