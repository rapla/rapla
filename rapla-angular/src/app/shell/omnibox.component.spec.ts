import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { OmniboxComponent } from './omnibox.component';
import { FilterStore } from '../state/filter-store';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { SearchService } from '../search/search.service';
import type { SearchResult, SearchResultGroup, SearchResultKind } from '../search/search.types';

// Canned corpus — exercises ALL result kinds the omnibox renders (the real
// SearchService is resources-only today; this keeps the component's group/event
// handling under test independently of the service).
const CORPUS: SearchResult[] = [
  { id: 'res-1', kind: 'resource', label: 'Raum A-101', actions: ['filter-replace', 'filter-add'] },
  { id: 'grp-1', kind: 'group', label: 'Räume C-Bau', count: 12, actions: ['load-group'] },
  { id: 'evt-1', kind: 'event', label: 'Mathematik I', actions: ['navigate', 'filter-add', 'edit'] },
  {
    id: 'usr-1',
    kind: 'user',
    label: 'Burns Monty',
    sublabel: 'monty',
    actions: ['filter-replace', 'filter-add'],
  },
];
const HEADINGS: Record<SearchResultKind, string> = {
  resource: 'Ressourcen',
  event: 'Veranstaltungen',
  user: 'Benutzer',
  occurrence: 'Termine',
  group: 'Gruppen',
  savedView: 'Ansichten',
};
const fakeSearch = {
  search: (term: string) => {
    const q = term.trim().toLowerCase();
    const hits = q ? CORPUS.filter((r) => r.label.toLowerCase().includes(q)) : [];
    const kinds = [...new Set(hits.map((r) => r.kind))];
    const groups: SearchResultGroup[] = kinds.map((kind) => ({
      kind,
      heading: HEADINGS[kind],
      results: hits.filter((r) => r.kind === kind),
    }));
    return of(groups);
  },
};

function setTerm(f: ComponentFixture<OmniboxComponent>, term: string): void {
  // Drive via onType (sets the term AND opens the dropdown), mirroring a real
  // keystroke without ngModel's zoneless input-event flakiness.
  (f.componentInstance as unknown as { onType(v: string): void }).onType(term);
}

/**
 * Flush the async term → toObservable → switchMap → toSignal chain. The
 * term-change emission needs a macrotask turn AFTER stability before the
 * filtered groups land in the DOM; a single whenStable() can render the prior
 * (empty-term) emission. Two rounds + a macrotask make it deterministic.
 */
async function settle(f: ComponentFixture<OmniboxComponent>): Promise<void> {
  await f.whenStable();
  await new Promise((r) => setTimeout(r, 0));
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
}

function buttonFor(el: HTMLElement, rowIndex: number, action: string): HTMLButtonElement {
  const rows = el.querySelectorAll('.row');
  return rows[rowIndex].querySelector(`button[data-action="${action}"]`) as HTMLButtonElement;
}

describe('OmniboxComponent', () => {
  let filter: FilterStore;
  let resources: ResourceSelectionStore;

  beforeEach(async () => {
    localStorage.clear(); // ResourceSelection recents persist — isolate each test
    await TestBed.configureTestingModule({
      imports: [OmniboxComponent],
      providers: [
        { provide: SearchService, useValue: fakeSearch },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    filter = TestBed.inject(FilterStore);
    resources = TestBed.inject(ResourceSelectionStore);
    filter.clear();
    resources.clearGroup();
  });

  it('typing a term renders result rows', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'mathe');
    await settle(f);
    const rows = (f.nativeElement as HTMLElement).querySelectorAll('.row');
    expect(rows.length).toBeGreaterThan(0);
  });

  it('clicking "Belegung" on a resource row replaces the filter', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Raum A-101');
    await settle(f);
    const el = f.nativeElement as HTMLElement;
    buttonFor(el, 0, 'filter-replace').click();
    await f.whenStable();
    expect(filter.entries().length).toBe(1);
    expect(filter.entries()[0]).toMatchObject({ id: 'res-1', kind: 'resource' });
  });

  it('clicking "+" adds to the filter without clearing', async () => {
    filter.add({ id: 'pre', kind: 'resource', label: 'Pre' });
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Raum A-101');
    await settle(f);
    buttonFor(f.nativeElement as HTMLElement, 0, 'filter-add').click();
    await f.whenStable();
    expect(filter.has('pre')).toBe(true);
    expect(filter.has('res-1')).toBe(true);
  });

  it('clicking "+" on a user row adds a user-kind scope chip (not a resource chip)', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Burns Monty');
    await settle(f);
    buttonFor(f.nativeElement as HTMLElement, 0, 'filter-add').click();
    await f.whenStable();
    const chip = filter.entries().find((e) => e.id === 'usr-1');
    expect(chip).toBeDefined();
    expect(chip?.kind).toBe('user');
  });

  it('closes the dropdown on Escape', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Raum A-101');
    await settle(f);
    expect((f.nativeElement as HTMLElement).querySelector('.results')).not.toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await f.whenStable();
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('.results')).toBeNull();
  });

  it('closes the dropdown on an outside click', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Raum A-101');
    await settle(f);
    expect((f.nativeElement as HTMLElement).querySelector('.results')).not.toBeNull();
    document.body.click();
    await f.whenStable();
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('.results')).toBeNull();
  });

  it('closes the dropdown after a terminal action (Belegung)', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Raum A-101');
    await settle(f);
    buttonFor(f.nativeElement as HTMLElement, 0, 'filter-replace').click();
    await f.whenStable();
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('.results')).toBeNull();
  });

  it('remembers a found resource in Recents when acted on', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Raum A-101');
    await settle(f);
    buttonFor(f.nativeElement as HTMLElement, 0, 'filter-replace').click();
    await f.whenStable();
    expect(resources.recents().map((x) => x.id)).toEqual(['res-1']);
  });

  it('a found user is pulled into Recents as a user item (like a resource)', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Burns Monty');
    await settle(f);
    buttonFor(f.nativeElement as HTMLElement, 0, 'filter-replace').click();
    await f.whenStable();
    const recent = resources.recents().find((x) => x.id === 'usr-1');
    expect(recent).toBeDefined();
    expect(recent?.kind).toBe('user');
  });

  it('clicking "in Liste laden" on a group populates the resource selection group', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'C-Bau');
    await settle(f);
    // Only the group result matches 'C-Bau' → it is the sole row.
    buttonFor(f.nativeElement as HTMLElement, 0, 'load-group').click();
    await f.whenStable();
    expect(resources.group().length).toBe(1);
    expect(resources.group()[0].id).toBe('grp-1');
    expect(resources.groupLabel()).toBe('Räume C-Bau');
  });
});
