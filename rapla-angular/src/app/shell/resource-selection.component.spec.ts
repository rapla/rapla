import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ResourceSelectionComponent } from './resource-selection.component';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { FilterStore } from '../state/filter-store';
import { AllocatableEditDialogComponent } from '../allocatable/allocatable-edit-dialog.component';

function setInput(el: HTMLElement, selector: string, value: string): void {
  const input = el.querySelector(selector) as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('ResourceSelectionComponent', () => {
  let resources: ResourceSelectionStore;
  let filter: FilterStore;
  const dialogOpen = vi.fn();

  beforeEach(async () => {
    localStorage.clear(); // recents/favorites persist — isolate before the store hydrates
    dialogOpen.mockClear();
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
    resources.clearGroup();
    resources.setActiveTab('recents');
    resources.setActive(null);
    filter.clear();
  });

  it('renders the items of the active list', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348 PC-Hörsaal' });
    resources.pushRecent({ id: 'C452', label: 'C452 Labor' });
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    const items = Array.from((f.nativeElement as HTMLElement).querySelectorAll('.item')).map(
      (i) => i.textContent?.trim() ?? '',
    );
    expect(items.length).toBe(2);
    expect(items[0]).toContain('C452');
  });

  it('switching tab changes the rendered list', async () => {
    resources.pushRecent({ id: 'R', label: 'Recent' });
    resources.loadGroup('Räume C-Bau', [{ id: 'G', label: 'GroupItem' }]);
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    let items = (f.nativeElement as HTMLElement).querySelectorAll('.item');
    expect(items[0].textContent).toContain('GroupItem');

    resources.setActiveTab('recents');
    await f.whenStable();
    items = (f.nativeElement as HTMLElement).querySelectorAll('.item');
    expect(items[0].textContent).toContain('Recent');
  });

  it('typing in the list filter narrows the visible items', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348 PC-Hörsaal' });
    resources.pushRecent({ id: 'A474', label: 'A474 Hörsaal' });
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    setInput(f.nativeElement as HTMLElement, '.stsearch', 'c348');
    await f.whenStable();
    const items = (f.nativeElement as HTMLElement).querySelectorAll('.item');
    expect(items.length).toBe(1);
    expect(items[0].textContent).toContain('C348');
  });

  it('clicking an item makes it active and replaces the filter (stepping)', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348 PC-Hörsaal' });
    resources.pushRecent({ id: 'C452', label: 'C452 Labor' });
    filter.add({ id: 'OLD', kind: 'resource', label: 'old' });
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    const first = (f.nativeElement as HTMLElement).querySelector('.item') as HTMLElement;
    first.click();
    await f.whenStable();
    expect(resources.activeId()).toBe('C452');
    expect(filter.entries().map((e) => e.id)).toEqual(['C452']);
  });

  it('stepping a user item creates a user-kind scope chip (not a resource filter)', async () => {
    resources.pushRecent({ id: 'u1', label: 'Burns Monty', kind: 'user' });
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    const first = (f.nativeElement as HTMLElement).querySelector('.item') as HTMLElement;
    first.click();
    await f.whenStable();
    expect(filter.entries()).toEqual([{ id: 'u1', kind: 'user', label: 'Burns Monty', color: undefined }]);
  });

  it('toggling the star pins to Favoriten WITHOUT stepping the row', async () => {
    resources.pushRecent({ id: 'C348', label: 'C348' });
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    (f.nativeElement as HTMLElement).querySelector('.fav')!.dispatchEvent(
      new MouseEvent('click', { bubbles: true }),
    );
    await f.whenStable();
    expect(resources.isFavorite('C348')).toBe(true);
    expect(resources.activeId()).toBeNull(); // star did not step
    expect(filter.isEmpty()).toBe(true);
  });

  it('stepping does NOT add to Recents (no reshuffle while stepping)', async () => {
    resources.loadGroup('Räume C-Bau', [{ id: 'C348', label: 'C348' }]);
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    (f.nativeElement as HTMLElement).querySelector('.item')!.dispatchEvent(new Event('click'));
    await f.whenStable();
    expect(resources.recents()).toEqual([]);
    expect(resources.activeId()).toBe('C348'); // it IS shown, just not re-ordered into recents
  });

  it('group tab shows the label and a clear control that empties the group', async () => {
    resources.loadGroup('Räume C-Bau', [{ id: 'C348', label: 'C348' }]);
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('.grouphdr')?.textContent).toContain('Räume C-Bau');
    (el.querySelector('.clr') as HTMLElement).click();
    await f.whenStable();
    expect(resources.group()).toEqual([]);
  });

  describe('Swing-tree selection semantics (PRD 099 Phase 4)', () => {
    async function makeList() {
      resources.loadGroup('G', [
        { id: 'r1', label: 'Raum 1' },
        { id: 'r2', label: 'Raum 2' },
        { id: 'r3', label: 'Raum 3' },
        { id: 'r4', label: 'Raum 4' },
      ]);
      const f = TestBed.createComponent(ResourceSelectionComponent);
      await f.whenStable();
      return f;
    }
    const items = (f: { nativeElement: HTMLElement }) =>
      Array.from(f.nativeElement.querySelectorAll<HTMLElement>('.item'));
    const click = (el: HTMLElement, init: MouseEventInit = {}) =>
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, ...init }));
    const keydown = (f: { nativeElement: HTMLElement }, key: string, init: KeyboardEventInit = {}) =>
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

    it('keys typed into the search input do not step the list', async () => {
      const f = await makeList();
      click(items(f)[0]);
      const input = f.nativeElement.querySelector('.stsearch') as HTMLInputElement;
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
      expect(chipIds()).toEqual(['r1']); // unchanged
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
        expect.stringContaining('Raum 1'),
        expect.stringContaining('Raum 3'),
      ]);
    });
  });

  it('⋮ menu appears on resource items only; Bearbeiten/Anzeigen open the dialog (PRD 096)', async () => {
    resources.pushRecent({ id: 'cam1', label: 'Canon G25 01' });
    resources.pushRecent({ id: 'u1', label: 'Burns Monty', kind: 'user' });
    const f = TestBed.createComponent(ResourceSelectionComponent);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.act').length).toBe(1); // user rows get no editor menu

    (el.querySelector('.act') as HTMLButtonElement).click();
    await f.whenStable();
    const menuItems = Array.from(document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-item'));
    expect(menuItems.map((m) => m.textContent?.trim())).toEqual(['Bearbeiten', 'Anzeigen']);

    menuItems[0].click();
    await f.whenStable();
    expect(dialogOpen).toHaveBeenCalledWith(
      AllocatableEditDialogComponent,
      expect.objectContaining({ data: { id: 'cam1', readOnly: false } }),
    );
    // the ⋮ click must not step/replace the filter
    expect(filter.isEmpty()).toBe(true);
    expect(resources.activeId()).toBeNull();
  });
});
