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

  it('renders all 5 render-mode buttons', async () => {
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    const labels = Array.from(
      f.nativeElement.querySelectorAll('.modes button') as NodeListOf<HTMLButtonElement>,
    ).map((b) => b.textContent?.trim());
    expect(labels).toEqual(['Woche', 'Tabelle', 'Monat', 'Tag', 'Programm']);
  });

  it('clicking Woche sets renderMode week and marks that button active', async () => {
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    const buttons = Array.from(
      f.nativeElement.querySelectorAll('.modes button') as NodeListOf<HTMLButtonElement>,
    );
    const woche = buttons.find((b) => b.textContent?.trim() === 'Woche')!;
    woche.click();
    f.detectChanges();
    await f.whenStable();
    expect(viewState.renderMode()).toBe('week');
    expect(woche.classList.contains('on')).toBe(true);
  });

  it('shows the from/to date parts of the active window', async () => {
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-21T23:59:59' });
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    await f.whenStable();
    const range = f.nativeElement.querySelector('.range') as HTMLElement;
    expect(range.textContent?.trim()).toBe('2026-06-15 … 2026-06-21');
  });
});
