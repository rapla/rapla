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
    const labels = Array.from((f.nativeElement as HTMLElement).querySelectorAll('mat-chip')).map(
      (c) => c.textContent?.trim() ?? '',
    );
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

  it('PRD 123 D8 — 10 or more resource chips fold into one "N Ressourcen" chip; user and event chips stay single', async () => {
    for (let i = 1; i <= 10; i++) store.add({ id: `r${i}`, kind: 'resource', label: `Raum ${i}` });
    store.add({ id: 'u1', kind: 'user', label: 'Burns Monty' });
    store.add({ id: 'e1', kind: 'event', label: 'Prog II' });
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const labels = Array.from(el.querySelectorAll('mat-chip')).map(
      (c) => c.textContent?.trim() ?? '',
    );
    expect(labels.length).toBe(3);
    expect(labels[0]).toContain('10 Ressourcen');
    (el.querySelector('mat-chip .chip-remove') as HTMLButtonElement).click();
    await f.whenStable();
    expect(store.entries().map((e) => e.id)).toEqual(['u1', 'e1']);
  });

  it('9 resource chips stay single', async () => {
    for (let i = 1; i <= 9; i++) store.add({ id: `r${i}`, kind: 'resource', label: `Raum ${i}` });
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    expect((f.nativeElement as HTMLElement).querySelectorAll('mat-chip').length).toBe(9);
  });

  it('10 or more user chips fold into one "N Benutzer" chip after the resource chip; its × removes only the user chips', async () => {
    store.add({ id: 'e1', kind: 'event', label: 'Prog II' });
    for (let i = 1; i <= 10; i++) store.add({ id: `u${i}`, kind: 'user', label: `user-${i}` });
    for (let i = 1; i <= 10; i++) store.add({ id: `r${i}`, kind: 'resource', label: `Raum ${i}` });
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const labels = () =>
      Array.from(el.querySelectorAll('mat-chip')).map((c) => c.textContent?.trim() ?? '');
    expect(labels().length).toBe(3);
    expect(labels()[0]).toContain('10 Ressourcen');
    expect(labels()[1]).toContain('10 Benutzer');
    expect(labels()[2]).toContain('Prog II');
    (el.querySelectorAll('mat-chip')[1].querySelector('.chip-remove') as HTMLButtonElement).click();
    await f.whenStable();
    expect(store.entries().filter((e) => e.kind === 'user')).toEqual([]);
    expect(store.entries().filter((e) => e.kind === 'resource').length).toBe(10);
    expect(store.has('e1')).toBe(true);
  });

  it('9 user chips stay single', async () => {
    for (let i = 1; i <= 9; i++) store.add({ id: `u${i}`, kind: 'user', label: `user-${i}` });
    const f = TestBed.createComponent(ChipRailComponent);
    await f.whenStable();
    expect((f.nativeElement as HTMLElement).querySelectorAll('mat-chip').length).toBe(9);
  });
});
