import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import {
  ViewControlStripComponent,
  monthWindowOf,
  shiftMonth,
  monthLabel,
} from './view-control-strip.component';
import { ViewStateStore } from '../state/view-state-store';

describe('month window helpers', () => {
  it('monthWindowOf yields [1st .. 1st-of-next-month) for a mid-month date', () => {
    expect(monthWindowOf('2026-07-15T13:45:00')).toEqual({
      from: '2026-07-01T00:00:00',
      to: '2026-08-01T00:00:00',
    });
  });

  it('monthWindowOf wraps the year for December', () => {
    expect(monthWindowOf('2026-12-03T00:00:00')).toEqual({
      from: '2026-12-01T00:00:00',
      to: '2027-01-01T00:00:00',
    });
  });

  it('shiftMonth steps an aligned month window forward and backward', () => {
    const july = { from: '2026-07-01T00:00:00', to: '2026-08-01T00:00:00' };
    expect(shiftMonth(july, 1)).toEqual({
      from: '2026-08-01T00:00:00',
      to: '2026-09-01T00:00:00',
    });
    expect(shiftMonth(july, -1)).toEqual({
      from: '2026-06-01T00:00:00',
      to: '2026-07-01T00:00:00',
    });
  });

  it('shiftMonth wraps the year in both directions', () => {
    expect(shiftMonth({ from: '2026-12-01T00:00:00', to: '2027-01-01T00:00:00' }, 1)).toEqual({
      from: '2027-01-01T00:00:00',
      to: '2027-02-01T00:00:00',
    });
    expect(shiftMonth({ from: '2026-01-01T00:00:00', to: '2026-02-01T00:00:00' }, -1)).toEqual({
      from: '2025-12-01T00:00:00',
      to: '2026-01-01T00:00:00',
    });
  });

  it('shiftMonth snaps a non-aligned (week) window to its from-month before shifting', () => {
    expect(shiftMonth({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' }, 1)).toEqual({
      from: '2026-07-01T00:00:00',
      to: '2026-08-01T00:00:00',
    });
  });

  it('monthLabel renders the German month name + year', () => {
    expect(monthLabel('2026-07-01T00:00:00')).toBe('Juli 2026');
    expect(monthLabel('2026-03-14T09:00:00')).toBe('März 2026');
  });
});

describe('ViewControlStripComponent', () => {
  let viewState: ViewStateStore;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewControlStripComponent],
    }).compileComponents();
    viewState = TestBed.inject(ViewStateStore);
  });

  it('hides the mode switch when only one render mode (default)', async () => {
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    expect(f.nativeElement.querySelectorAll('.modes button').length).toBe(0);
  });

  it('shows Woche + Tabelle buttons for renderModes [week, table]', async () => {
    viewState.setRenderModes(['week', 'table']);
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    const labels = Array.from(
      f.nativeElement.querySelectorAll('.modes button') as NodeListOf<HTMLButtonElement>,
    ).map((b) => b.textContent?.trim());
    expect(labels).toEqual(['Woche', 'Tabelle']);
  });

  it('WEEK mode: navigation + read-only range, no date inputs', async () => {
    viewState.setRenderModes(['week', 'table']);
    viewState.setRenderMode('week');
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    expect((f.nativeElement.querySelector('.range') as HTMLElement).textContent?.trim()).toBe(
      '2026-06-15 … 2026-06-22',
    );
    expect(f.nativeElement.querySelector('.nav')).toBeTruthy();
    expect(f.nativeElement.querySelectorAll('.range-edit input').length).toBe(0);
  });

  it('TABLE mode: editable from/to Material datepickers, no navigation', async () => {
    viewState.setRenderModes(['week', 'table']);
    viewState.setRenderMode('table');
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    expect(f.nativeElement.querySelectorAll('.range-edit input').length).toBe(2);
    const cmp = f.componentInstance as unknown as {
      fromDate(): Date | null;
      toDate(): Date | null;
    };
    expect(cmp.fromDate()?.getTime()).toBe(new Date(2026, 5, 15).getTime());
    expect(cmp.toDate()?.getTime()).toBe(new Date(2026, 5, 22).getTime());
    expect(f.nativeElement.querySelector('.nav')).toBeNull();
  });

  it('picking a from date updates the window, keeping to', async () => {
    viewState.setRenderModes(['week', 'table']);
    viewState.setRenderMode('table');
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    f.componentInstance.onFrom({ value: new Date(2026, 5, 10) } as never);
    expect(viewState.window()).toEqual({ from: '2026-06-10T00:00:00', to: '2026-06-22T00:00:00' });
  });

  it('MONTH mode: shows the German month label; prev/next step the window by a month', async () => {
    viewState.setRenderModes(['month', 'table']);
    viewState.setRenderMode('month');
    viewState.setWindow({ from: '2026-07-01T00:00:00', to: '2026-08-01T00:00:00' });
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    expect((f.nativeElement.querySelector('.month-label') as HTMLElement).textContent?.trim()).toBe(
      'Juli 2026',
    );
    expect(f.nativeElement.querySelectorAll('.range-edit input').length).toBe(0);
    const buttons = Array.from(
      f.nativeElement.querySelectorAll('.nav .navbtn') as NodeListOf<HTMLButtonElement>,
    );
    buttons.find((b) => b.title === 'vor')!.click();
    expect(viewState.window()).toEqual({ from: '2026-08-01T00:00:00', to: '2026-09-01T00:00:00' });
    buttons.find((b) => b.title === 'zurück')!.click();
    expect(viewState.window()).toEqual({ from: '2026-07-01T00:00:00', to: '2026-08-01T00:00:00' });
  });

  it('clicking Woche sets renderMode week and marks that button active', async () => {
    viewState.setRenderModes(['week', 'table']);
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    const woche = Array.from(
      f.nativeElement.querySelectorAll('.modes button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Woche')!;
    woche.click();
    f.detectChanges();
    await f.whenStable();
    expect(viewState.renderMode()).toBe('week');
    expect(woche.classList.contains('on')).toBe(true);
  });
});
