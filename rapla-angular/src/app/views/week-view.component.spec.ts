import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { of } from 'rxjs';

import { WeekViewComponent } from './week-view.component';
import { GraphqlService, type GqlResponse, type ViewMeta } from '../graphql/graphql.service';
import { ViewStateStore } from '../state/view-state-store';
import { WEEK_VIEW_FALLBACK } from './week-view';

const META: ViewMeta = WEEK_VIEW_FALLBACK;

const BLOCKS: Record<string, unknown>[] = [
  {
    start: '2026-06-17T10:00:00', // Mittwoch
    end: '2026-06-17T11:30:00',
    times: '10:00 - 11:30',
    name: 'Physik',
    personen: [{ id: 'p1', name: 'Prof X' }],
    nichtPersonen: [{ id: 'r1', name: 'C452' }],
  },
  {
    start: '2026-06-15T08:00:00', // Montag
    end: '2026-06-15T09:30:00',
    times: '08:00 - 09:30',
    name: 'Mathe',
    personen: [],
    nichtPersonen: [{ id: 'r2', name: 'C348' }],
  },
];

const GQL_STUB = {
  query: <T>(): ReturnType<GraphqlService['query']> =>
    of({
      data: { appointmentBlocks: BLOCKS } as unknown as T,
      extensions: { view: META },
    } as GqlResponse<T>),
};

async function settle(f: ComponentFixture<WeekViewComponent>): Promise<void> {
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
}

describe('WeekViewComponent', () => {
  let viewState: ViewStateStore;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WeekViewComponent],
      providers: [{ provide: GraphqlService, useValue: GQL_STUB }],
    }).compileComponents();
    viewState = TestBed.inject(ViewStateStore);
    viewState.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
  });

  it('groups blocks into weekday sections in Montag→Sonntag order', async () => {
    const f = TestBed.createComponent(WeekViewComponent);
    await settle(f);
    const headers = Array.from((f.nativeElement as HTMLElement).querySelectorAll('.day h3')).map(
      (h) => h.textContent ?? '',
    );
    expect(headers.length).toBe(2);
    expect(headers[0]).toContain('Montag'); // ordered, even though Mittwoch block came first
    expect(headers[1]).toContain('Mittwoch');
  });

  it('renders the block name and joined non-person allocatable in the right day', async () => {
    const f = TestBed.createComponent(WeekViewComponent);
    await settle(f);
    const el = f.nativeElement as HTMLElement;
    const days = el.querySelectorAll('.day');
    const montag = days[0].textContent ?? '';
    expect(montag).toContain('Mathe');
    expect(montag).toContain('C348');
    expect((days[1].textContent ?? '')).toContain('Physik');
  });

  it('does not render the hidden reservation/duration columns as headers', async () => {
    const f = TestBed.createComponent(WeekViewComponent);
    await settle(f);
    const ths = Array.from((f.nativeElement as HTMLElement).querySelectorAll('thead th')).map(
      (t) => t.textContent?.trim() ?? '',
    );
    expect(ths).not.toContain('reservation');
    expect(ths).not.toContain('durationMinutes');
    expect(ths).toContain('Titel');
  });
});
