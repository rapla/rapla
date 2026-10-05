import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { ResourcePickerComponent, type PickEvent } from './resource-picker.component';
import type { AvailabilityRow } from '../event/availability-search.service';
import { RecentsFavoritesService } from '../state/recents-favorites.service';
import { ReviewStore } from '../state/review-store';
import type { FilterEntry } from '../state/filter-store';

const room = (i: number, typeKey = 'room', typeName = 'Raum') => ({
  id: `r${i}`,
  kind: 'RESOURCE',
  name: `Raum ${String(i).padStart(2, '0')}`,
  classification: { typeKey, type: { name: typeName } },
});

/** PRD 123 D1 / PRD 127 — the shared picker: accordion sections, type folders, grouped search, the assign host. */
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
    Array.from(el.querySelectorAll('.item .lbl')).map(
      (i) => i.childNodes[0]?.textContent?.trim() ?? '',
    );
  const heads = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('.sechead .secbtn .glabel')).map((g) => g.textContent?.trim());
  const folders = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('.grouprow')).map(
      (r) =>
        `${r.querySelector('.glabel')?.textContent?.trim()} ${r.querySelector('.count')?.textContent?.trim()}`,
    );

  async function openFolder(f: ComponentFixture<ResourcePickerComponent>, typeName: string) {
    const row = Array.from(
      (f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.grouprow'),
    ).find((r) => r.querySelector('.glabel')?.textContent?.trim() === typeName)!;
    row.querySelector<HTMLButtonElement>('.toggle')!.click();
    await f.whenStable();
    f.detectChanges();
  }

  it('PRD 127 D5 — Ressourcen holds one folder per type in server order; a folder opens its resources', async () => {
    const f = await create([room(2, 'room', 'Raum'), room(1, 'camera', 'Kamera'), room(3)]);
    const el = f.nativeElement as HTMLElement;
    expect(heads(el)).toEqual(['Ressourcen']); // no persons → no Personen section
    expect(folders(el)).toEqual(['Raum 2', 'Kamera 1']);
    expect(el.querySelector('.grouprow .ico')?.textContent?.trim()).toBe('folder');
    await openFolder(f, 'Raum');
    expect(labels(el)).toEqual(['Raum 02', 'Raum 03']);
    expect(el.querySelector('.grouprow .ico')?.textContent?.trim()).toBe('folder_open');
  });

  it('PRD 127 D1 — persons sit in their own section; exactly one section is open', async () => {
    const person = (i: number, typeKey: string, typeName: string) => ({
      ...room(i, typeKey, typeName),
      kind: 'PERSON',
    });
    const f = await create([
      room(1),
      person(3, 'lecturer', 'Dozent'),
      person(5, 'student', 'Assistenz'),
    ]);
    const el = f.nativeElement as HTMLElement;
    expect(folders(el)).toEqual(['Raum 1']);
    const personsHead = el.querySelectorAll<HTMLButtonElement>('.sechead .secbtn')[1];
    personsHead.click();
    await f.whenStable();
    f.detectChanges();
    expect(f.componentInstance.chip()).toBe('persons');
    expect(folders(el)).toEqual(['Dozent 1', 'Assistenz 1']);
    expect(
      Array.from(el.querySelectorAll('.sechead .secbtn')).map((b) =>
        b.getAttribute('aria-expanded'),
      ),
    ).toEqual(['false', 'true']);
  });

  it('PRD 120 D7 — a building row toggles open to its rooms; the row has no count and no alle wählen', async () => {
    const building = { ...room(1, 'building', 'Gebäude'), name: 'Gebäude A' };
    const child = { ...room(2), parents: [{ id: 'r1' }] };
    const f = await create([building, child], { mode: 'rail' });
    const el = f.nativeElement as HTMLElement;
    await openFolder(f, 'Gebäude');
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

  it('PRD 120 D12 — a folder pages its top-level rows; an expanded building adds its rooms without moving "Weitere"', async () => {
    const building = { ...room(0), name: 'Aaa Gebäude' };
    const children = [1, 2, 3].map((i) => ({
      ...room(i, 'part', 'Teil'),
      parents: [{ id: 'r0' }],
    }));
    const rest = Array.from({ length: 120 }, (_, i) => room(10 + i));
    const f = await create([building, ...children, ...rest]);
    const el = f.nativeElement as HTMLElement;
    await openFolder(f, 'Raum');
    const more = () => el.querySelector('.showmore')?.textContent?.trim();
    expect(labels(el).length).toBe(100);
    const before = more();
    (el.querySelector('.item button.toggle') as HTMLButtonElement).click();
    f.detectChanges();
    expect(labels(el).length).toBe(103);
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
    expect(folders(el)).toEqual(['Raum 2']);
  });

  it('PRD 127 D2 — a search is flat under one heading per type, parents in small text, users last', async () => {
    const building = { ...room(1, 'building', 'Gebäude'), name: 'Haus Nord' };
    const hit = { ...room(2), name: 'Nordsaal', parents: [{ id: 'r1' }] };
    const f = await create([building, hit], { query: 'nord' }, [
      { id: 'u1', username: 'nordmann', name: 'Nordmann X' },
    ]);
    const el = f.nativeElement as HTMLElement;
    expect(
      Array.from(el.querySelectorAll('.typehdr')).map((h) =>
        Array.from(h.querySelectorAll('span'), (t) => t.textContent?.trim()).join(' '),
      ),
    ).toEqual(['Gebäude 1', 'Raum 1', 'Benutzer 1']);
    expect(labels(el)).toEqual(['Haus Nord', 'Nordsaal', 'Nordmann X']);
    expect(el.querySelectorAll('.item .path')[0]?.textContent?.trim()).toBe('Haus Nord');
    expect(el.querySelector('.sechead')).toBeNull();
  });

  it('PRD 127 D1 — a click on the open head closes it', async () => {
    const f = await create([room(1)]);
    const el = f.nativeElement as HTMLElement;
    const expanded = () =>
      Array.from(el.querySelectorAll('.sechead .secbtn')).map((b) =>
        b.getAttribute('aria-expanded'),
      );
    expect(expanded()).toEqual(['true']);
    el.querySelector<HTMLButtonElement>('.sechead .secbtn')!.click();
    await f.whenStable();
    f.detectChanges();
    expect(f.componentInstance.chip()).toBe('none');
    expect(expanded()).toEqual(['false']);
    expect(el.querySelector('.grouprow')).toBeNull();
  });

  it('PRD 127 D1 — the heads below the open section stick to the bottom, stacked; the heads above do not', async () => {
    const person = { ...room(2, 'lecturer', 'Dozent'), kind: 'PERSON' };
    const f = await create([room(1), person], {}, [
      { id: 'u1', username: 'monty', name: 'Burns Monty' },
      { id: 'u2', username: 'homer', name: 'Simpson Homer' },
    ]);
    const el = f.nativeElement as HTMLElement;
    const sticky = () =>
      Array.from(el.querySelectorAll<HTMLElement>('.sechead')).map((h) => [
        h.querySelector('.glabel')?.textContent?.trim(),
        h.classList.contains('below'),
        h.style.bottom,
      ]);
    expect(sticky()).toEqual([
      ['Ressourcen', false, ''],
      ['Personen', true, 'calc(var(--sechead-h) * 1)'],
      ['Benutzer', true, 'calc(var(--sechead-h) * 0)'],
    ]);
    el.querySelectorAll<HTMLButtonElement>('.sechead .secbtn')[1].click();
    await f.whenStable();
    f.detectChanges();
    expect(sticky()).toEqual([
      ['Ressourcen', false, ''],
      ['Personen', false, ''],
      ['Benutzer', true, 'calc(var(--sechead-h) * 0)'],
    ]);
  });

  it('PRD 127 — every row names its full label in a native tooltip', async () => {
    const long = { ...room(1), name: 'Raum mit einem sehr langen Namen, der abgeschnitten wird' };
    const grouped = { ...room(2), groupPaths: [['Gebäude A']] } as ReturnType<typeof room>;
    const f = await create([long, grouped]);
    const el = f.nativeElement as HTMLElement;
    await openFolder(f, 'Raum');
    expect(el.querySelector('.grouprow')?.getAttribute('title')).toBe('Raum');
    expect(
      Array.from(el.querySelectorAll('.grouprow')).map((g) => g.getAttribute('title')),
    ).toContain('Gebäude A');
    expect(el.querySelector('.item')?.getAttribute('title')).toBe(long.name);
  });

  it('PRD 127 D2 — a type heading in the search folds its hits away, keeps its count, and opens again for a new query', async () => {
    const f = await create([room(1), room(2), room(3, 'camera', 'Kamera')], { query: 'raum' });
    const el = f.nativeElement as HTMLElement;
    const heading = () => el.querySelector<HTMLButtonElement>('button.typehdr')!;
    expect(heading().getAttribute('aria-expanded')).toBe('true');
    expect(heading().textContent).toContain('▾');
    expect(labels(el)).toEqual(['Raum 01', 'Raum 02', 'Raum 03']);
    heading().click();
    f.detectChanges();
    expect(heading().getAttribute('aria-expanded')).toBe('false');
    expect(heading().textContent).toContain('▸');
    expect(heading().querySelector('.count')?.textContent?.trim()).toBe('2');
    expect(labels(el)).toEqual(['Raum 03']);
    expect(heading().querySelector('.selectall')).toBeNull();
    f.componentInstance.query.set('raum 0');
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(heading().getAttribute('aria-expanded')).toBe('true');
  });

  it('PRD 127 D7 — a folder toggle and a resource click report their type', async () => {
    const f = await create([room(1), room(2, 'camera', 'Kamera')]);
    const types: string[] = [];
    f.componentInstance.typeClick.subscribe((t) => types.push(t));
    await openFolder(f, 'Kamera');
    (f.nativeElement as HTMLElement).querySelector<HTMLElement>('.item')!.click();
    await openFolder(f, 'Raum');
    expect(types).toEqual(['camera', 'camera', 'room']);
  });

  it('PRD 127 D8 — section heads carry label and count only; no count row, no "zuklappen"', async () => {
    const f = await create([room(1), room(2)]);
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('.sechead .selectall')).toBeNull();
    expect(el.querySelector('.sechead .count')?.textContent?.trim()).toBe('2');
    expect(el.querySelector('.listhdr, .collapse')).toBeNull();
  });

  it('PRD 127 D8 — the search bar carries the hit count and "alle wählen", flipping to "Auswahl aufheben"', async () => {
    const f = await create([room(1), room(2), room(3)], { query: 'raum' });
    const el = f.nativeElement as HTMLElement;
    const btn = () => el.querySelector<HTMLButtonElement>('.searchnote .listall');
    expect(el.querySelector('.searchnote')?.textContent).toContain('3 Treffer');
    expect(btn()?.textContent?.trim()).toBe('alle wählen');
    f.componentRef.setInput('checked', new Set(['r1', 'r2', 'r3']));
    f.detectChanges();
    expect(btn()?.textContent?.trim()).toBe('Auswahl aufheben');
    const assign = await create([room(1)], { query: 'raum', mode: 'assign' });
    expect((assign.nativeElement as HTMLElement).querySelector('.listall')).toBeNull();
  });

  it('PRD 127 D8 — a type folder\'s "alle wählen" flips to "Auswahl aufheben" once all its members are selected', async () => {
    const f = await create([room(1), room(2)]);
    const el = f.nativeElement as HTMLElement;
    const btn = () => el.querySelector<HTMLButtonElement>('.grouprow .selectall')!;
    expect(btn().textContent?.trim()).toBe('alle wählen');
    f.componentRef.setInput('checked', new Set(['r1']));
    f.detectChanges();
    expect(btn().textContent?.trim()).toBe('alle wählen');
    f.componentRef.setInput('checked', new Set(['r1', 'r2']));
    f.detectChanges();
    expect(btn().textContent?.trim()).toBe('Auswahl aufheben');
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
    expect(el.querySelector('.sechead .selectall')).toBeNull();
    await openFolder(f, 'Raum');
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
    await openFolder(f, 'Raum');
    const el = f.nativeElement as HTMLElement;
    for (const t of Array.from(el.querySelectorAll<HTMLElement>('.grouprow .toggle')).slice(1))
      t.click();
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

  it('D9 — the Benutzer section is rail-only: the assign host cannot assign accounts', async () => {
    const users = [
      { id: 'u1', username: 'monty', name: 'Burns Monty' },
      { id: 'u2', username: 'homer', name: 'Simpson Homer' },
    ];
    expect(heads((await create([room(1)], {}, users)).nativeElement)).toContain('Benutzer');
    expect(heads((await create([room(1)], { mode: 'assign' }, users)).nativeElement)).not.toContain(
      'Benutzer',
    );
  });

  it('assign mode: arrows step the rows and Enter picks', async () => {
    const f = await create([room(1), room(2)], { mode: 'assign' });
    await openFolder(f, 'Raum');
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

  it('PRD 127 D7 — a click on a search heading counts as a type click for "+ Neu"; the Benutzer heading does not', async () => {
    const f = await create([room(1), room(2, 'camera', 'Kamera')], { query: 'Raum' }, [
      { id: 'u1', username: 'raumwart', name: 'Raumwart' },
    ]);
    const types: string[] = [];
    f.componentInstance.typeClick.subscribe((t) => types.push(t));
    const el = f.nativeElement as HTMLElement;
    for (const h of Array.from(el.querySelectorAll<HTMLElement>('.typehdr'))) h.click();
    expect(types).toEqual(['room', 'camera']);
  });

  describe('PRD 128 D7 — the conflict / request tree as the picker source of a Prüfen view', () => {
    const bucket = (resourceId: string, disabled: boolean, count: number) => ({
      keys: [
        { key: 'RESOURCE', value: resourceId },
        { key: 'DISABLED', value: String(disabled) },
      ],
      count,
    });
    const REQUEST = {
      resource: { id: 'r2', name: 'Raum 02' },
      reservationId: 'v1',
      reservation: { name: 'Tagung' },
      appointments: [{ start: '2026-10-06T09:00:00' }],
    };

    async function withReview(
      buckets: unknown[],
      requests: unknown[],
      inputs: Record<string, unknown> = {},
    ): Promise<ComponentFixture<ResourcePickerComponent>> {
      const f = await create([room(1), room(2)], inputs);
      TestBed.inject(ReviewStore).ensureLoaded();
      http
        .expectOne((r) => String(r.body?.query).includes('conflictStats'))
        .flush({ data: { conflictStats: buckets, resourceRequests: requests } });
      await f.whenStable();
      f.detectChanges();
      return f;
    }

    it('the accordion has only the five Planen sections, whatever the review data (D7)', async () => {
      const f = await withReview([bucket('r1', false, 2)], [REQUEST]);
      expect(heads(f.nativeElement)).toEqual(['Ressourcen']);
    });

    it('a review source replaces the accordion by its tree: no section heads, no "Meine Buchungen"', async () => {
      const f = await withReview([bucket('r1', false, 2)], [REQUEST], { source: 'requests' });
      const el = f.nativeElement as HTMLElement;
      expect(heads(el)).toEqual([]);
      expect(el.querySelector('.sechead')).toBeNull();
      expect(folders(el)).toEqual(['Raum 1']);
    });

    it('Konflikte: type → resource; opening a resource loads its conflicts; a click hands out the chip and its date', async () => {
      const f = await withReview([bucket('r1', false, 1), bucket('r2', true, 3)], [], {
        source: 'conflicts',
      });
      const el = f.nativeElement as HTMLElement;
      expect(folders(el)).toEqual(['Raum 1', 'Deaktivierte Konflikte 3']);
      expect(el.querySelector('.grouprow .selectall')).toBeNull();
      await openFolder(f, 'Raum');
      await openFolder(f, 'Raum 01');
      const req = http.expectOne((r) => String(r.body?.query).includes('conflicts(filter'));
      expect(req.request.body.variables).toEqual({ f: { resourceIdsIn: ['r1'] } });
      req.flush({
        data: {
          conflicts: [
            {
              id: 'CONFLICT;r1;a1;a2',
              startDate: '2026-10-05T10:00:00',
              disabled: false,
              description: 'belegt',
              resource: { id: 'r1', name: 'Raum 01' },
              reservation1: { name: 'A' },
              reservation2: null,
            },
          ],
        },
      });
      await f.whenStable();
      f.detectChanges();
      const picks: { entry: FilterEntry; date: string | null; ctrl: boolean }[] = [];
      f.componentInstance.reviewPick.subscribe((p) => picks.push(p));
      const entry = el.querySelector<HTMLElement>('.item.entry')!;
      expect(entry.textContent).toContain('05.10. 10:00 A ↔ belegt');
      entry.dispatchEvent(new MouseEvent('click', { ctrlKey: true, bubbles: true }));
      expect(picks).toEqual([
        {
          entry: {
            id: 'CONFLICT;r1;a1;a2',
            kind: 'conflict',
            label: '⚠ Raum 01 · 05.10.',
            resourceName: 'Raum 01',
          },
          date: '2026-10-05T10:00:00',
          ctrl: true,
        },
      ]);
    });

    it('Ressourcenanfragen lists the requests; a selected chip marks its row', async () => {
      const f = await withReview([], [REQUEST], {
        source: 'requests',
        checked: new Set(['REQUEST;r2;v1']),
      });
      await openFolder(f, 'Raum');
      await openFolder(f, 'Raum 02');
      const entry = (f.nativeElement as HTMLElement).querySelector('.item.entry')!;
      expect(entry.textContent).toContain('Tagung · 06.10.');
      expect(entry.classList).toContain('selected');
    });

    it('search in Konflikte narrows to matching resources and counts them (OQ16)', async () => {
      const f = await withReview([bucket('r1', false, 1), bucket('r2', false, 2)], [], {
        source: 'conflicts',
        query: 'Raum 02',
      });
      const el = f.nativeElement as HTMLElement;
      expect(heads(el)).toEqual([]);
      expect(folders(el)).toEqual(['Raum 2', 'Raum 02 2']);
      expect(el.querySelector('.searchnote')?.textContent).toContain('1 Treffer');
      expect(el.querySelector('.searchnote .listall')).toBeNull();
    });
  });
});
