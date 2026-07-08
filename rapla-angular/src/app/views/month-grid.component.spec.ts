import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { MonthGridComponent } from './month-grid.component';

const ROWS: Record<string, unknown>[] = [
  {
    start: '2026-07-07T11:00:00',
    end: '2026-07-07T12:00:00',
    name: 'Seminar KI',
    times: '11:00',
    color: '#ef6c00',
    reservation: { id: 'res-4', canModify: true },
    appointmentId: 'a4',
  },
  {
    start: '2026-07-07T14:00:00',
    end: '2026-07-07T15:00:00',
    name: 'Projektreview',
    times: null,
    color: null,
    reservation: { id: 'res-5', canModify: false },
    appointmentId: 'a5',
  },
  {
    start: '2026-07-13T08:00:00',
    end: '2026-07-15T17:00:00',
    name: 'Projektwoche',
    times: '08:00',
    color: '#3f51b5',
    reservation: { id: 'res-m1', canModify: true },
    appointmentId: 'am1',
  },
  {
    start: '2026-07-17T09:00:00',
    end: '2026-07-21T16:00:00',
    name: 'Messe',
    times: '09:00',
    color: '#7b1fa2',
    reservation: { id: 'res-m3', canModify: true },
    appointmentId: 'am3',
  },
  {
    start: '2026-07-06T20:00:00',
    end: '2026-07-07T00:00:00',
    name: 'Abendkurs',
    times: '20:00',
    color: '#00796b',
    reservation: { id: 'res-mid', canModify: true },
    appointmentId: 'amid',
  },
  {
    start: '2026-07-02T10:00:00',
    end: '2026-07-02T11:00:00',
    name: 'Ohne Reservation',
    times: '10:00',
    color: '#c2185b',
    appointmentId: 'anores',
  },
];

function mount(rows: Record<string, unknown>[] = ROWS): ComponentFixture<MonthGridComponent> {
  const f = TestBed.createComponent(MonthGridComponent);
  f.componentRef.setInput('rows', rows);
  f.componentRef.setInput('anchor', '2026-07-07');
  f.detectChanges();
  return f;
}

function chips(f: ComponentFixture<MonthGridComponent>, name: string): HTMLElement[] {
  return Array.from((f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.chip')).filter(
    (c) => (c.textContent ?? '').includes(name),
  );
}

// jsdom normalizes calc(3/7*100% - 5px) to calc(42.8571% - 5px) — extract the %.
function pct(style: string): number {
  const m = /calc\(([\d.]+)%/.exec(style);
  return m ? Number(m[1]) : NaN;
}

const col = (n: number) => (n / 7) * 100;

describe('MonthGridComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MonthGridComponent] }).compileComponents();
  });

  it('renders the Mo–So header and 35 day cells for July 2026', () => {
    const f = mount();
    const el = f.nativeElement as HTMLElement;
    const dows = Array.from(el.querySelectorAll('.dow')).map((d) => d.textContent?.trim());
    expect(dows).toEqual(['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']);
    expect(el.querySelectorAll('.cell').length).toBe(35);
    expect(el.querySelectorAll('.cell.other').length).toBe(4); // Jun 29+30, Aug 1+2
    expect(el.querySelectorAll('.cell.today').length).toBeLessThanOrEqual(1);
  });

  it('renders a single-day block as one chip with time + name', () => {
    const f = mount();
    const chip = chips(f, 'Seminar KI');
    expect(chip.length).toBe(1);
    expect(chip[0].textContent).toContain('11:00');
    expect(pct(chip[0].style.width)).toBeCloseTo(col(1), 1);
  });

  it('renders a 3-day block as ONE element spanning 3 columns in its week', () => {
    const f = mount();
    const chip = chips(f, 'Projektwoche');
    expect(chip.length).toBe(1);
    expect(pct(chip[0].style.left)).toBeCloseTo(col(0), 1); // Mon Jul 13
    expect(pct(chip[0].style.width)).toBeCloseTo(col(3), 1);
  });

  it('splits a week-boundary block into two elements with ‹ › markers', () => {
    const f = mount();
    const parts = chips(f, 'Messe');
    expect(parts.length).toBe(2);
    expect(parts[0].textContent).toContain('›'); // Fri Jul 17 → continues right
    expect(parts[0].textContent).not.toContain('‹');
    expect(parts[1].textContent).toContain('‹'); // Mon Jul 20 → continued from left
    expect(parts[1].textContent).not.toContain('›');
    expect(pct(parts[0].style.width)).toBeCloseTo(col(3), 1); // Fr–So
    expect(pct(parts[1].style.width)).toBeCloseTo(col(2), 1); // Mo–Di
  });

  it('a block ending at T00:00:00 does not cover that day (span 1)', () => {
    const f = mount();
    const chip = chips(f, 'Abendkurs');
    expect(chip.length).toBe(1);
    expect(pct(chip[0].style.width)).toBeCloseTo(col(1), 1);
    expect(pct(chip[0].style.left)).toBeCloseTo(col(0), 1); // Mon Jul 6 only
  });

  it('null color → .neutral class, falls back to the start time', () => {
    const f = mount();
    const chip = chips(f, 'Projektreview');
    expect(chip.length).toBe(1);
    expect(chip[0].classList.contains('neutral')).toBe(true);
    expect(chip[0].textContent).toContain('14:00');
    const colored = chips(f, 'Seminar KI')[0];
    expect(colored.classList.contains('neutral')).toBe(false);
  });

  it('stacks overlapping bars — the second one is pushed below (larger top)', () => {
    const f = mount();
    const tops = [chips(f, 'Seminar KI')[0], chips(f, 'Projektreview')[0]]
      .map((c) => parseInt(c.style.top, 10))
      .sort((a, b) => a - b);
    expect(tops[1]).toBeGreaterThan(tops[0]);
  });

  it('emits openEvent with the reservation id on chip click; skips rows without one', () => {
    const f = mount();
    const emitted: string[] = [];
    f.componentInstance.openEvent.subscribe((id) => emitted.push(id));
    chips(f, 'Seminar KI')[0].click();
    expect(emitted).toEqual(['res-4']);
    chips(f, 'Ohne Reservation')[0].click();
    expect(emitted).toEqual(['res-4']);
  });
});

