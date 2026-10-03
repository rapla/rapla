import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { ResourcePickerComponent, type PickEvent } from './resource-picker.component';
import type { AvailabilityRow } from '../event/availability-search.service';
import { RecentsFavoritesService } from '../state/recents-favorites.service';

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
    users: { id: string; username: string; name: string }[] = [],
  ): Promise<ComponentFixture<ResourcePickerComponent>> {
    const f = TestBed.createComponent(ResourcePickerComponent);
    for (const [k, v] of Object.entries(inputs)) f.componentRef.setInput(k, v);
    f.detectChanges();
    for (const req of http.match((r) => r.url === '/api/graphql')) {
      req.flush({ data: { resources: rows, users } });
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

  it('D3 — with resources and persons the type select groups them like the create dialog (PRD 122 D9)', async () => {
    const person = (i: number, typeKey: string, typeName: string) => ({
      ...room(i, typeKey, typeName),
      kind: 'PERSON',
    });
    const f = await create([
      room(1),
      room(2),
      person(3, 'lecturer', 'Dozent'),
      room(4, 'camera', 'Kamera'),
      person(5, 'student', 'Assistenz'),
    ]);
    const select = (f.nativeElement as HTMLElement).querySelector('select.typesel')!;
    expect(select.querySelector(':scope > option')?.textContent?.trim()).toBe('Typ ▾');
    expect(
      Array.from(select.querySelectorAll('optgroup')).map((g) => ({
        label: g.getAttribute('label'),
        types: Array.from(g.querySelectorAll('option')).map((o) => o.textContent?.trim()),
      })),
    ).toEqual([
      { label: 'Ressourcen', types: ['Kamera (1)', 'Raum (2)'] },
      { label: 'Personen', types: ['Assistenz (1)', 'Dozent (1)'] },
    ]);
  });

  it('PRD 120 D7 — a building row toggles open to its rooms; the row has no count and no alle wählen', async () => {
    const building = { ...room(1, 'building', 'Gebäude'), name: 'Gebäude A' };
    const child = { ...room(2), parents: [{ id: 'r1' }] };
    const f = await create([building, child], { mode: 'rail' });
    const el = f.nativeElement as HTMLElement;
    const select = el.querySelector('select.typesel') as HTMLSelectElement;
    select.value = 'type:building';
    select.dispatchEvent(new Event('change'));
    await f.whenStable();
    f.detectChanges();
    expect(labels(el)).toEqual(['Gebäude A']);
    const row = el.querySelector('.item')!;
    expect(row.querySelector('.count, .selectall')).toBeNull();
    const toggle = row.querySelector('button.toggle') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    f.detectChanges();
    expect(labels(el)).toEqual(['Gebäude A', 'Raum 02']);
    const childRow = el.querySelectorAll('.item')[1] as HTMLElement;
    expect(childRow.querySelector('button.toggle')).toBeNull();
    expect(childRow.querySelector('.tspace')).not.toBeNull();
    expect(parseFloat(childRow.style.paddingLeft)).toBeGreaterThan(
      parseFloat((row as HTMLElement).style.paddingLeft),
    );
  });

  it('PRD 120 — a favorite building toggles open to its rooms from the whole list', async () => {
    const building = { ...room(1, 'building', 'Gebäude'), name: 'Gebäude A' };
    const child = { ...room(2), parents: [{ id: 'r1' }] };
    const f = await create([building, child]);
    const lists = TestBed.inject(RecentsFavoritesService);
    const loaded = lists.reload();
    http.expectOne('/api/recents').flush([]);
    http
      .expectOne('/api/favorites')
      .flush([
        { id: 'r1', kind: 'resource', label: 'Gebäude A', color: null, typeKey: 'building' },
      ]);
    await loaded;
    f.componentInstance.chip.set('favorites');
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    expect(labels(el)).toEqual(['Gebäude A']);
    (el.querySelector('.item button.toggle') as HTMLButtonElement).click();
    f.detectChanges();
    expect(labels(el)).toEqual(['Gebäude A', 'Raum 02']);
  });

  it('PRD 120 — a search hit in Alle stays expandable', async () => {
    const building = { ...room(1, 'building', 'Gebäude'), name: 'Gebäude A' };
    const child = { ...room(2), parents: [{ id: 'r1' }] };
    const f = await create([building, child], {
      query: 'gebäude',
    });
    const el = f.nativeElement as HTMLElement;
    expect(labels(el)).toEqual(['Gebäude A']);
    (el.querySelector('.item button.toggle') as HTMLButtonElement).click();
    f.detectChanges();
    expect(labels(el)).toEqual(['Gebäude A', 'Raum 02']);
  });

  it('PRD 120 D12 — in Alle the page counts top-level rows only; an expanded building adds its rooms', async () => {
    const building = { ...room(0, 'building', 'Gebäude'), name: 'Aaa Gebäude' };
    const children = [1, 2, 3].map((i) => ({ ...room(i), parents: [{ id: 'r0' }] }));
    const rest = Array.from({ length: 30 }, (_, i) => room(10 + i));
    const f = await create([building, ...children, ...rest]);
    const el = f.nativeElement as HTMLElement;
    const more = () => el.querySelector('.showmore')?.textContent?.trim();
    expect(labels(el).length).toBe(20);
    const before = more();
    (el.querySelector('.item button.toggle') as HTMLButtonElement).click();
    f.detectChanges();
    expect(labels(el).length).toBe(23);
    expect(labels(el).slice(0, 4)).toEqual(['Aaa Gebäude', 'Raum 01', 'Raum 02', 'Raum 03']);
    expect(more()).toBe(before);
  });

  it('an active search shows its filter with the hit count and × clears it', async () => {
    const f = await create([room(1), room(2)], { query: 'sdsa' });
    const el = f.nativeElement as HTMLElement;
    expect(labels(el)).toEqual([]);
    expect(el.querySelector('.searchnote')?.textContent).toContain(
      'Suchfilter „sdsa“ aktiv · 0 Treffer',
    );
    (el.querySelector('.searchnote button') as HTMLButtonElement).click();
    f.detectChanges();
    expect(f.componentInstance.query()).toBe('');
    expect(el.querySelector('.searchnote')).toBeNull();
    expect(labels(el)).toEqual(['Raum 01', 'Raum 02']);
  });

  it('D3 — one kind only keeps the type select flat', async () => {
    const f = await create([room(1), room(2, 'camera', 'Kamera')]);
    expect((f.nativeElement as HTMLElement).querySelector('select.typesel optgroup')).toBeNull();
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

  it('D9 — the Benutzer chip is rail-only: the assign host cannot assign accounts', async () => {
    const users = [
      { id: 'u1', username: 'monty', name: 'Burns Monty' },
      { id: 'u2', username: 'homer', name: 'Simpson Homer' },
    ];
    const chipLabels = (f: ComponentFixture<ResourcePickerComponent>) =>
      Array.from((f.nativeElement as HTMLElement).querySelectorAll('.chips button')).map((b) =>
        b.textContent?.trim(),
      );
    expect(chipLabels(await create([room(1)], {}, users))).toContain('Benutzer');
    expect(chipLabels(await create([room(1)], { mode: 'assign' }, users))).not.toContain(
      'Benutzer',
    );
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
