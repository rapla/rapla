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

async function make(rows: Row[]): Promise<ComponentFixture<WeekGridComponent>> {
  const f = TestBed.createComponent(WeekGridComponent);
  f.componentRef.setInput('rows', rows);
  f.componentRef.setInput('anchor', '2026-06-15T00:00:00');
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
  return f;
}

function hpxOf(f: ComponentFixture<WeekGridComponent>): string {
  const wg = (f.nativeElement as HTMLElement).querySelector('.wg') as HTMLElement;
  return wg.style.getPropertyValue('--wg-hpx');
}

describe('WeekGridComponent — hour rows auto-fit the viewport height', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [WeekGridComponent] }).compileComponents();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('a tall viewport stretches the hour rows to fill the grid', async () => {
    const f = await make([block('2026-06-15', '09:00', '11:00', 'ev')]);
    f.componentInstance.maxHeight.set(1216);
    f.componentInstance.headerPx.set(30);
    f.componentInstance.chromePx.set(2);
    f.detectChanges();
    const { startHour, endHour } = f.componentInstance.layout();
    const expected = Math.floor((1216 - 30 - 2 - 16) / (endHour - startHour));
    expect(expected).toBeGreaterThan(48);
    expect(hpxOf(f)).toBe(`${expected}px`);
  });

  it('a short viewport floors at 48px and scrolls (Swing minBlockWidth analog)', async () => {
    const f = await make([block('2026-06-15', '09:00', '11:00', 'ev')]);
    f.componentInstance.maxHeight.set(320);
    f.detectChanges();
    expect(hpxOf(f)).toBe('48px');
  });

  it('chip geometry is expressed in --wg-hpx so print can pin it back to 48px', async () => {
    const f = await make([block('2026-06-15', '09:00', '11:00', 'ev')]);
    const chip = (f.nativeElement as HTMLElement).querySelector('.chip') as HTMLElement;
    expect(chip.style.top).toContain('var(--wg-hpx)');
    expect(chip.style.height).toContain('var(--wg-hpx)');
  });
});