describe('MonthGridComponent — drag-create day-range selection (PRD 095 Phase 3)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MonthGridComponent] }).compileComponents();
  });

  function cell(f: ComponentFixture<MonthGridComponent>, day: string): HTMLElement {
    const el = (f.nativeElement as HTMLElement).querySelector<HTMLElement>(
      `.cell[data-day="${day}"]`,
    );
    if (!el) throw new Error(`no cell for ${day}`);
    return el;
  }

  /** jsdom has no elementsFromPoint — the component hit-tests through it. */
  function stubHit(el: HTMLElement | null): void {
    (document as unknown as { elementsFromPoint: (x: number, y: number) => Element[] })
      .elementsFromPoint = () => (el ? [el] : []);
  }

  function pointer(type: string, x: number, y: number): Event {
    return new MouseEvent(type, { clientX: x, clientY: y, button: 0, bubbles: true });
  }

  it('drag over cells selects the range and emits createRange on release', () => {
    const f = mount();
    const emitted: { from: string; to: string }[] = [];
    f.componentInstance.createRange.subscribe((r) => emitted.push(r));
    const start = cell(f, '2026-07-22');
    start.dispatchEvent(pointer('pointerdown', 10, 10));
    stubHit(cell(f, '2026-07-24'));
    start.dispatchEvent(pointer('pointermove', 60, 10));
    f.detectChanges();
    expect(cell(f, '2026-07-23').classList.contains('selecting')).toBe(true);
    expect(cell(f, '2026-07-24').classList.contains('selecting')).toBe(true);
    start.dispatchEvent(pointer('pointerup', 60, 10));
    expect(emitted).toEqual([{ from: '2026-07-22', to: '2026-07-24' }]);
    f.detectChanges();
    expect(cell(f, '2026-07-23').classList.contains('selecting')).toBe(false);
  });

  it('a plain click on free space emits nothing', () => {
    const f = mount();
    const emitted: unknown[] = [];
    f.componentInstance.createRange.subscribe((r) => emitted.push(r));
    const c = cell(f, '2026-07-22');
    c.dispatchEvent(pointer('pointerdown', 10, 10));
    c.dispatchEvent(pointer('pointerup', 11, 10));
    expect(emitted).toEqual([]);
  });

  it('a backwards drag emits the sorted range', () => {
    const f = mount();
    const emitted: { from: string; to: string }[] = [];
    f.componentInstance.createRange.subscribe((r) => emitted.push(r));
    const start = cell(f, '2026-07-24');
    start.dispatchEvent(pointer('pointerdown', 60, 10));
    stubHit(cell(f, '2026-07-22'));
    start.dispatchEvent(pointer('pointermove', 10, 10));
    start.dispatchEvent(pointer('pointerup', 10, 10));
    expect(emitted).toEqual([{ from: '2026-07-22', to: '2026-07-24' }]);
  });

  it('ESC cancels the selection without emitting', () => {
    const f = mount();
    const emitted: unknown[] = [];
    f.componentInstance.createRange.subscribe((r) => emitted.push(r));
    const start = cell(f, '2026-07-22');
    start.dispatchEvent(pointer('pointerdown', 10, 10));
    stubHit(cell(f, '2026-07-24'));
    start.dispatchEvent(pointer('pointermove', 60, 10));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    start.dispatchEvent(pointer('pointerup', 60, 10));
    expect(emitted).toEqual([]);
    f.detectChanges();
    expect(cell(f, '2026-07-23').classList.contains('selecting')).toBe(false);
  });
});
