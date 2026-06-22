import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { of, Subject } from 'rxjs';

import { ViewHostComponent } from './view-host.component';
import { GraphqlService, type GqlResponse, type ViewMeta } from '../graphql/graphql.service';
import { ViewStateStore } from '../state/view-state-store';
import { FilterStore } from '../state/filter-store';

// A representative week-view render-meta (date columns → weekday grouping) with
// the variable signature the server emits (drives type-driven binding).
const SAMPLE_VIEW_META: ViewMeta = {
  key: 'Wochenansicht',
  title: 'Wochenansicht',
  variables: [{ name: 'filter', type: 'ReservationFilter!' }],
  columns: [
    { alias: 'start', header: 'Von', type: 'LocalDateTime', order: 1 },
    { alias: 'end', header: 'Bis', type: 'LocalDateTime', order: 2 },
    { alias: 'times', header: 'Zeit', type: 'String', order: 3 },
    { alias: 'name', header: 'Titel', type: 'String', order: 4 },
    { alias: 'personen', header: 'Personen', type: 'Allocatable', order: 5, join: ', ' },
    { alias: 'nichtPersonen', header: 'Nicht-Personen', type: 'Allocatable', order: 6, join: ', ' },
  ],
};

const BLOCKS: Record<string, unknown>[] = [
  { start: '2026-06-17T10:00:00', end: '2026-06-17T11:30:00', times: '10:00 - 11:30', name: 'Physik', personen: [], nichtPersonen: [{ id: 'r1', name: 'C452' }] },
  { start: '2026-06-15T08:00:00', end: '2026-06-15T09:30:00', times: '08:00 - 09:30', name: 'Mathe', personen: [], nichtPersonen: [{ id: 'r2', name: 'C348' }] },
];

// Capturing stub — records the view name + variables of the most recent executeView.
const captured: { view: string | null; vars: Record<string, unknown> | null } = {
  view: null,
  vars: null,
};
const GQL_STUB = {
  executeView: <T>(
    viewName: string,
    vars: Record<string, unknown>,
  ): ReturnType<GraphqlService['executeView']> => {
    captured.view = viewName;
    captured.vars = vars;
    return of({
      data: { appointmentBlocks: BLOCKS } as unknown as T,
      extensions: { view: SAMPLE_VIEW_META },
    } as GqlResponse<T>);
  },
};

async function settle(f: ComponentFixture<ViewHostComponent>): Promise<void> {
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
}

function makeHost(viewName: string): ComponentFixture<ViewHostComponent> {
  const f = TestBed.createComponent(ViewHostComponent);
  f.componentRef.setInput('viewName', viewName);
  return f;
}

describe('ViewHostComponent', () => {
  let viewState: ViewStateStore;
  let filter: FilterStore;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewHostComponent],
      providers: [{ provide: GraphqlService, useValue: GQL_STUB }],
    }).compileComponents();
    viewState = TestBed.inject(ViewStateStore);
    filter = TestBed.inject(FilterStore);
    filter.clear();
    captured.view = null;
    captured.vars = null;
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
  });

  it('renders the weekday view as ordered day sections', async () => {
    const f = makeHost('Wochenansicht');
    await settle(f);
    const headers = Array.from((f.nativeElement as HTMLElement).querySelectorAll('.day h3')).map(
      (h) => h.textContent ?? '',
    );
    expect(headers.length).toBe(2);
    expect(headers[0]).toContain('Montag');
    expect(headers[1]).toContain('Mittwoch');
  });

  it('renders a flat view (table render mode) with no day headers', async () => {
    const f = makeHost('rapla_appointments');
    await settle(f);
    viewState.setRenderMode('table'); // user switches to flat
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.day h3').length).toBe(0);
    expect((el.textContent ?? '')).toContain('Mathe');
    expect((el.textContent ?? '')).toContain('Physik');
  });

  it('executes the STORED view by name (consumer path)', async () => {
    const f = makeHost('Wochenansicht');
    await settle(f);
    expect(captured.view).toBe('Wochenansicht');
  });

  it('binds a resource chip into the ReservationFilter by type (allocatableMatching.idIn)', async () => {
    filter.replace({ id: 'C348', kind: 'resource', label: 'C348' });
    const f = makeHost('Wochenansicht');
    await settle(f);
    expect(captured.vars?.['filter']).toMatchObject({
      from: '2026-06-15T00:00:00',
      to: '2026-06-22T00:00:00',
      allocatableMatching: { idIn: ['C348'] },
    });
  });

  it('render mode drives grouping: Tabelle flattens the week view live', async () => {
    const f = makeHost('Wochenansicht');
    await settle(f);
    expect((f.nativeElement as HTMLElement).querySelectorAll('.day h3').length).toBe(2); // week → sections
    viewState.setRenderMode('table');
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelectorAll('.day h3').length).toBe(0); // table → flat
  });

  it('does not render hidden columns (reservation/duration) as headers', async () => {
    const f = makeHost('Wochenansicht');
    await settle(f);
    const ths = Array.from((f.nativeElement as HTMLElement).querySelectorAll('thead th')).map(
      (t) => t.textContent?.trim() ?? '',
    );
    expect(ths).not.toContain('reservation');
    expect(ths).not.toContain('durationMinutes');
    expect(ths).toContain('Titel');
  });
});

describe('ViewHostComponent — stale response handling', () => {
  let viewState: ViewStateStore;
  let calls: Subject<unknown>[];

  const resp = (n: number): GqlResponse<unknown> => ({
    data: { appointmentBlocks: Array.from({ length: n }, () => ({ date: '2026-06-15' })) },
    extensions: { view: { key: 'k', columns: [] } },
  });

  beforeEach(async () => {
    calls = [];
    const stub = {
      executeView: () => {
        const s = new Subject<unknown>();
        calls.push(s);
        return s.asObservable();
      },
    };
    await TestBed.configureTestingModule({
      imports: [ViewHostComponent],
      providers: [{ provide: GraphqlService, useValue: stub }],
    }).compileComponents();
    viewState = TestBed.inject(ViewStateStore);
    TestBed.inject(FilterStore).clear();
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
  });

  it('ignores an older response that lands after a newer one (no firehose clobber)', async () => {
    const f = TestBed.createComponent(ViewHostComponent);
    f.componentRef.setInput('viewName', 'Wochenansicht');
    f.detectChanges(); // query #1 → calls[0]
    await f.whenStable();
    viewState.setWindow({ from: '2026-06-22T00:00:00', to: '2026-06-29T00:00:00' });
    f.detectChanges(); // query #2 → calls[1]
    await f.whenStable();
    expect(calls.length).toBeGreaterThanOrEqual(2);

    // newer query (#2) resolves first with 1 row
    calls[calls.length - 1].next(resp(1));
    f.detectChanges();
    expect(f.componentInstance.total()).toBe(1);

    // older query (#1) resolves LATER with a 500-row firehose → must be ignored
    calls[0].next(resp(500));
    f.detectChanges();
    expect(f.componentInstance.total()).toBe(1);
  });
});
