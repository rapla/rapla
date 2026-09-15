import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ResourceSelectionComponent } from './resource-selection.component';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { FilterStore } from '../state/filter-store';
import { RecentsFavoritesService } from '../state/recents-favorites.service';
import { ResourceEditDialogComponent } from '../resource/resource-edit-dialog.component';

const room = (i: number) => ({
  id: `r${i}`,
  kind: 'RESOURCE',
  name: `Raum ${String(i).padStart(2, '0')}`,
  classification: { typeKey: 'room', type: { name: 'Raum' } },
});

const lecturer = {
  id: 'p1',
  kind: 'PERSON',
  name: 'Prof. Lehmann',
  classification: { typeKey: 'lecturer', type: { name: 'Dozent' } },
};

type Wire = typeof room extends (i: number) => infer R ? R : never;
interface UserWire {
  id: string;
  username: string;
  name: string;
}

describe('ResourceSelectionComponent', () => {
  let resources: ResourceSelectionStore;
  let filter: FilterStore;
  let http: HttpTestingController;
  const dialogOpen = vi.fn();

  beforeEach(async () => {
    localStorage.clear(); // recents/favorites persist — isolate before the store hydrates
    dialogOpen.mockReset();
    dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });
    await TestBed.configureTestingModule({
      imports: [ResourceSelectionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();
    resources = TestBed.inject(ResourceSelectionStore);
    filter = TestBed.inject(FilterStore);
    http = TestBed.inject(HttpTestingController);
    resources.setActiveChip('recents');
    resources.setQuery('');
    resources.setActive(null);
    filter.clear();
  });

  /** The picker loads the lean list once on creation (PRD 119 D8). */
  function flushPickerList(rows: Wire[] = [], users: UserWire[] = []): void {
    for (const req of http.match(
      (r) => r.url === '/api/graphql' && String(r.body?.query).includes('resources'),
    )) {
      req.flush({ data: { resources: rows, users } });
    }
  }

  async function create(rows: Wire[] = [], users: UserWire[] = []) {
    const f = TestBed.createComponent(ResourceSelectionComponent);
    flushPickerList(rows, users);
    await f.whenStable();
    return f;
  }

  async function typeQuery(f: ComponentFixture<ResourceSelectionComponent>, q: string) {
    resources.setQuery(q);
    await f.whenStable();
  }

  const labels = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('.item .lbl')).map((i) => i.textContent?.trim() ?? '');
  const chipButtons = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('.chips button'));
  const recentPosts = () =>
    http.match((r) => r.url === '/api/recents' && r.method === 'POST').length;

  it('a click under Alle never moves the clicked row; the new recent ranks up after a chip change (PRD 119 P3b, user ruling A)', async () => {
    resources.setActiveChip('all');
    const f = await create(Array.from({ length: 5 }, (_, i) => room(i + 1)));
    const el = f.nativeElement as HTMLElement;
    const row = (label: string) =>
      Array.from(el.querySelectorAll<HTMLElement>('.item')).find(
        (i) => i.querySelector('.lbl')?.textContent?.trim() === label,
      )!;
    row('Raum 03').click();
    await f.whenStable();
    expect(labels(el)).toEqual(['Raum 01', 'Raum 02', 'Raum 03', 'Raum 04', 'Raum 05']);
    resources.setActiveChip('favorites');
    await f.whenStable();
    resources.setActiveChip('all');
    await f.whenStable();
    expect(labels(el)[0]).toBe('Raum 03');
  });

  it('recents that arrive after the list still rank on top under Alle (P3b review SHOULD)', async () => {
    resources.setActiveChip('all');
    const f = await create(Array.from({ length: 5 }, (_, i) => room(i + 1)));
    const el = f.nativeElement as HTMLElement;
    expect(labels(el)[0]).toBe('Raum 01');
    const reload = TestBed.inject(RecentsFavoritesService).reload();
    http
      .expectOne((r) => r.url === '/api/recents' && r.method === 'GET')
      .flush([{ id: 'r4', kind: 'resource', label: 'Raum 04', color: null, typeKey: 'room' }]);
    http.expectOne((r) => r.url === '/api/favorites' && r.method === 'GET').flush([]);
    await reload;
    await f.whenStable();
    expect(labels(el)[0]).toBe('Raum 04');
  });

  it('a new user with no recents or favorites sees the first 20 resources on Alle, the rest behind a "Weitere" button', async () => {
    resources.setActiveChip('all');
    const f = await create(Array.from({ length: 25 }, (_, i) => room(i + 1)));
    const el = f.nativeElement as HTMLElement;
    expect(labels(el).length).toBe(20);
    expect(labels(el)[0]).toBe('Raum 01');
    const more = el.querySelector('.showmore') as HTMLElement;
    expect(more.tagName).toBe('BUTTON');
    expect(more.textContent).toContain('Weitere 5 anzeigen');
    more.click();
    await f.whenStable();
    expect(labels(el).length).toBe(25);
  });

  it('has no search input of its own — the top field drives the list (PRD 119 D3)', async () => {
    const f = await create([room(1)]);
    expect((f.nativeElement as HTMLElement).querySelector('input')).toBeNull();
  });

  it('shows a chip row (Alle, Favoriten, Zuletzt, one per type) with aria-pressed on the active chip', async () => {
    const f = await create([room(1), lecturer]);
    const el = f.nativeElement as HTMLElement;
    expect(chipButtons(el).map((b) => b.textContent?.trim())).toEqual([
      'Alle',
      '★ Favoriten',
      'Zuletzt',
      'Dozent',
      'Raum',
    ]);
    expect(chipButtons(el).map((b) => b.getAttribute('aria-pressed'))).toEqual([
      'false',
      'false',
      'true',
      'false',
      'false',
    ]);
  });

  it('a type chip lists only resources of that type, name-sorted', async () => {
    const f = await create([room(2), lecturer, room(1)]);
    const el = f.nativeElement as HTMLElement;
    chipButtons(el)
      .find((b) => b.textContent?.trim() === 'Raum')!
      .click();
    await f.whenStable();
    expect(labels(el)).toEqual(['Raum 01', 'Raum 02']);
  });

  it('the shared query narrows the list without any request to the server', async () => {
    resources.setActiveChip('all');
    const f = await create([room(1), room(2), room(12)]);
    await typeQuery(f, '1');
    expect(labels(f.nativeElement as HTMLElement)).toEqual(['Raum 01', 'Raum 12']);
    http.expectNone('/api/graphql');
    http.expectNone('/api/users');
  });

  it('under Alle, matching users appear only while typing (also by username) and step as a user scope chip', async () => {
    resources.setActiveChip('all');
    const f = await create([room(1)], [{ id: 'u1', username: 'monty', name: 'Burns Monty' }]);
    const el = f.nativeElement as HTMLElement;
    expect(labels(el)).toEqual(['Raum 01']);
    await typeQuery(f, 'mont');
    expect(labels(el)).toEqual(['Burns Monty']);
    (el.querySelector('.item') as HTMLElement).click();
    await f.whenStable();
    expect(filter.entries()).toEqual([
      { id: 'u1', kind: 'user', label: 'Burns Monty', color: undefined },
    ]);
  });

  it('a plain click on a row pushes a recent (PRD 119 D5)', async () => {
    resources.setActiveChip('all');
    const f = await create([room(1)]);
    (f.nativeElement as HTMLElement).querySelector<HTMLElement>('.item')!.click();
    await f.whenStable();
    expect(recentPosts()).toBe(1);
  });

  it('stepping under Zuletzt does NOT push a recent (the stepped list must not reshuffle)', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348' });
    http
      .match('/api/recents')
      .forEach((r) =>
        r.flush([{ id: 'C348', kind: 'resource', label: 'C348', color: null, typeKey: null }]),
      );
    const f = await create();
    const item = (f.nativeElement as HTMLElement).querySelector<HTMLElement>('.item');
    expect(item).not.toBeNull();
    item!.click();
    await f.whenStable();
    expect(recentPosts()).toBe(0);
    expect(resources.activeId()).toBe('C348');
  });

  it('a focus request from the search dropdown focuses the list', async () => {
    const f = await create([room(1)]);
    resources.requestPickerFocus();
    await f.whenStable();
    expect(document.activeElement).toBe((f.nativeElement as HTMLElement).querySelector('.stepper'));
  });

  it('renders the items of the active chip', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348 PC-Hörsaal' });
    resources.pushRecent({ id: 'C452', label: 'C452 Labor' });
    const f = await create();
    const items = labels(f.nativeElement as HTMLElement);
    expect(items.length).toBe(2);
    expect(items[0]).toContain('C452');
  });

  it('switching chip changes the rendered list', async () => {
    resources.pushRecent({ id: 'R', label: 'Recent' });
    const f = await create([room(1)]);
    const el = f.nativeElement as HTMLElement;
    expect(labels(el)).toEqual(['Recent']);
    chipButtons(el)
      .find((b) => b.textContent?.trim() === 'Raum')!
      .click();
    await f.whenStable();
    expect(labels(el)).toEqual(['Raum 01']);
  });

  it('clicking an item makes it active and replaces the filter (stepping)', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348 PC-Hörsaal' });
    resources.pushRecent({ id: 'C452', label: 'C452 Labor' });
    filter.add({ id: 'OLD', kind: 'resource', label: 'old' });
    const f = await create();
    const first = (f.nativeElement as HTMLElement).querySelector('.item') as HTMLElement;
    first.click();
    await f.whenStable();
    expect(resources.activeId()).toBe('C452');
    expect(filter.entries().map((e) => e.id)).toEqual(['C452']);
  });

  it('stepping a user item creates a user-kind scope chip (not a resource filter)', async () => {
    resources.pushRecent({ id: 'u1', label: 'Burns Monty', kind: 'user' });
    const f = await create();
    const first = (f.nativeElement as HTMLElement).querySelector('.item') as HTMLElement;
    first.click();
    await f.whenStable();
    expect(filter.entries()).toEqual([
      { id: 'u1', kind: 'user', label: 'Burns Monty', color: undefined },
    ]);
  });

  it('toggling the star pins to Favoriten WITHOUT stepping the row', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348' });
    const f = await create();
    (f.nativeElement as HTMLElement)
      .querySelector('.fav')!
      .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await f.whenStable();
    expect(resources.isFavorite('C348')).toBe(true);
    expect(resources.activeId()).toBeNull(); // star did not step
    expect(filter.isEmpty()).toBe(true);
  });

  it('a saved resource edit reloads the picker list (PRD 119 OQ6)', async () => {
    resources.pushRecent({ id: 'cam1', label: 'Canon G25 01' });
    dialogOpen.mockReturnValue({ afterClosed: () => of('saved') });
    const f = await create();
    const el = f.nativeElement as HTMLElement;
    (el.querySelector('.act') as HTMLButtonElement).click();
    await f.whenStable();
    Array.from(document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-item'))[0].click();
    await f.whenStable();
    const reload = http.match(
      (r) => r.url === '/api/graphql' && String(r.body?.query).includes('resources'),
    );
    expect(reload.length).toBe(1);
    reload[0].flush({ data: { resources: [], users: [] } });
  });

  describe('Swing-tree selection semantics (PRD 099 Phase 4)', () => {
    async function makeList() {
      resources.setActiveChip('type:room');
      return create([room(1), room(2), room(3), room(4)]);
    }
    const items = (f: { nativeElement: HTMLElement }) =>
      Array.from(f.nativeElement.querySelectorAll<HTMLElement>('.item'));
    const click = (el: HTMLElement, init: MouseEventInit = {}) =>
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, ...init }));
    const keydown = (
      f: { nativeElement: HTMLElement },
      key: string,
      init: KeyboardEventInit = {},
    ) =>
      (f.nativeElement.querySelector('.stepper') as HTMLElement).dispatchEvent(
        new KeyboardEvent('keydown', { key, bubbles: true, ...init }),
      );
    const chipIds = () => filter.entries().map((e) => e.id);

    it('shift-click selects the RANGE, replacing the previous chips', async () => {
      const f = await makeList();
      click(items(f)[0]);
      click(items(f)[2], { shiftKey: true });
      expect(chipIds()).toEqual(['r1', 'r2', 'r3']);
      click(items(f)[1], { shiftKey: true }); // re-range from the same anchor
      expect(chipIds()).toEqual(['r1', 'r2']);
    });

    it('ctrl-click TOGGLES a chip', async () => {
      const f = await makeList();
      click(items(f)[0]);
      click(items(f)[2], { ctrlKey: true });
      expect(chipIds()).toEqual(['r1', 'r3']);
      click(items(f)[0], { ctrlKey: true });
      expect(chipIds()).toEqual(['r3']);
    });

    it('modifier gestures keep chips from outside the list; plain click clears them', async () => {
      const f = await makeList();
      filter.add({ id: 'EXT', kind: 'event', label: 'ext' });
      click(items(f)[0], { ctrlKey: true });
      expect(chipIds()).toEqual(['EXT', 'r1']);
      click(items(f)[1]);
      expect(chipIds()).toEqual(['r2']);
    });

    it('ArrowDown steps to the next item (keyboard stepping)', async () => {
      const f = await makeList();
      click(items(f)[0]);
      keydown(f, 'ArrowDown');
      expect(chipIds()).toEqual(['r2']);
      expect(resources.activeId()).toBe('r2');
    });

    it('Shift+ArrowDown extends the range', async () => {
      const f = await makeList();
      click(items(f)[0]);
      keydown(f, 'ArrowDown', { shiftKey: true });
      expect(chipIds()).toEqual(['r1', 'r2']);
    });

    it('Escape clears the selection', async () => {
      const f = await makeList();
      click(items(f)[0]);
      keydown(f, 'Escape');
      expect(filter.isEmpty()).toBe(true);
    });

    it('an externally removed chip stays removed (model re-syncs from chips)', async () => {
      const f = await makeList();
      click(items(f)[0]);
      click(items(f)[1], { ctrlKey: true });
      filter.remove('r1'); // chip removed in the toolbar
      click(items(f)[2], { ctrlKey: true });
      expect(chipIds()).toEqual(['r2', 'r3']); // r1 did not resurrect
    });

    it('selected items are highlighted in the list', async () => {
      const f = await makeList();
      click(items(f)[0]);
      click(items(f)[2], { ctrlKey: true });
      f.detectChanges();
      const selected = items(f).filter((el) => el.classList.contains('selected'));
      expect(selected.map((el) => el.textContent ?? '')).toEqual([
        expect.stringContaining('Raum 01'),
        expect.stringContaining('Raum 03'),
      ]);
    });
  });

  it('⋮ menu appears on resource items only; Bearbeiten/Anzeigen open the dialog (PRD 096)', async () => {
    resources.pushRecent({ id: 'cam1', label: 'Canon G25 01' });
    resources.pushRecent({ id: 'u1', label: 'Burns Monty', kind: 'user' });
    const f = await create();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.act').length).toBe(1); // user rows get no editor menu

    (el.querySelector('.act') as HTMLButtonElement).click();
    await f.whenStable();
    const menuItems = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-item'),
    );
    expect(menuItems.map((m) => m.textContent?.trim())).toEqual(['Bearbeiten', 'Anzeigen']);

    menuItems[0].click();
    await f.whenStable();
    expect(dialogOpen).toHaveBeenCalledWith(
      ResourceEditDialogComponent,
      expect.objectContaining({ data: { id: 'cam1', readOnly: false } }),
    );
    // the ⋮ click must not step/replace the filter
    expect(filter.isEmpty()).toBe(true);
    expect(resources.activeId()).toBeNull();
  });

  describe('group tree under a type chip (PRD 119 D2/D11)', () => {
    const grouped = (id: string, name: string, groupPaths: string[][] = []) => ({
      id,
      kind: 'RESOURCE',
      name,
      classification: { typeKey: 'room', type: { name: 'Raum' } },
      groupPaths,
    });
    const rows = (el: HTMLElement) =>
      Array.from(el.querySelectorAll<HTMLElement>('.grouprow, .item')).map((r) =>
        r.classList.contains('grouprow')
          ? `▸ ${r.querySelector('.glabel')?.textContent?.trim()} (${r.querySelector('.count')?.textContent?.trim()})`
          : (r.querySelector('.lbl')?.textContent?.trim() ?? ''),
      );
    const group = (el: HTMLElement, label: string) =>
      Array.from(el.querySelectorAll<HTMLElement>('.grouprow')).find(
        (r) => r.querySelector('.glabel')?.textContent?.trim() === label,
      )!;
    const fixture = [
      grouped('r1', 'Hörsaal 1', [['Gebäude A']]),
      grouped('r2', 'Labor', [['Gebäude A']]),
      grouped('r3', 'Hörsaal 2', [['Gebäude B']]),
      grouped('r4', 'Aula'),
    ];

    it('shows collapsed groups with member counts, the ungrouped resources after them', async () => {
      resources.setActiveChip('type:room');
      const f = await create(fixture);
      expect(rows(f.nativeElement as HTMLElement)).toEqual([
        '▸ Gebäude A (2)',
        '▸ Gebäude B (1)',
        'Aula',
      ]);
    });

    it('expanding a group shows its members', async () => {
      resources.setActiveChip('type:room');
      const f = await create(fixture);
      const el = f.nativeElement as HTMLElement;
      const toggle = group(el, 'Gebäude A').querySelector<HTMLButtonElement>('.toggle')!;
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      toggle.click();
      await f.whenStable();
      expect(rows(el)).toEqual([
        '▸ Gebäude A (2)',
        'Hörsaal 1',
        'Labor',
        '▸ Gebäude B (1)',
        'Aula',
      ]);
      expect(group(el, 'Gebäude A').querySelector('.toggle')?.getAttribute('aria-expanded')).toBe(
        'true',
      );
      // review S1 — the controls name their group
      expect(group(el, 'Gebäude A').querySelector('.toggle')?.getAttribute('aria-label')).toBe(
        'Gebäude A',
      );
      expect(group(el, 'Gebäude A').querySelector('.selectall')?.getAttribute('aria-label')).toBe(
        'Alle in Gebäude A wählen',
      );
    });

    it('"alle wählen" replaces the selection with the members of the group', async () => {
      resources.setActiveChip('type:room');
      filter.add({ id: 'OLD', kind: 'resource', label: 'old' });
      const f = await create(fixture);
      group(f.nativeElement as HTMLElement, 'Gebäude A')
        .querySelector<HTMLButtonElement>('.selectall')!
        .click();
      await f.whenStable();
      expect(filter.entries().map((e) => e.id)).toEqual(['r1', 'r2']);
    });

    it('the query opens the branches with matches and drops the rest', async () => {
      resources.setActiveChip('type:room');
      const f = await create(fixture);
      await typeQuery(f, 'hörsaal');
      expect(rows(f.nativeElement as HTMLElement)).toEqual([
        '▸ Gebäude A (1)',
        'Hörsaal 1',
        '▸ Gebäude B (1)',
        'Hörsaal 2',
      ]);
    });

    it('under Alle, a group whose name matches the query appears as an expandable row', async () => {
      resources.setActiveChip('all');
      const f = await create(fixture);
      await typeQuery(f, 'gebäude a');
      const el = f.nativeElement as HTMLElement;
      expect(group(el, 'Gebäude A')).toBeTruthy();
      group(el, 'Gebäude A').querySelector<HTMLButtonElement>('.toggle')!.click();
      await f.whenStable();
      expect(rows(el)).toContain('Labor');
    });

    it('D12 — a type with more than 100 resources shows 100, then "Weitere 50 anzeigen" reveals the rest', async () => {
      resources.setActiveChip('type:room');
      const f = await create(Array.from({ length: 150 }, (_, i) => room(i + 1)));
      const el = f.nativeElement as HTMLElement;
      expect(labels(el).length).toBe(100);
      const more = el.querySelector<HTMLButtonElement>('.showmore')!;
      expect(more.textContent?.trim()).toBe('Weitere 50 anzeigen');
      more.click();
      await f.whenStable();
      expect(labels(el).length).toBe(150);
      expect(el.querySelector('.showmore')).toBeNull();
    });

    it('D12 — an open group with more than 100 members shows 100 of them, then "Weitere"', async () => {
      resources.setActiveChip('type:room');
      const f = await create(
        Array.from({ length: 120 }, (_, i) => grouped(`g${i}`, `Raum ${i}`, [['Gebäude A']])),
      );
      const el = f.nativeElement as HTMLElement;
      group(el, 'Gebäude A').querySelector<HTMLButtonElement>('.toggle')!.click();
      await f.whenStable();
      expect(labels(el).length).toBe(100);
      const more = el.querySelector<HTMLButtonElement>('.showmore')!;
      expect(more.textContent?.trim()).toBe('Weitere 20 anzeigen');
      more.click();
      await f.whenStable();
      expect(labels(el).length).toBe(120);
    });

    it('D12 — a search result stops at 100 rows, "Weitere" reveals the next block', async () => {
      resources.setActiveChip('all');
      const f = await create(Array.from({ length: 150 }, (_, i) => room(i + 1)));
      await typeQuery(f, 'raum');
      const el = f.nativeElement as HTMLElement;
      expect(labels(el).length).toBe(100);
      expect(el.querySelector('.showmore')?.textContent?.trim()).toBe('Weitere 50 anzeigen');
    });

    it('D12 — groups start collapsed except the path to a selected resource', async () => {
      resources.setActiveChip('type:room');
      filter.add({ id: 'r2', kind: 'resource', label: 'Labor' });
      const f = await create(fixture);
      const el = f.nativeElement as HTMLElement;
      expect(rows(el)).toEqual([
        '▸ Gebäude A (2)',
        'Hörsaal 1',
        'Labor',
        '▸ Gebäude B (1)',
        'Aula',
      ]);
      expect(group(el, 'Gebäude A').querySelector('.toggle')?.getAttribute('aria-expanded')).toBe(
        'true',
      );
    });

    it('click, Ctrl and Shift select over the resource rows of an expanded tree', async () => {
      resources.setActiveChip('type:room');
      const f = await create(fixture);
      const el = f.nativeElement as HTMLElement;
      for (const g of ['Gebäude A', 'Gebäude B']) {
        group(el, g).querySelector<HTMLButtonElement>('.toggle')!.click();
        await f.whenStable();
      }
      const item = (label: string) =>
        Array.from(el.querySelectorAll<HTMLElement>('.item')).find(
          (i) => i.querySelector('.lbl')?.textContent?.trim() === label,
        )!;
      const click = (label: string, init: MouseEventInit = {}) =>
        item(label).dispatchEvent(new MouseEvent('click', { bubbles: true, ...init }));
      const ids = () =>
        filter
          .entries()
          .map((e) => e.id)
          .sort();

      click('Hörsaal 1');
      await f.whenStable();
      expect(ids()).toEqual(['r1']);
      click('Hörsaal 2', { shiftKey: true }); // range across the group boundary
      await f.whenStable();
      expect(ids()).toEqual(['r1', 'r2', 'r3']);
      click('Labor', { ctrlKey: true });
      await f.whenStable();
      expect(ids()).toEqual(['r1', 'r3']);
      // selecting inside an open group keeps it open
      expect(rows(el)).toEqual([
        '▸ Gebäude A (2)',
        'Hörsaal 1',
        'Labor',
        '▸ Gebäude B (1)',
        'Hörsaal 2',
        'Aula',
      ]);
    });

    it('shows every resource of a type without the "Weitere" page (the tree is collapsible)', async () => {
      resources.setActiveChip('type:room');
      const f = await create(Array.from({ length: 25 }, (_, i) => room(i + 1)));
      const el = f.nativeElement as HTMLElement;
      expect(labels(el).length).toBe(25);
      expect(el.querySelector('.showmore')).toBeNull();
    });
  });
});
