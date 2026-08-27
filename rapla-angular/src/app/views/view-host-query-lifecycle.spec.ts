import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { Observable, Subject, of } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';

import { ViewHostComponent } from './view-host.component';
import { GraphqlService, type GqlResponse, type ViewMeta } from '../graphql/graphql.service';
import { ViewStateStore } from '../state/view-state-store';
import { FilterStore } from '../state/filter-store';
import { EventDataService } from '../event/event-data.service';
import { UndoToastService } from '../actions/undo-toast.service';

/**
 * PRD 106 Phase 1 / PRD 078 Phase 5 — rapid date-navigation must not leave a
 * trail of un-cancelled requests. The trigger is throttled (leading+trailing)
 * and piped through switchMap, so a burst of clicks collapses to the leading
 * request plus one trailing request on the FINAL window, and whatever is still
 * in flight when a newer trigger lands gets unsubscribed (→ xhr.abort()).
 */

const META: ViewMeta = {
  key: 'Termine',
  title: 'Termine',
  variables: [{ name: 'filter', type: 'ReservationFilter!' }],
  columns: [{ alias: 'name', header: 'Titel', type: 'String', order: 1 }],
};

const RESPONSE = {
  data: { appointmentBlocks: [] },
  extensions: { view: META },
} as GqlResponse<unknown>;

function windowAt(day: string) {
  return { from: `2026-06-${day}T00:00:00`, to: `2026-06-${day}T23:59:59` };
}

/** Captured `executeView(name, variables)` calls. */
let calls: Record<string, unknown>[];
/** Number of subscriptions torn down without completing (i.e. aborted). */
let aborted: number;

/** Emits the response synchronously — lets us assert call count + variables. */
const immediateGql = {
  executeView: (_name: string, variables: Record<string, unknown> = {}) => {
    calls.push(variables);
    return of(RESPONSE);
  },
  mutate: vi.fn(() => of({ kind: 'ok', data: {} })),
};

/** Never emits — lets us observe the teardown of a superseded request. */
const hangingGql = {
  executeView: (_name: string, variables: Record<string, unknown> = {}) =>
    new Observable<GqlResponse<unknown>>(() => {
      calls.push(variables);
      return () => {
        aborted++;
      };
    }),
  mutate: vi.fn(() => of({ kind: 'ok', data: {} })),
};

async function configure(gql: unknown): Promise<void> {
  await TestBed.configureTestingModule({
    imports: [ViewHostComponent],
    providers: [
      { provide: GraphqlService, useValue: gql },
      {
        provide: MatDialog,
        useValue: { open: vi.fn(() => ({ afterClosed: () => of(undefined) })) },
      },
      { provide: EventDataService, useValue: { load: vi.fn(), save: vi.fn() } },
      { provide: UndoToastService, useValue: { run: vi.fn(), mutated$: new Subject<void>() } },
    ],
  }).compileComponents();
  const filter = TestBed.inject(FilterStore);
  filter.clear();
  filter.replace({ id: 'scope-1', kind: 'resource', label: 'Scope' });
  TestBed.inject(ViewStateStore).setWindow(windowAt('15'));
}

function mount(): ComponentFixture<ViewHostComponent> {
  const f = TestBed.createComponent(ViewHostComponent);
  f.componentRef.setInput('viewName', 'Termine');
  f.detectChanges();
  return f;
}

describe('ViewHostComponent — query lifecycle (PRD 106)', () => {
  beforeEach(() => {
    calls = [];
    aborted = 0;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('collapses a burst of window changes to the leading + one trailing request', async () => {
    await configure(immediateGql);
    const store = TestBed.inject(ViewStateStore);
    const f = mount();
    expect(calls.length).toBe(1); // leading request fires immediately

    for (const day of ['16', '17', '18']) {
      store.setWindow(windowAt(day));
      f.detectChanges();
    }
    expect(calls.length).toBe(1); // burst swallowed while the throttle window is open

    vi.advanceTimersByTime(300);
    f.detectChanges();
    expect(calls.length).toBe(2); // exactly one trailing request

    // ...and it carries the FINAL window, not an intermediate one.
    const filter = calls[1]['filter'] as { from: string };
    expect(filter.from).toBe(windowAt('18').from);
  });

  it('aborts an in-flight request when a newer one supersedes it', async () => {
    await configure(hangingGql);
    const store = TestBed.inject(ViewStateStore);
    const f = mount();
    expect(calls.length).toBe(1);
    expect(aborted).toBe(0);

    store.setWindow(windowAt('20'));
    f.detectChanges();
    vi.advanceTimersByTime(300);
    f.detectChanges();

    expect(calls.length).toBe(2);
    expect(aborted).toBe(1); // the first request was cancelled, not just ignored
  });
});

/** Raumauslastung shape: a second NonNull variable the {} first query cannot satisfy. */
const TWO_FILTER_META: ViewMeta = {
  key: 'Raumauslastung',
  title: 'Raumauslastung',
  variables: [
    { name: 'filter', type: 'ReservationFilter!' },
    { name: 'allocatableFilter', type: 'AllocatableFilter!' },
  ],
  columns: [{ alias: 'raum', header: 'Raum', type: 'String', order: 1 }],
};

/** First call fails with a coercion error but carries the signature; the re-query succeeds. */
const errorWithMetaThenOk = {
  executeView: (_name: string, variables: Record<string, unknown> = {}) => {
    calls.push(variables);
    return of(
      calls.length === 1
        ? ({
            errors: [{ message: "Variable 'allocatableFilter' has coerced Null value" }],
            extensions: { view: TWO_FILTER_META },
          } as GqlResponse<unknown>)
        : ({
            data: { appointmentBlockStats: [] },
            extensions: { view: TWO_FILTER_META },
          } as GqlResponse<unknown>),
    );
  },
  mutate: vi.fn(() => of({ kind: 'ok', data: {} })),
};

/**
 * PRD 097 D8 fallout — a view's stored defaultVariables no longer fill missing variables, so
 * the {} first query of a view with a second NonNull filter FAILS. The server still sends
 * extensions.view with the error (ViewMetaOnErrorTest); the host must bind that signature and
 * re-query with properly filled variables instead of being stuck on the error forever.
 */
describe('ViewHostComponent — signature recovery from an errored first query (PRD 097 D8)', () => {
  beforeEach(() => {
    calls = [];
    aborted = 0;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('binds the signature off the error response and re-queries with filled variables', async () => {
    await configure(errorWithMetaThenOk);
    const f = mount();
    expect(calls.length).toBe(1);
    expect(calls[0]['allocatableFilter']).toBeUndefined(); // signature unknown on first load

    f.detectChanges(); // meta bound from the error → the query effect re-runs
    vi.advanceTimersByTime(300); // the re-query is the throttle's trailing emission
    f.detectChanges();

    expect(calls.length).toBe(2);
    expect(calls[1]['allocatableFilter']).toEqual({ idIn: ['scope-1'] });
    expect(f.componentInstance.error()).toBeNull();
  });
});
