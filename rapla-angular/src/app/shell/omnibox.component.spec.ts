import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';

import { OmniboxComponent } from './omnibox.component';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { ViewStateStore } from '../state/view-state-store';
import { SearchService } from '../search/search.service';
import { EventSheetComponent } from '../event/event-sheet.component';
import type { SearchResult, SearchResultGroup } from '../search/search.types';

/** PRD 119 D4 — the dropdown lists events only; resources and users are rows of the picker. */
const EVENTS: SearchResult[] = [
  { id: 'evt-1', kind: 'event', label: 'Mathematik I', start: '2026-10-07T10:00:00' },
  { id: 'evt-2', kind: 'event', label: 'Mathematik II', start: '2026-10-14T08:00:00' },
];

const fakeSearch = {
  search: (term: string) => {
    const q = term.trim().toLowerCase();
    const hits = q ? EVENTS.filter((r) => r.label.toLowerCase().includes(q)) : [];
    const groups: SearchResultGroup[] = hits.length
      ? [{ kind: 'event', heading: 'Veranstaltungen', results: hits }]
      : [];
    return of(groups);
  },
};

const room = (id: string, name: string) => ({
  id,
  kind: 'RESOURCE',
  name,
  classification: { typeKey: 'room', type: { name: 'Raum' } },
});

function setTerm(f: ComponentFixture<OmniboxComponent>, term: string): void {
  (f.componentInstance as unknown as { onType(v: string): void }).onType(term);
}

/** Flush the async term → toObservable → throttle/switchMap → toSignal chain. */
async function settle(f: ComponentFixture<OmniboxComponent>): Promise<void> {
  await f.whenStable();
  await new Promise((r) => setTimeout(r, 350)); // past the search throttle's trailing edge
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
}

describe('OmniboxComponent', () => {
  let resources: ResourceSelectionStore;
  let viewState: ViewStateStore;
  let http: HttpTestingController;
  const dialogOpen = vi.fn();

  beforeEach(async () => {
    localStorage.clear();
    dialogOpen.mockReset();
    await TestBed.configureTestingModule({
      imports: [OmniboxComponent],
      providers: [
        { provide: SearchService, useValue: fakeSearch },
        { provide: MatDialog, useValue: { open: dialogOpen } },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    resources = TestBed.inject(ResourceSelectionStore);
    viewState = TestBed.inject(ViewStateStore);
    http = TestBed.inject(HttpTestingController);
    resources.setQuery('');
  });

  function loadPicker(rows: ReturnType<typeof room>[]): void {
    resources.ensureLoaded();
    http
      .expectOne((r) => r.url === '/api/graphql' && String(r.body?.query).includes('resources'))
      .flush({ data: { resources: rows, users: [] } });
  }

  it('typing writes the shared picker query from the first character, without a dropdown', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'm');
    await settle(f);
    expect(resources.query()).toBe('m');
    expect((f.nativeElement as HTMLElement).querySelector('.results')).toBeNull();
  });

  it('from three characters the dropdown shows the resource count row and event hits only', async () => {
    loadPicker([room('r1', 'Mathe-Labor'), room('r2', 'Mathe-Raum'), room('r3', 'Chemie')]);
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Mat');
    await settle(f);
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('.countrow')?.textContent).toContain(
      '2 Ressourcen und Gruppen in der Liste links',
    );
    const rows = Array.from(el.querySelectorAll('.row')).map((r) => r.textContent ?? '');
    expect(rows).toEqual([
      expect.stringContaining('Mathematik I'),
      expect.stringContaining('Mathematik II'),
    ]);
    expect(el.querySelectorAll('button[data-action]').length).toBe(0);
    // real buttons: Enter AND Space activate them (P2 review S2)
    expect(el.querySelector('.countrow')?.tagName).toBe('BUTTON');
    expect(Array.from(el.querySelectorAll('.row')).every((r) => r.tagName === 'BUTTON')).toBe(true);
  });

  it('clicking an event hit jumps to its week (span kept) and opens its sheet with the search hint', async () => {
    viewState.setWindow({ from: '2026-06-01T00:00:00', to: '2026-06-08T00:00:00' });
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Mathematik I');
    await settle(f);
    const el = f.nativeElement as HTMLElement;
    (el.querySelector('.row') as HTMLElement).click();
    await settle(f);
    expect(viewState.window()).toEqual({ from: '2026-10-05T00:00:00', to: '2026-10-12T00:00:00' });
    expect(dialogOpen).toHaveBeenCalledWith(
      EventSheetComponent,
      expect.objectContaining({ data: { id: 'evt-1', searchHint: true } }),
    );
    expect(el.querySelector('.results')).toBeNull();
  });

  it('clicking the count row asks the picker for focus and closes the dropdown', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Mathe');
    await settle(f);
    const before = resources.pickerFocus();
    (f.nativeElement as HTMLElement).querySelector<HTMLElement>('.countrow')!.click();
    await settle(f);
    expect(resources.pickerFocus()).toBe(before + 1);
    expect((f.nativeElement as HTMLElement).querySelector('.results')).toBeNull();
  });

  it('closes the dropdown on Escape', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Mathe');
    await settle(f);
    expect((f.nativeElement as HTMLElement).querySelector('.results')).not.toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await settle(f);
    expect((f.nativeElement as HTMLElement).querySelector('.results')).toBeNull();
  });

  it('closes the dropdown on an outside click', async () => {
    const f = TestBed.createComponent(OmniboxComponent);
    setTerm(f, 'Mathe');
    await settle(f);
    expect((f.nativeElement as HTMLElement).querySelector('.results')).not.toBeNull();
    document.body.click();
    await settle(f);
    expect((f.nativeElement as HTMLElement).querySelector('.results')).toBeNull();
  });
});
