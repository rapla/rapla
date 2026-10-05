import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { Subject, delay, of } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';

import { ViewHostComponent } from './view-host.component';
import { ROW_MENU_PROVIDERS, EventRowMenuProvider } from './row-menu';
import { GraphqlService, type GqlResponse, type ViewMeta } from '../graphql/graphql.service';
import { ViewStateStore } from '../state/view-state-store';
import { FilterStore } from '../state/filter-store';
import { EventDataService } from '../event/event-data.service';
import { UndoToastService } from '../actions/undo-toast.service';

// jsdom has no ResizeObserver (the week grid measures its viewport height with one)
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
  },
);

/**
 * PRD 074 § Sort × cursor — the server pages `appointmentBlocks` (default 1000 rows) and reports
 * `extensions.view.page { hasMore, endCursor }`. The table says so ("3+ Termine") and offers
 * "Weitere laden", which re-queries page 0's variables with `$after` = endCursor and APPENDS.
 * A header click on a server-sortable column binds `$sort` and reloads page 0.
 */

const META: ViewMeta = {
  key: 'Termine',
  title: 'Termine',
  rowLabel: 'Termin|Termine',
  renderModes: ['table', 'week'],
  variables: [
    { name: 'filter', type: 'ReservationFilter!' },
    { name: 'sort', type: '[BlockSort!]' },
    { name: 'after', type: 'String' },
  ],
  columns: [
    { alias: 'start', header: 'Von', type: 'LocalDateTime', order: 1 },
    { alias: 'name', header: 'Titel', type: 'String', order: 2 },
    { alias: 'persons', header: 'Personen', type: 'String', order: 3 },
  ],
};

let meta: ViewMeta;
let calls: Record<string, unknown>[];
/** Response delay in ms — 0 = synchronous (the loading flip never renders). */
let latency: number;

/** Page 0 = R0..R2 (cursor "c1"), page "c1" = R3..R5 (last page); names carry the window's day. */
function respond(variables: Record<string, unknown>) {
  const after = variables['after'] as string | undefined;
  const from = (variables['filter'] as { from?: string } | undefined)?.from ?? '';
  const day = from.slice(8, 10);
  const sort = variables['sort'] as { field: string; dir: string }[] | undefined;
  const base = after ? 3 : 0;
  const names =
    sort?.[0]?.field === 'NAME' ? ['Z', 'M', 'A'] : [0, 1, 2].map((i) => `R${base + i}`);
  const rows = names.map((name, i) => ({
    start: `2026-06-${day || '15'}T0${base + i}:00:00`,
    name: `${name}${day}`,
    persons: `P${2 - i}`,
  }));
  return {
    data: { appointmentBlocks: rows },
    extensions: {
      view: {
        ...meta,
        page: { limit: 3, returned: 3, hasMore: !after, endCursor: after ? 'c2' : 'c1' },
      },
    },
  };
}

const gql = {
  executeView: <T>(_name: string, variables: Record<string, unknown> = {}) => {
    calls.push(variables);
    const res = of(respond(variables) as unknown as GqlResponse<T>);
    return latency ? res.pipe(delay(latency)) : res;
  },
  mutate: vi.fn(() => of({ kind: 'ok', data: {} })),
};

