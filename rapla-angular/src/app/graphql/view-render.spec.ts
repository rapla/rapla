import { describe, it, expect } from 'vitest';
import { renderCell } from './view-render';
import { ViewColumn } from './graphql.service';

describe('renderCell', () => {
  const col = (over: Partial<ViewColumn> = {}): ViewColumn => ({ alias: 'x', header: 'X', ...over });

  it('renders a plain scalar as-is', () => {
    expect(renderCell({ x: 'hello' }, col())).toBe('hello');
  });

  it('renders empty string for null/undefined/missing', () => {
    expect(renderCell({ x: null }, col())).toBe('');
    expect(renderCell({ x: undefined }, col())).toBe('');
    expect(renderCell({}, col())).toBe('');
  });

  it('collapses an object to its displayName', () => {
    expect(renderCell({ x: { displayName: 'Programmieren II' } }, col())).toBe('Programmieren II');
  });

  it('collapses an object selected as { name } (real views use name)', () => {
    expect(renderCell({ x: { name: 'Prof. X' } }, col())).toBe('Prof. X');
  });

  it('falls back to the first string leaf for an arbitrary single-field object', () => {
    expect(renderCell({ x: { label: 'H004' } }, col())).toBe('H004');
  });

  it('joins a list of {displayName} with the default separator', () => {
    const row = { x: [{ displayName: 'FN-TEK23' }, { displayName: 'FN-TEN23' }] };
    expect(renderCell(row, col({ type: '[String]' }))).toBe('FN-TEK23, FN-TEN23');
  });

  it('joins a list of allocatables selected as { name } with explicit separator', () => {
    const row = { x: [{ name: 'Prof. X' }, { name: 'Dr. A' }] };
    expect(renderCell(row, col({ type: 'Allocatable', join: '; ' }))).toBe('Prof. X; Dr. A');
  });

  it('honors an explicit join separator', () => {
    const row = { x: [{ displayName: 'A' }, { displayName: 'B' }] };
    expect(renderCell(row, col({ join: '; ' }))).toBe('A; B');
  });

  it('drops empty entries when joining', () => {
    const row = { x: [{ displayName: 'A' }, { displayName: null }, { displayName: 'B' }] };
    expect(renderCell(row, col())).toBe('A, B');
  });

  it('formats a DateTime scalar to locale string', () => {
    const out = renderCell({ x: '2026-06-15T08:00:00' }, col({ type: 'DateTime' }));
    // Locale-dependent exact text; assert it parsed (not the raw ISO) and carries the year.
    expect(out).not.toBe('2026-06-15T08:00:00');
    expect(out).toContain('2026');
  });

  it('leaves an unparseable DateTime untouched', () => {
    expect(renderCell({ x: 'not-a-date' }, col({ type: 'DateTime' }))).toBe('not-a-date');
  });
});
