import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ResourceSelectionComponent } from './resource-selection.component';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { FilterStore } from '../state/filter-store';

function setInput(el: HTMLElement, selector: string, value: string): void {
  const input = el.querySelector(selector) as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('ResourceSelectionComponent', () => {
  let resources: ResourceSelectionStore;
  let filter: FilterStore;

  beforeEach(async () => {
    localStorage.clear(); // recents/favorites persist — isolate before the store hydrates
    await TestBed.configureTestingModule({
      imports: [ResourceSelectionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
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
});