const W1 = { from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' };
const W2 = { from: '2026-06-22T00:00:00', to: '2026-06-29T00:00:00' };

function mount(): ComponentFixture<ViewHostComponent> {
  const f = TestBed.createComponent(ViewHostComponent);
  f.componentRef.setInput('viewName', 'Termine');
  f.detectChanges();
  vi.advanceTimersByTime(300); // the signature re-query after the {} first load
  f.detectChanges();
  vi.advanceTimersByTime(300);
  f.detectChanges();
  return f;
}

function el(f: ComponentFixture<ViewHostComponent>): HTMLElement {
  return f.nativeElement as HTMLElement;
}

function clickLoadMore(f: ComponentFixture<ViewHostComponent>): void {
  el(f).querySelector<HTMLButtonElement>('button.load-more')!.click();
  vi.advanceTimersByTime(300);
  f.detectChanges();
}

function names(f: ComponentFixture<ViewHostComponent>): unknown[] {
  return f.componentInstance.rows().map((r) => r['name']);
}

function header(f: ComponentFixture<ViewHostComponent>, text: string): HTMLElement {
  return [...el(f).querySelectorAll<HTMLElement>('th')].find((th) =>
    th.textContent?.includes(text),
  )!;
}

function renderedNames(f: ComponentFixture<ViewHostComponent>): string[] {
  return [...el(f).querySelectorAll('tr.mat-mdc-row')].map(
    (tr) => tr.querySelectorAll('td')[1]?.textContent?.trim() ?? '',
  );
}

describe('ViewHostComponent — hasMore + cursor load more (PRD 074 item 5)', () => {
  beforeEach(async () => {
    calls = [];
    meta = META;
    latency = 0;
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [ViewHostComponent],
      providers: [
        { provide: GraphqlService, useValue: gql },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: EventDataService, useValue: { load: vi.fn(), save: vi.fn() } },
        { provide: UndoToastService, useValue: { run: vi.fn(), mutated$: new Subject<void>() } },
        { provide: ROW_MENU_PROVIDERS, useClass: EventRowMenuProvider, multi: true },
      ],
    }).compileComponents();
    const filter = TestBed.inject(FilterStore);
    filter.clear();
    filter.replace({ id: 'scope-1', kind: 'resource', label: 'Scope' });
    const state = TestBed.inject(ViewStateStore);
    state.setRenderMode('table');
    state.setWindow(W1);
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('marks a truncated result: "3+ Termine" and a "Weitere laden" button', () => {
    const f = mount();
    expect(TestBed.inject(ViewStateStore).resultInfo()).toBe('3+ Termine');
    expect(el(f).querySelector('button.load-more')?.textContent?.trim()).toBe('Weitere laden');
  });

  it('"Weitere laden" re-queries page 0 with $after = endCursor and appends', () => {
    const f = mount();
    const page0 = calls[calls.length - 1];
    clickLoadMore(f);
    const next = calls[calls.length - 1];
    expect(next['after']).toBe('c1');
    expect(next).not.toHaveProperty('offset');
    expect({ ...next, after: undefined }).toEqual({ ...page0, after: undefined });
    expect(names(f)).toEqual(['R015', 'R115', 'R215', 'R315', 'R415', 'R515']);
    expect(TestBed.inject(ViewStateStore).resultInfo()).toBe('6 Termine');
    expect(el(f).querySelector('button.load-more')).toBeNull();
  });

  it('a window change resets to page 0 (no stale cursor)', () => {
    const f = mount();
    clickLoadMore(f);
    TestBed.inject(ViewStateStore).setWindow(W2);
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(calls[calls.length - 1]).not.toHaveProperty('after');
    expect(names(f)).toEqual(['R022', 'R122', 'R222']);
  });

  it('no button when the view declares no $after (page 0 would be appended again)', () => {
    meta = { ...META, variables: [{ name: 'filter', type: 'ReservationFilter!' }] };
    const f = mount();
    expect(TestBed.inject(ViewStateStore).resultInfo()).toBe('3+ Termine');
    expect(el(f).querySelector('button.load-more')).toBeNull();
  });

  it('"Weitere laden" inside the throttle after a window change does not append to the old rows', () => {
    const f = mount();
    const state = TestBed.inject(ViewStateStore);
    state.setWindow(W2);
    f.detectChanges(); // leading: page 0 of W2 goes out, the throttle window opens
    state.setWindow(W1);
    f.detectChanges(); // trailing pending: page 0 of W1
    f.componentInstance.loadMore(); // still the W2 rows + cursor on screen
    vi.advanceTimersByTime(300);
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(calls[calls.length - 1]).not.toHaveProperty('after');
    expect(names(f)).toEqual(['R015', 'R115', 'R215']);
  });

  it('offers "Weitere laden" in the week grid too and appends there', () => {
    const f = mount();
    TestBed.inject(ViewStateStore).setRenderMode('week');
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(el(f).querySelector('app-week-grid')).not.toBeNull();
    clickLoadMore(f);
    expect(calls[calls.length - 1]['after']).toBe('c1');
    expect(f.componentInstance.rows().length).toBe(6);
  });

  it('a click on "Titel" sorts server-side: $sort NAME, page 0, server order kept', () => {
    const f = mount();
    header(f, 'Titel').click();
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();
    const last = calls[calls.length - 1];
    expect(last['sort']).toEqual([{ field: 'NAME', dir: 'ASC' }]);
    expect(last).not.toHaveProperty('after');
    expect(renderedNames(f)).toEqual(['Z15', 'M15', 'A15']);
    clickLoadMore(f);
    expect(calls[calls.length - 1]).toMatchObject({
      sort: [{ field: 'NAME', dir: 'ASC' }],
      after: 'c1',
    });
  });

  it('a column the server cannot sort stays client-sorted and says so in the header', () => {
    const f = mount();
    const persons = header(f, 'Personen');
    expect(persons.getAttribute('title')).toBe('sortiert nur geladene Einträge');
    expect(header(f, 'Titel').getAttribute('title')).toBeNull();
    const before = calls.length;
    persons.click();
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(calls.length).toBe(before);
    expect(renderedNames(f)).toEqual(['R215', 'R115', 'R015']);
  });

  it('a second "Titel" click sorts DESC and the header shows the server sort', () => {
    latency = 10;
    const f = mount();
    for (const dir of ['ASC', 'DESC']) {
      header(f, 'Titel').click();
      f.detectChanges();
      vi.advanceTimersByTime(300);
      f.detectChanges();
      expect(calls[calls.length - 1]['sort']).toEqual([{ field: 'NAME', dir }]);
      expect(header(f, 'Titel').getAttribute('aria-sort')).toBe(
        dir === 'ASC' ? 'ascending' : 'descending',
      );
    }
  });

  it('rows stay rendered while a page is appended (no scroll jump); button disabled in flight', () => {
    latency = 10;
    const f = mount();
    el(f).querySelector<HTMLButtonElement>('button.load-more')!.click();
    f.detectChanges();
    expect(el(f).querySelectorAll('tr.mat-mdc-row').length).toBe(3);
    expect(el(f).querySelector<HTMLButtonElement>('button.load-more')!.disabled).toBe(true);
    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(el(f).querySelectorAll('tr.mat-mdc-row').length).toBe(6);
  });

  it('a grouped view never sends the table header sort', () => {
    meta = { ...META, groupBy: 'start', renderModes: ['table', 'grouped'] };
    const f = mount();
    header(f, 'Titel').click();
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(calls[calls.length - 1]['sort']).toEqual([{ field: 'NAME', dir: 'ASC' }]);
    TestBed.inject(ViewStateStore).setRenderMode('grouped');
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(calls[calls.length - 1]).not.toHaveProperty('sort');
  });
});
