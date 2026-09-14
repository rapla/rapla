import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { WeekGridComponent } from './week-grid.component';

// jsdom has no ResizeObserver (the grid measures its viewport height with one)
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
  },
);

type Row = Record<string, unknown>;

function block(day: string, from: string, to: string, name: string): Row {
  return { start: `${day}T${from}:00`, end: `${day}T${to}:00`, name };
}

/** n overlapping blocks on `day` → n parallel lanes. */
function lanes(day: string, n: number): Row[] {
  return Array.from({ length: n }, (_, i) => block(day, '09:00', '11:00', `ev-${day}-${i}`));
}

const WEEK = ['2026-06-15', '2026-06-16', '2026-06-17', '2026-06-18', '2026-06-19'];

async function make(rows: Row[]): Promise<ComponentFixture<WeekGridComponent>> {
  const f = TestBed.createComponent(WeekGridComponent);
  f.componentRef.setInput('rows', rows);
  f.componentRef.setInput('anchor', '2026-06-15T00:00:00');
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
  return f;
}

describe('WeekGridComponent — print layout (PRD 077)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [WeekGridComponent] }).compileComponents();
  });

  afterEach(() => {
    // the component injects a document-level @page style — must vanish with it
    TestBed.resetTestingModule();
  });

  it('a calm week prints as a grid: no stacked class, landscape @page injected', async () => {
    const f = await make(WEEK.map((d) => block(d, '09:00', '10:00', `ev-${d}`)));
    expect((f.nativeElement as HTMLElement).classList.contains('print-stacked')).toBe(false);
    const styles = Array.from(document.head.querySelectorAll('style')).map((s) => s.textContent);
    expect(styles.some((s) => s?.includes('@page { size: A4 landscape; }'))).toBe(true);
  });

  it('a busy week stacks — and still prints landscape (week/day view is always landscape)', async () => {
    const f = await make(WEEK.flatMap((d) => lanes(d, 8)));
    expect((f.nativeElement as HTMLElement).classList.contains('print-stacked')).toBe(true);
    const styles = Array.from(document.head.querySelectorAll('style')).map((s) => s.textContent);
    expect(styles.some((s) => s?.includes('@page { size: A4 landscape; }'))).toBe(true);
  });

  it('each day column carries its print-only label and hour axis', async () => {
    const f = await make(WEEK.map((d) => block(d, '09:00', '10:00', `ev-${d}`)));
    const el = f.nativeElement as HTMLElement;
    const labels = Array.from(el.querySelectorAll('.daycol .pday')).map((n) =>
      n.textContent?.trim(),
    );
    expect(labels.length).toBe(7);
    expect(labels[0]).toMatch(/15/);
    expect(el.querySelectorAll('.daycol .phlabel').length).toBeGreaterThan(0);
  });

  it('removing the grid removes the @page override (table/month print portrait)', async () => {
    const f = await make(WEEK.map((d) => block(d, '09:00', '10:00', `ev-${d}`)));
    f.destroy();
    const styles = Array.from(document.head.querySelectorAll('style')).map((s) => s.textContent);
    expect(styles.some((s) => s?.includes('landscape'))).toBe(false);
  });
});
