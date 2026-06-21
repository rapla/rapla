import { describe, it, expect } from 'vitest';
import { weekWindow, WEEK_VIEW_FALLBACK, WEEK_VIEW_QUERY } from './week-view';

describe('weekWindow', () => {
  it('spans Monday 00:00 to the next Monday for a midweek date', () => {
    const { from, to } = weekWindow(new Date('2026-06-17T12:00:00Z')); // Wednesday
    expect(from).toBe('2026-06-15T00:00:00');
    expect(to).toBe('2026-06-22T00:00:00');
  });

  it('treats Monday as the first day of its own week', () => {
    const { from } = weekWindow(new Date('2026-06-15T08:00:00Z'));
    expect(from).toBe('2026-06-15T00:00:00');
  });

  it('treats Sunday as the last day of the same week', () => {
    const { from, to } = weekWindow(new Date('2026-06-21T23:00:00Z')); // Sunday
    expect(from).toBe('2026-06-15T00:00:00');
    expect(to).toBe('2026-06-22T00:00:00');
  });

  it('emits zoneless LocalDateTime strings', () => {
    const { from, to } = weekWindow();
    expect(from).toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00$/);
    expect(to).not.toMatch(/[Zz]$/);
  });
});

describe('WEEK_VIEW_FALLBACK', () => {
  it('declares the visible columns in order, matching the @view query', () => {
    expect(WEEK_VIEW_FALLBACK.columns.map((c) => c.alias)).toEqual([
      'start',
      'end',
      'times',
      'name',
      'personen',
      'nichtPersonen',
    ]);
  });

  it('joins the two allocatable columns with ", "', () => {
    const cols = Object.fromEntries(WEEK_VIEW_FALLBACK.columns.map((c) => [c.alias, c]));
    expect(cols['personen'].join).toBe(', ');
    expect(cols['nichtPersonen'].join).toBe(', ');
  });
});

describe('WEEK_VIEW_QUERY', () => {
  it('uses the generic isPersonEq split, not a deployment-specific room key', () => {
    expect(WEEK_VIEW_QUERY).toContain('isPersonEq: false');
    expect(WEEK_VIEW_QUERY).not.toContain('typeKeyIn');
  });

  it('declares the Wochenansicht view title', () => {
    expect(WEEK_VIEW_QUERY).toContain('@view(title: "Wochenansicht")');
  });

  it('hides the reservation field at the PARENT (per PRD 074: @hidden on children does not hide the parent column)', () => {
    // Verified against the live server: without `reservation @hidden` a junk
    // "reservation" column leaks into extensions.view.
    expect(WEEK_VIEW_QUERY).toMatch(/reservation\s+@hidden\s*\{/);
  });
});
