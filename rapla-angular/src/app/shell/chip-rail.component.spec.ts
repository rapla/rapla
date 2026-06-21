import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { ChipRailComponent } from './chip-rail.component';
import { FilterStore } from '../state/filter-store';

describe('ChipRailComponent', () => {
  let store: FilterStore;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ChipRailComponent] }).compileComponents();
    store = TestBed.inject(FilterStore);
    store.clear();
  });

  it('shows the empty hint and no chips when nothing is selected', async () => {
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-hint')).not.toBeNull();
    expect(el.querySelectorAll('mat-chip').length).toBe(0);
  });

  it('renders one chip per selection entry, in order', async () => {
    store.add({ id: 'A', kind: 'resource', label: 'C348' });
    store.add({ id: 'B', kind: 'event', label: 'Programmieren II' });
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    const labels = Array.from(
      (f.nativeElement as HTMLElement).querySelectorAll('mat-chip'),
    ).map((c) => c.textContent?.trim() ?? '');
    expect(labels.length).toBe(2);
    expect(labels[0]).toContain('C348');
    expect(labels[1]).toContain('Programmieren II');
  });

  it('clicking a chip remove button drops that entry from the store', async () => {
    store.add({ id: 'A', kind: 'resource', label: 'C348' });
    store.add({ id: 'B', kind: 'event', label: 'Prog II' });
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    const removeBtn = (f.nativeElement as HTMLElement).querySelector(
      '.chip-remove',
    ) as HTMLButtonElement;
    removeBtn.click();
    await f.whenStable();
    expect(store.has('A')).toBe(false);
    expect(store.has('B')).toBe(true);
  });

  it('clear-all empties the store', async () => {
    store.add({ id: 'A', kind: 'resource', label: 'C348' });
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    const clearBtn = (f.nativeElement as HTMLElement).querySelector(
      '.clear-all',
    ) as HTMLButtonElement;
    clearBtn.click();
    await f.whenStable();
    expect(store.isEmpty()).toBe(true);
  });
});
