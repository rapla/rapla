import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Subject, of } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';

import { ViewHostComponent } from './view-host.component';
import { ROW_MENU_PROVIDERS, EventRowMenuProvider } from './row-menu';
import { GraphqlService, type GqlResponse, type ViewMeta } from '../graphql/graphql.service';
import { ViewStateStore } from '../state/view-state-store';
import { FilterStore } from '../state/filter-store';
import { EventDataService } from '../event/event-data.service';
import { UndoToastService } from '../actions/undo-toast.service';
import { ViewControlStripComponent } from '../shell/view-control-strip.component';

const META: ViewMeta = {
  key: 'Termine',
  title: 'Termine',
  rowLabel: 'Termin|Termine',
  variables: [{ name: 'filter', type: 'ReservationFilter!' }],
  columns: [
    { alias: 'start', header: 'Von', type: 'LocalDateTime', order: 1 },
    { alias: 'name', header: 'Titel', type: 'String', order: 2 },
  ],
};

const ROWS: Record<string, unknown>[] = [
  { start: '2026-06-15T08:00:00', name: 'A' },
  { start: '2026-06-16T08:00:00', name: 'B' },
  { start: '2026-06-17T08:00:00', name: 'C' },
];

const GQL_STUB = {
  executeView: <T>(): ReturnType<GraphqlService['executeView']> =>
    of({
      data: { appointmentBlocks: ROWS } as unknown as T,
      extensions: { view: META },
    } as GqlResponse<T>),
  mutate: vi.fn(() => of({ kind: 'ok', data: {} })),
};

async function makeHost(): Promise<ComponentFixture<ViewHostComponent>> {
  const f = TestBed.createComponent(ViewHostComponent);
  f.componentRef.setInput('viewName', 'Termine');
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
  return f;
}

describe('ViewHostComponent — result info placement (screen vs print)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewHostComponent],
      providers: [
        { provide: GraphqlService, useValue: GQL_STUB },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: EventDataService, useValue: { load: vi.fn(), save: vi.fn() } },
        { provide: UndoToastService, useValue: { run: vi.fn(), mutated$: new Subject<void>() } },
        { provide: ROW_MENU_PROVIDERS, useClass: EventRowMenuProvider, multi: true },
      ],
    }).compileComponents();
    const filter = TestBed.inject(FilterStore);
    filter.clear();
    filter.replace({ id: 'scope-1', kind: 'resource', label: 'Scope' });
    TestBed.inject(ViewStateStore).setWindow({
      from: '2026-06-15T00:00:00',
      to: '2026-06-22T00:00:00',
    });
  });

  it('does NOT render the on-screen count line anymore', async () => {
    const f = await makeHost();
    const metaLine = (f.nativeElement as HTMLElement).querySelector('p.meta');
    expect(metaLine?.textContent ?? '').not.toContain('Termine');
  });

  it('the title carries the count for print — "Termine (3 Termine)"', async () => {
    const f = await makeHost();
    const h2 = (f.nativeElement as HTMLElement).querySelector('h2.view-title');
    expect(h2?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Termine (3 Termine)');
  });

  it('publishes the count to ViewStateStore.resultInfo for the control strip', async () => {
    await makeHost();
    expect(TestBed.inject(ViewStateStore).resultInfo()).toBe('3 Termine');
  });
});

describe('ViewControlStripComponent — right-aligned result info', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ViewControlStripComponent] }).compileComponents();
  });

  it('renders resultInfo from the store, right-aligned', () => {
    const store = TestBed.inject(ViewStateStore);
    store.setResultInfo('68 Termine');
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    const info = (f.nativeElement as HTMLElement).querySelector('.result-info');
    expect(info?.textContent?.trim()).toBe('68 Termine');
  });

  it('renders nothing when no result info is published', () => {
    const store = TestBed.inject(ViewStateStore);
    store.setResultInfo(null);
    const f = TestBed.createComponent(ViewControlStripComponent);
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('.result-info')).toBeNull();
  });
});
