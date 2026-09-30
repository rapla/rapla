import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { ResourcePickerComponent, type PickEvent } from './resource-picker.component';
import type { AvailabilityRow } from '../event/availability-search.service';

const room = (i: number, typeKey = 'room', typeName = 'Raum') => ({
  id: `r${i}`,
  kind: 'RESOURCE',
  name: `Raum ${String(i).padStart(2, '0')}`,
  classification: { typeKey, type: { name: typeName } },
});

/** PRD 123 D1/D3 — the shared picker in its assign host and the folded type select. */
describe('ResourcePickerComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ResourcePickerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  async function create(
    rows: ReturnType<typeof room>[],
    inputs: Record<string, unknown> = {},
  ): Promise<ComponentFixture<ResourcePickerComponent>> {
    const f = TestBed.createComponent(ResourcePickerComponent);
    for (const [k, v] of Object.entries(inputs)) f.componentRef.setInput(k, v);
    f.detectChanges();
    for (const req of http.match((r) => r.url === '/api/graphql')) {
      req.flush({ data: { resources: rows, users: [] } });
    }
    await f.whenStable();
    f.detectChanges();
    return f;
  }

  const labels = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('.item .lbl')).map((i) => i.textContent?.trim() ?? '');

  it('D3 — the types fold into one select with counts; picking one shows its tree', async () => {
    const f = await create([room(1), room(2), room(3, 'lecturer', 'Dozent')]);
    const el = f.nativeElement as HTMLElement;
    expect(
      Array.from(el.querySelectorAll('.chips button')).map((b) => b.textContent?.trim()),
    ).toEqual(['Alle', '★ Favoriten', 'Zuletzt']);
    const options = Array.from(el.querySelectorAll('select.typesel option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(options).toEqual(['Typ ▾', 'Dozent (1)', 'Raum (2)']);
    const select = el.querySelector('select.typesel') as HTMLSelectElement;
    select.value = 'type:room';
    select.dispatchEvent(new Event('change'));
    await f.whenStable();
    f.detectChanges();
    expect(f.componentInstance.chip()).toBe('type:room');
    expect(labels(el)).toEqual(['Raum 01', 'Raum 02']);
  });

  it('assign mode renders checkbox rows with the availability pill and emits the gesture', async () => {
    const availability = new Map<string, AvailabilityRow>([
      ['r1', { id: 'r1', name: 'Raum 01', status: 'AVAILABLE', conflictingAppointmentIds: [] }],
      ['r2', { id: 'r2', name: 'Raum 02', status: 'CONFLICT', conflictingAppointmentIds: ['a1'] }],
    ]);
    const f = await create([room(1), room(2)], {
      mode: 'assign',
      checked: new Set(['r2']),
      availability,
    });
    const el = f.nativeElement as HTMLElement;
    const picks: PickEvent[] = [];
    f.componentInstance.pick.subscribe((p) => picks.push(p));
    const items = Array.from(el.querySelectorAll<HTMLElement>('.item'));
    expect(items.map((i) => i.querySelector('.chk')?.textContent?.trim())).toEqual(['', '✓']);
    expect(items.map((i) => i.querySelector('.pill')?.textContent?.trim())).toEqual([
      'frei',
      'belegt',
    ]);
    expect(items[1].classList.contains('selected')).toBe(true);
    expect(el.querySelector('.act')).toBeNull(); // no ⋮ menu outside the rail
    items[0].click();
    items[1].dispatchEvent(new MouseEvent('click', { ctrlKey: true, bubbles: true }));
    expect(picks.map((p) => [p.item.id, p.ctrl])).toEqual([
      ['r1', false],
      ['r2', true],
    ]);
  });

  it('assign mode: arrows step over group rows and "Weitere…" to the next item (L4)', async () => {
    const grouped = (i: number, group: string) => ({ ...room(i), groupPaths: [[group]] });
    const rows = [grouped(1, 'A'), ...Array.from({ length: 101 }, (_, i) => grouped(i + 2, 'B'))];
    const f = await create(rows, { mode: 'assign' });
    f.componentInstance.chip.set('type:room');
    await f.whenStable();
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    for (const t of Array.from(el.querySelectorAll<HTMLElement>('.grouprow .toggle'))) t.click();
    await f.whenStable();
    f.detectChanges();
    const items = Array.from(el.querySelectorAll<HTMLElement>('.item'));
    expect(items.length).toBe(101); // 1 in A + the first block of 100 in B, a "Weitere…" row in between
    items[0].focus();
    items[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    expect(document.activeElement).toBe(items[1]); // across the group row of B
    items[100].focus();
    items[100].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
    expect(document.activeElement).toBe(items[99]);
  });

  it('assign mode: arrows step the rows and Enter picks', async () => {
    const f = await create([room(1), room(2)], { mode: 'assign' });
    const el = f.nativeElement as HTMLElement;
    const picks: PickEvent[] = [];
    f.componentInstance.pick.subscribe((p) => picks.push(p));
    const [first, second] = Array.from(el.querySelectorAll<HTMLElement>('.item'));
    first.focus();
    first.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    expect(document.activeElement).toBe(second);
    second.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(picks.map((p) => p.item.id)).toEqual(['r2']);
  });
});
