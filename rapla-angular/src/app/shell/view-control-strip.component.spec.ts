import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { ViewControlStripComponent } from './view-control-strip.component';
import { ViewStateStore } from '../state/view-state-store';

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

  it('TABLE mode: editable from/to date inputs, no navigation', async () => {
    viewState.setRenderModes(['week', 'table']);
    viewState.setRenderMode('table');
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    const inputs = f.nativeElement.querySelectorAll(
      '.range-edit input[type=date]',
    ) as NodeListOf<HTMLInputElement>;
    expect(inputs.length).toBe(2);
    expect(inputs[0].value).toBe('2026-06-15');
    expect(inputs[1].value).toBe('2026-06-22');
    expect(f.nativeElement.querySelector('.nav')).toBeNull();
  });

  it('editing the from date updates the window, keeping to', async () => {
    viewState.setRenderModes(['week', 'table']);
    viewState.setRenderMode('table');
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    const from = f.nativeElement.querySelector('.range-edit input[type=date]') as HTMLInputElement;
    from.value = '2026-06-10';
    from.dispatchEvent(new Event('change'));
    expect(viewState.window()).toEqual({ from: '2026-06-10T00:00:00', to: '2026-06-22T00:00:00' });
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
