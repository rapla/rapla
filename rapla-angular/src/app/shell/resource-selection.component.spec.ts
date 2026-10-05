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
import { ViewStateStore } from '../state/view-state-store';
import { RecentsFavoritesService } from '../state/recents-favorites.service';
import { ResourceEditDialogComponent } from '../resource/resource-edit-dialog.component';
import { AuthService, type Identity } from '../auth/auth.service';

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
  interface Creatable {
    key: string;
    name: string;
    classificationType: string;
  }

  function flushPickerList(
    rows: Wire[] = [],
    users: UserWire[] = [],
    creatable: Creatable[] = [],
  ): void {
    for (const req of http.match(
      (r) => r.url === '/api/graphql' && String(r.body?.query).includes('resources'),
    )) {
      req.flush({ data: { resources: rows, users } });
    }
    // PRD 128 — the rail asks for the Konflikte / Ressourcenanfragen heads once.
    for (const req of http.match(
      (r) => r.url === '/api/graphql' && String(r.body?.query).includes('conflictStats'),
    )) {
      req.flush({ data: { conflictStats: [], resourceRequests: [] } });
    }
    // PRD 122 — the "+ Neu" button asks for the creatable types once per rail.
    for (const req of http.match(
      (r) => r.url === '/api/graphql' && String(r.body?.query).includes('newResourceOptions'),
    )) {
      req.flush({ data: { newResourceOptions: { resourceTypes: creatable } } });
    }
  }

  /** PRD 127 D5 — toggle a type folder of the open section. */
  async function openFolder(f: ComponentFixture<ResourceSelectionComponent>, typeName = 'Raum') {
    const row = Array.from(
      (f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.grouprow'),
    ).find((r) => r.querySelector('.glabel')?.textContent?.trim() === typeName)!;
    row.querySelector<HTMLButtonElement>('.toggle')!.click();
    await f.whenStable();
    f.detectChanges();
  }

  /** PRD 127 D1 — click a section head. */
  async function openSectionHead(f: ComponentFixture<ResourceSelectionComponent>, label: string) {
    const head = Array.from(
      (f.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.sechead .secbtn'),
    ).find((b) => b.querySelector('.glabel')?.textContent?.trim() === label)!;
    head.click();
    await f.whenStable();
    f.detectChanges();
  }

  async function create(rows: Wire[] = [], users: UserWire[] = [], creatable: Creatable[] = []) {
    const f = TestBed.createComponent(ResourceSelectionComponent);
    flushPickerList(rows, users, creatable);
    await f.whenStable();
    f.detectChanges();
    return f;
  }

  const newButton = (f: ComponentFixture<ResourceSelectionComponent>) =>
    (f.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.newbtn');

  // PRD 122 D1/D3 (ruling B1) — the "+ Neu" button in the rail header
  it('"+ Neu" is hidden when the server offers no creatable type; the header row stays for the search field (PRD 119 D13)', async () => {
    const f = await create([room(1)]);
    expect(newButton(f)).toBeNull();
    expect((f.nativeElement as HTMLElement).querySelector('.sthead input.pksearch')).not.toBeNull();
  });

  it('the rail header carries no label, only "+ Neu" (PRD 127, user 2026-10-04)', async () => {
    const f = await create(
      [room(1)],
      [],
      [{ key: 'room', name: 'Raum', classificationType: 'roomClassification' }],
    );
    const head = (f.nativeElement as HTMLElement).querySelector('.sthead')!;
    expect(head.textContent?.trim()).toBe('+ Neu');
    expect(head.firstElementChild?.matches('input.pksearch')).toBe(true);
  });

  it('"+ Neu" preselects the type of the last clicked type folder or resource, else the first creatable type (PRD 127 D7)', async () => {
    const creatable: Creatable[] = [
      { key: 'lecturer', name: 'Dozent', classificationType: 'lecturerClassification' },
      { key: 'room', name: 'Raum', classificationType: 'roomClassification' },
    ];
    resources.setActiveChip('resources');
    const f = await create([room(1), lecturer], [], creatable);
    newButton(f)!.click();
    expect(dialogOpen).toHaveBeenLastCalledWith(
      ResourceEditDialogComponent,
      expect.objectContaining({ data: { create: { typeKey: 'lecturer' } } }),
    );
    await openFolder(f, 'Raum');
    newButton(f)!.click();
    expect(dialogOpen).toHaveBeenLastCalledWith(
      ResourceEditDialogComponent,
      expect.objectContaining({ data: { create: { typeKey: 'room' } } }),
    );
    await openSectionHead(f, 'Personen');
    await openFolder(f, 'Dozent');
    (f.nativeElement as HTMLElement).querySelector<HTMLElement>('.item')!.click();
    await openSectionHead(f, 'Ressourcen');
    newButton(f)!.click();
    expect(dialogOpen).toHaveBeenLastCalledWith(
      ResourceEditDialogComponent,
      expect.objectContaining({ data: { create: { typeKey: 'lecturer' } } }),
    );
  });

  it('a saved new resource reloads the picker list (PRD 122 D6)', async () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of('saved') });
    resources.setActiveChip('');
    const f = await create(
      [room(1)],
      [],
      [{ key: 'room', name: 'Raum', classificationType: 'roomClassification' }],
    );
    newButton(f)!.click();
    await f.whenStable();
    const reload = http.match(
      (r) => r.url === '/api/graphql' && String(r.body?.query).includes('resources'),
    );
    expect(reload.length).toBe(1);
    reload[0].flush({ data: { resources: [room(1), room(2)], users: [] } });
    await f.whenStable();
    await openFolder(f);
    expect(labels(f.nativeElement as HTMLElement)).toEqual(['Raum 01', 'Raum 02']);
  });

  async function typeQuery(f: ComponentFixture<ResourceSelectionComponent>, q: string) {
    resources.setQuery(q);
    await f.whenStable();
  }

  const labels = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('.item .lbl')).map((i) => i.textContent?.trim() ?? '');
  const heads = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('.sechead .secbtn'));
  const headLabels = (el: HTMLElement) =>
    heads(el).map((b) => b.querySelector('.glabel')?.textContent?.trim());
  const recentPosts = () =>
    http.match((r) => r.url === '/api/recents' && r.method === 'POST').length;

  it('a click in a type folder never moves the clicked row (server order, PRD 127 D6)', async () => {
    resources.setActiveChip('resources');
    const f = await create(Array.from({ length: 5 }, (_, i) => room(i + 1)));
    const el = f.nativeElement as HTMLElement;
    await openFolder(f);
    const row = (label: string) =>
      Array.from(el.querySelectorAll<HTMLElement>('.item')).find(
        (i) => i.querySelector('.lbl')?.textContent?.trim() === label,
      )!;
    row('Raum 03').click();
    await f.whenStable();
    expect(labels(el)).toEqual(['Raum 01', 'Raum 02', 'Raum 03', 'Raum 04', 'Raum 05']);
  });

  it('recents that arrive after the list add the Zuletzt section (PRD 127 D1)', async () => {
    resources.setActiveChip('');
    const f = await create(Array.from({ length: 5 }, (_, i) => room(i + 1)));
    const el = f.nativeElement as HTMLElement;
    expect(headLabels(el)).toEqual(['Ressourcen']);
    const reload = TestBed.inject(RecentsFavoritesService).reload();
    http
      .expectOne((r) => r.url === '/api/recents' && r.method === 'GET')
      .flush([{ id: 'r4', kind: 'resource', label: 'Raum 04', color: null, typeKey: 'room' }]);
    http.expectOne((r) => r.url === '/api/favorites' && r.method === 'GET').flush([]);
    await reload;
    await f.whenStable();
    expect(headLabels(el)).toEqual(['Zuletzt', 'Ressourcen']);
  });

  it('a new user with no recents or favorites starts on Ressourcen with its type folders closed (PRD 127 D1)', async () => {
    resources.setActiveChip('');
    const f = await create(Array.from({ length: 25 }, (_, i) => room(i + 1)));
    const el = f.nativeElement as HTMLElement;
    expect(resources.activeChip()).toBe('resources');
    expect(labels(el)).toEqual([]);
    expect(el.querySelector('.grouprow .glabel')?.textContent?.trim()).toBe('Raum');
    expect(el.querySelector('.grouprow .count')?.textContent?.trim()).toBe('25');
  });

  it('PRD 128 D1/OQ15 — a conflict click replaces the chips of its context and jumps to its date; Ctrl adds', async () => {
    const f = await create([room(1)]);
    const viewState = TestBed.inject(ViewStateStore);
    viewState.setWindow({ from: '2026-06-01T00:00:00', to: '2026-06-08T00:00:00' });
    filter.setContext('conflicts');
    const k = (id: string) => ({ id, kind: 'conflict' as const, label: id });
    const host = f.componentInstance as unknown as {
      reviewPick(e: { entry: ReturnType<typeof k>; date: string | null; ctrl: boolean }): void;
    };
    host.reviewPick({ entry: k('CONFLICT;r1;a;b'), date: '2026-10-07T10:00:00', ctrl: false });
    expect(filter.entries().map((e) => e.id)).toEqual(['CONFLICT;r1;a;b']);
    expect(viewState.window()).toEqual({ from: '2026-10-05T00:00:00', to: '2026-10-12T00:00:00' });
    host.reviewPick({ entry: k('CONFLICT;r1;c;d'), date: null, ctrl: true });
    expect(filter.entries().map((e) => e.id)).toEqual(['CONFLICT;r1;a;b', 'CONFLICT;r1;c;d']);
    host.reviewPick({ entry: k('CONFLICT;r1;c;d'), date: null, ctrl: true });
    expect(filter.entries().map((e) => e.id)).toEqual(['CONFLICT;r1;a;b']);
    filter.setContext('plan');
  });

  describe('PRD 119 D13 — the picker search field', () => {
    const field = (f: { nativeElement: unknown }) =>
      (f.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input.pksearch')!;
    const press = (f: { nativeElement: unknown }, key: string, init: KeyboardEventInit = {}) =>
      field(f).dispatchEvent(
        new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init }),
      );

    it('has exactly one search input; typing narrows the picker through the persisted store query', async () => {
      resources.setActiveChip('resources');
      const f = await create([room(1), room(2)]);
      const el = f.nativeElement as HTMLElement;
      expect(el.querySelectorAll('input').length).toBe(1);
      field(f).value = '02';
      field(f).dispatchEvent(new Event('input'));
      await f.whenStable();
      expect(resources.query()).toBe('02');
      expect(el.querySelector('.searchnote')?.textContent).toContain('1 Treffer');
    });

    it('shows a query restored from storage', async () => {
      resources.setQuery('Hör');
      const f = await create([room(1)]);
      expect(field(f).value).toBe('Hör');
    });

    it('Enter steps the first shown row, Ctrl+Enter adds, ArrowDown focuses the list, Escape clears (PRD 123 D4)', async () => {
      resources.setActiveChip('resources');
      const f = await create([room(2), room(1)]);
      resources.setQuery('Raum');
      await f.whenStable();
      press(f, 'Enter');
      await f.whenStable();
      expect(filter.entries().map((e) => e.id)).toEqual(['r2']);
      resources.setQuery('01');
      await f.whenStable();
      press(f, 'Enter', { ctrlKey: true });
      await f.whenStable();
      expect(filter.entries().map((e) => e.id)).toEqual(['r2', 'r1']);
      press(f, 'ArrowDown');
      expect(document.activeElement).toBe(
        (f.nativeElement as HTMLElement).querySelector('.stepper'),
      );
      press(f, 'Escape');
      expect(resources.query()).toBe('');
    });
  });

  it("the stepper fills the 290px drawer's border-box content (289px) instead of a fixed width that adds a horizontal scrollbar", async () => {
    const f = await create([room(1)]);
    const stepper = (f.nativeElement as HTMLElement).querySelector<HTMLElement>('.stepper')!;
    expect(getComputedStyle(stepper).width).toBe('100%');
  });

  it('shows the section heads with aria-expanded and a sticky highlight on the one open section (PRD 127 D1)', async () => {
    resources.pushRecent({ id: 'R', label: 'Recent' });
    const f = await create([room(1), lecturer]);
    const el = f.nativeElement as HTMLElement;
    expect(headLabels(el)).toEqual(['Zuletzt', 'Ressourcen', 'Personen']);
    expect(heads(el).map((b) => b.getAttribute('aria-expanded'))).toEqual([
      'true',
      'false',
      'false',
    ]);
    expect(el.querySelectorAll('.sechead.open').length).toBe(1);
    await openSectionHead(f, 'Personen');
    expect(heads(el).map((b) => b.getAttribute('aria-expanded'))).toEqual([
      'false',
      'false',
      'true',
    ]);
    expect(resources.activeChip()).toBe('persons');
    expect(el.querySelector('.grouprow .glabel')?.textContent?.trim()).toBe('Dozent');
  });

  it('a type folder lists only resources of that type, in server order (PRD 127 D6)', async () => {
    resources.setActiveChip('resources');
    const f = await create([room(2), lecturer, room(1)]);
    const el = f.nativeElement as HTMLElement;
    await openFolder(f, 'Raum');
    expect(labels(el)).toEqual(['Raum 02', 'Raum 01']);
  });

  it('the shared query narrows the list without any request to the server', async () => {
    resources.setActiveChip('');
    const f = await create([room(1), room(2), room(12)]);
    await typeQuery(f, '1');
    expect(labels(f.nativeElement as HTMLElement)).toEqual(['Raum 01', 'Raum 12']);
    http.expectNone('/api/graphql');
    http.expectNone('/api/users');
  });

  it('matching users appear in the search only (also by username), last, and step as a user scope chip', async () => {
    resources.setActiveChip('');
    const f = await create([room(1)], [{ id: 'u1', username: 'monty', name: 'Burns Monty' }]);
    const el = f.nativeElement as HTMLElement;
    await openFolder(f);
    expect(labels(el)).toEqual(['Raum 01']);
    await typeQuery(f, 'mont');
    expect(labels(el)).toEqual(['Burns Monty']);
    (el.querySelector('.item') as HTMLElement).click();
    await f.whenStable();
    expect(filter.entries()).toEqual([
      { id: 'u1', kind: 'user', label: 'Burns Monty', color: undefined },
    ]);
  });

  describe('the Benutzer section (PRD 123 D9, PRD 127 D1)', () => {
    const ME: Identity = {
      userId: 'U-ME',
      username: 'admin',
      name: 'Admin X',
      admin: true,
      roles: [],
      impersonating: false,
      actor: null,
      target: null,
    };
    const USERS: UserWire[] = [
      { id: 'u2', username: 'monty', name: 'Burns Monty' },
      { id: 'U-ME', username: 'admin', name: 'Admin X' },
      { id: 'u3', username: 'homer', name: 'Simpson Homer' },
    ];
    it('comes last and lists every readable account, the own one first, the rest A–Z', async () => {
      TestBed.inject(AuthService).identity.set(ME);
      resources.setActiveChip('');
      const f = await create([room(1)], USERS);
      const el = f.nativeElement as HTMLElement;
      expect(headLabels(el)).toEqual(['Ressourcen', 'Benutzer']);
      await openSectionHead(f, 'Benutzer');
      expect(resources.activeChip()).toBe('users');
      expect(labels(el)).toEqual(['Admin X', 'Burns Monty', 'Simpson Homer']);
    });

    it('rows follow the selection gestures and become user scope chips', async () => {
      TestBed.inject(AuthService).identity.set(ME);
      resources.setActiveChip('users');
      const f = await create([room(1)], USERS);
      const rows = Array.from(
        (f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.item'),
      );
      rows[0].click();
      rows[2].dispatchEvent(new MouseEvent('click', { bubbles: true, ctrlKey: true }));
      expect(filter.entries().map((e) => [e.id, e.kind])).toEqual([
        ['U-ME', 'user'],
        ['u3', 'user'],
      ]);
    });

    it('the pinned card is gone', async () => {
      TestBed.inject(AuthService).identity.set(ME);
      const f = await create([room(1)], USERS);
      expect((f.nativeElement as HTMLElement).querySelector('.pinned')).toBeNull();
    });
  });

  describe('"alle wählen" in the search bar (PRD 123 D8, PRD 127 D8)', () => {
    const many = Array.from({ length: 25 }, (_, i) => room(i + 1));
    const bar = (el: HTMLElement) => el.querySelector<HTMLElement>('.searchnote');
    const all = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('.searchnote .listall')!;

    it('no count row and no button without a search; the heads carry the numbers', async () => {
      const f = await create(many);
      const el = f.nativeElement as HTMLElement;
      expect(el.querySelector('.listhdr, .listall, .collapse')).toBeNull();
      expect(el.querySelector('.sechead .count')?.textContent?.trim()).toBe('25');
    });

    it('takes every hit, also inside closed folders, and replaces the selection', async () => {
      filter.add({ id: 'OLD', kind: 'resource', label: 'old' });
      const f = await create(many);
      await typeQuery(f, 'raum');
      const el = f.nativeElement as HTMLElement;
      expect(bar(el)?.textContent).toContain('25 Treffer');
      all(el).click();
      await f.whenStable();
      expect(filter.entries().length).toBe(25);
      expect(filter.has('OLD')).toBe(false);
    });

    it('Ctrl adds; when every hit is selected the button reads "Auswahl aufheben" and clears the hits', async () => {
      filter.add({ id: 'OLD', kind: 'event', label: 'old' });
      const f = await create(many);
      await typeQuery(f, 'raum');
      const el = f.nativeElement as HTMLElement;
      expect(all(el).textContent?.trim()).toBe('alle wählen');
      all(el).dispatchEvent(new MouseEvent('click', { bubbles: true, ctrlKey: true }));
      await f.whenStable();
      f.detectChanges();
      expect(filter.entries().length).toBe(26);
      expect(all(el).textContent?.trim()).toBe('Auswahl aufheben');
      all(el).click();
      await f.whenStable();
      expect(filter.entries().map((e) => e.id)).toEqual(['OLD']);
    });

    it('resets the ▶ marker and pushes no recent', async () => {
      const f = await create(many);
      await typeQuery(f, 'raum');
      const el = f.nativeElement as HTMLElement;
      (el.querySelector('.item') as HTMLElement).click();
      const recents = resources.recents().length;
      all(el).click();
      await f.whenStable();
      expect(resources.activeId()).toBeNull();
      expect(resources.recents().length).toBe(recents);
    });

    it('selects matching accounts as user chips', async () => {
      const f = await create(
        [room(1)],
        [
          { id: 'u1', username: 'monty', name: 'Burns Monty' },
          { id: 'u2', username: 'homer', name: 'Simpson Homer' },
        ],
      );
      await typeQuery(f, 'simpson');
      all(f.nativeElement as HTMLElement).click();
      await f.whenStable();
      expect(filter.entries().map((e) => [e.id, e.kind])).toEqual([['u2', 'user']]);
    });
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

  it('a focus request from the search dropdown focuses the picker search field (PRD 119 D13)', async () => {
    const f = await create([room(1)]);
    resources.requestPickerFocus();
    await f.whenStable();
    expect(document.activeElement).toBe(
      (f.nativeElement as HTMLElement).querySelector('input.pksearch'),
    );
  });

  it('an activate-first request steps the first shown row of the picker — also in a type folder (PRD 123 M2)', async () => {
    resources.setActiveChip('resources');
    const f = await create([room(2), room(1)]);
    resources.setQuery('Raum');
    await f.whenStable();
    resources.requestActivateFirst(false);
    await f.whenStable();
    expect(filter.entries().map((e) => e.id)).toEqual(['r2']);
    expect(resources.activeId()).toBe('r2');
    expect(recentPosts()).toBe(1);
    resources.setQuery('01');
    await f.whenStable();
    resources.requestActivateFirst(true); // Ctrl = add
    await f.whenStable();
    expect(filter.entries().map((e) => e.id)).toEqual(['r2', 'r1']);
    resources.setQuery('');
    await f.whenStable();
    await openFolder(f);
    resources.requestActivateFirst(false);
    await f.whenStable();
    expect(filter.entries().map((e) => e.id)).toEqual(['r2']);
  });

  it('renders the items of the open section', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348 PC-Hörsaal' });
    resources.pushRecent({ id: 'C452', label: 'C452 Labor' });
    const f = await create();
    const items = labels(f.nativeElement as HTMLElement);
    expect(items.length).toBe(2);
    expect(items[0]).toContain('C452');
  });

  it('opening another section changes the rendered list', async () => {
    resources.pushRecent({ id: 'R', label: 'Recent' });
    const f = await create([room(1)]);
    const el = f.nativeElement as HTMLElement;
    expect(labels(el)).toEqual(['Recent']);
    await openSectionHead(f, 'Ressourcen');
    await openFolder(f);
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
      resources.setActiveChip('resources');
      const f = await create([room(1), room(2), room(3), room(4)]);
      await openFolder(f);
      return f;
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
      expect(chipIds()).toEqual(['r1', 'EXT']); // PRD 127 OQ6: tree order, unknown ids last
      click(items(f)[1]);
      expect(chipIds()).toEqual(['r2']);
    });

    describe('with only the own account, a "Meine Buchungen" row replaces the Benutzer section (PRD 127 OQ4)', () => {
      const ME: Identity = {
        userId: 'U-ME',
        username: 'admin',
        name: 'Admin X',
        admin: true,
        roles: [],
        impersonating: false,
        actor: null,
        target: null,
      };
      const mine = (f: { nativeElement: HTMLElement }) =>
        f.nativeElement.querySelector<HTMLElement>('.sechead.mine .secbtn')!;

      async function makeListWithMe() {
        TestBed.inject(AuthService).identity.set(ME);
        resources.setActiveChip('resources');
        const f = await create(
          [room(1), room(2), room(3), room(4)],
          [{ id: 'U-ME', username: 'admin', name: 'Admin X' }],
        );
        await openFolder(f);
        return f;
      }

      it('a click picks the own account, replacing the selection; the open section stays', async () => {
        const f = await makeListWithMe();
        const el = f.nativeElement as HTMLElement;
        expect(mine(f).querySelector('.glabel')?.textContent?.trim()).toBe('Meine Buchungen');
        expect(headLabels(el)).not.toContain('Benutzer');
        click(items(f)[0]);
        click(mine(f));
        await f.whenStable();
        f.detectChanges();
        expect(resources.activeChip()).toBe('resources');
        expect(mine(f).getAttribute('aria-pressed')).toBe('true');
        expect(filter.entries().map((e) => [e.id, e.kind])).toEqual([['U-ME', 'user']]);
      });

      it('Ctrl adds the own account to the selection', async () => {
        const f = await makeListWithMe();
        click(items(f)[0]);
        click(mine(f), { ctrlKey: true });
        expect(chipIds()).toEqual(['r1', 'U-ME']);
      });

      it('a second click deselects the own account', async () => {
        const f = await makeListWithMe();
        click(mine(f));
        await f.whenStable();
        f.detectChanges();
        expect(chipIds()).toEqual(['U-ME']);
        click(mine(f));
        await f.whenStable();
        f.detectChanges();
        expect(chipIds()).toEqual([]);
        expect(mine(f).getAttribute('aria-pressed')).toBe('false');
      });

      it('the pinned card is gone', async () => {
        const f = await makeListWithMe();
        expect(f.nativeElement.querySelector('.pinned')).toBeNull();
      });
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

  describe('group tree in a type folder (PRD 119 D2/D11, PRD 127 D5)', () => {
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
    async function inFolder(rows: ReturnType<typeof grouped>[] | Wire[]) {
      resources.setActiveChip('resources');
      const f = await create(rows as Wire[]);
      await openFolder(f);
      return f;
    }

    it('shows collapsed groups with member counts, the ungrouped resources after them', async () => {
      const f = await inFolder(fixture);
      expect(rows(f.nativeElement as HTMLElement)).toEqual([
        '▸ Raum (4)',
        '▸ Gebäude A (2)',
        '▸ Gebäude B (1)',
        'Aula',
      ]);
    });

    it('expanding a group shows its members', async () => {
      const f = await inFolder(fixture);
      const el = f.nativeElement as HTMLElement;
      const toggle = group(el, 'Gebäude A').querySelector<HTMLButtonElement>('.toggle')!;
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      toggle.click();
      await f.whenStable();
      expect(rows(el)).toEqual([
        '▸ Raum (4)',
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
      filter.add({ id: 'OLD', kind: 'resource', label: 'old' });
      const f = await inFolder(fixture);
      group(f.nativeElement as HTMLElement, 'Gebäude A')
        .querySelector<HTMLButtonElement>('.selectall')!
        .click();
      await f.whenStable();
      expect(filter.entries().map((e) => e.id)).toEqual(['r1', 'r2']);
    });

    it('the group button shares the D8 mechanics: Ctrl adds, a second click clears, no auto-expand', async () => {
      filter.add({ id: 'r4', kind: 'resource', label: 'Aula' });
      const f = await inFolder(fixture);
      const el = f.nativeElement as HTMLElement;
      const btn = () => group(el, 'Gebäude A').querySelector<HTMLButtonElement>('.selectall')!;
      btn().dispatchEvent(new MouseEvent('click', { bubbles: true, ctrlKey: true }));
      await f.whenStable();
      f.detectChanges();
      expect(filter.entries().map((e) => e.id)).toEqual(['r1', 'r2', 'r4']); // PRD 127 OQ6: tree order
      expect(rows(el)).toEqual(['▸ Raum (4)', '▸ Gebäude A (2)', '▸ Gebäude B (1)', 'Aula']);
      expect(btn().textContent?.trim()).toBe('Auswahl aufheben');
      btn().click();
      await f.whenStable();
      f.detectChanges();
      expect(filter.entries().map((e) => e.id)).toEqual(['r4']);
      expect(btn().textContent?.trim()).toBe('alle wählen');
    });

    it('D8 — the search bar button selects every hit', async () => {
      const f = await inFolder(fixture);
      const el = f.nativeElement as HTMLElement;
      await typeQuery(f, 'hörsaal');
      expect(el.querySelector('.searchnote')?.textContent).toContain('2 Treffer');
      el.querySelector<HTMLButtonElement>('.searchnote .listall')!.click();
      await f.whenStable();
      expect(
        filter
          .entries()
          .map((e) => e.id)
          .sort(),
      ).toEqual(['r1', 'r3']);
    });

    it('a search lists the hits flat under their type heading, parents below (PRD 127 D2)', async () => {
      const f = await inFolder(fixture);
      await typeQuery(f, 'hörsaal');
      const el = f.nativeElement as HTMLElement;
      expect(rows(el)).toEqual(['Hörsaal 1', 'Hörsaal 2']);
      expect(
        Array.from(el.querySelectorAll('.typehdr')).map((h) =>
          Array.from(h.querySelectorAll('span'), (t) => t.textContent?.trim()).join(' '),
        ),
      ).toEqual(['Raum 2']);
    });

    it('D12 — a type with more than 100 resources shows 100, then "Weitere 50 anzeigen" reveals the rest', async () => {
      const f = await inFolder(Array.from({ length: 150 }, (_, i) => room(i + 1)));
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
      const f = await inFolder(
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
      resources.setActiveChip('');
      const f = await create(Array.from({ length: 150 }, (_, i) => room(i + 1)));
      await typeQuery(f, 'raum');
      const el = f.nativeElement as HTMLElement;
      expect(labels(el).length).toBe(100);
      expect(el.querySelector('.showmore')?.textContent?.trim()).toBe('Weitere 50 anzeigen');
    });

    it('groups stay collapsed even on the path to a selected resource (PRD 127 D1, Q1)', async () => {
      filter.add({ id: 'r2', kind: 'resource', label: 'Labor' });
      const f = await inFolder(fixture);
      const el = f.nativeElement as HTMLElement;
      expect(rows(el)).toEqual(['▸ Raum (4)', '▸ Gebäude A (2)', '▸ Gebäude B (1)', 'Aula']);
      expect(group(el, 'Gebäude A').querySelector('.toggle')?.getAttribute('aria-expanded')).toBe(
        'false',
      );
    });

    it('click, Ctrl and Shift select over the resource rows of an expanded tree', async () => {
      const f = await inFolder(fixture);
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
        '▸ Raum (4)',
        '▸ Gebäude A (2)',
        'Hörsaal 1',
        'Labor',
        '▸ Gebäude B (1)',
        'Hörsaal 2',
        'Aula',
      ]);
    });

    it('shows every resource of a type without the "Weitere" page (the tree is collapsible)', async () => {
      const f = await inFolder(Array.from({ length: 25 }, (_, i) => room(i + 1)));
      const el = f.nativeElement as HTMLElement;
      expect(labels(el).length).toBe(25);
      expect(el.querySelector('.showmore')).toBeNull();
    });
  });
});
