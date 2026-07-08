import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter } from '@angular/material/core';

import { EventSheetComponent } from './event-sheet.component';

/**
 * Tier-6 — PRD 091 Phase 4.3: the recurrence panel in the Termine section.
 * Data layer via HttpClientTesting; the expandOccurrences preview query is
 * flushed by hand.
 */
describe('EventSheetComponent recurrence panel (Phase 4.3)', () => {
  beforeEach(async () => {
    window.history.replaceState({ isNew: true }, '');
    await TestBed.configureTestingModule({
      imports: [EventSheetComponent],
      providers: [
        provideAnimationsAsync(),
        provideNativeDateAdapter(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  const SDL = `
type eventClassification implements Classification & ReservationClassification {
  name: String @displayName(value : "Name") @editView(value : "title")
  type: DynamicType!
  typeKey: String!
}
`;

  async function create() {
    const fixture = TestBed.createComponent(EventSheetComponent);
    fixture.componentRef.setInput('id', 'e-test-rep-1');
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/graphql/schema').flush(SDL);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function flushPreview(rows: { start: string; end: string; exception: boolean }[]) {
    const ctl = TestBed.inject(HttpTestingController);
    const reqs = ctl.match(
      (r) =>
        r.url === '/api/graphql' &&
        ((r.body as { query?: string })?.query ?? '').includes('expandOccurrences'),
    );
    expect(reqs.length).toBeGreaterThan(0);
    reqs[reqs.length - 1].flush({ data: { expandOccurrences: rows } });
  }

  it('↻ opens the panel; selecting weekly seeds the rule with the start weekday', async () => {
    const fixture = await create();
    const cmp = fixture.componentInstance;
    const a = cmp.draft()!.appointments[0];
    expect(a.repeating).toBeNull();

    cmp.toggleRepeatingPanel(a);
    fixture.detectChanges();
    const panel = (fixture.nativeElement as HTMLElement).querySelector('.rep-panel');
    expect(panel).not.toBeNull();

    cmp.setRepeatingType(a, 'WEEKLY');
    fixture.detectChanges();
    const rule = cmp.draft()!.appointments[0].repeating!;
    expect(rule.type).toBe('WEEKLY');
    expect(rule.weekdays).toHaveLength(1);
    expect(cmp.undoLabel()).toBe('Wiederholung');

    // weekday chips render and toggle through the draft funnel
    const chips = (fixture.nativeElement as HTMLElement).querySelectorAll('.rep-weekdays .wd');
    expect(chips).toHaveLength(7);
    cmp.toggleRepWeekday(cmp.draft()!.appointments[0], 5);
    expect(cmp.draft()!.appointments[0].repeating!.weekdays).toContain(5);

    // rule edits are undoable — one step back removes the weekday
    cmp.undo();
    expect(cmp.draft()!.appointments[0].repeating!.weekdays).toHaveLength(1);
  });

  it('end-mode switch seeds count/until and the summary line renders', async () => {
    const fixture = await create();
    const cmp = fixture.componentInstance;
    const a = cmp.draft()!.appointments[0];
    cmp.toggleRepeatingPanel(a);
    cmp.setRepeatingType(a, 'WEEKLY');
    cmp.setRepEndMode(cmp.draft()!.appointments[0], 'COUNT');
    fixture.detectChanges();

    const rule = cmp.draft()!.appointments[0].repeating!;
    expect(rule.count).toBe(10);
    const summary = (fixture.nativeElement as HTMLElement).querySelector('.rep-summary');
    expect(summary?.textContent).toContain('10 Termine');
  });

  it('preview renders server rows; clicking one toggles the exception (UC-E4)', async () => {
    const fixture = await create();
    const cmp = fixture.componentInstance;
    const a = cmp.draft()!.appointments[0];
    cmp.toggleRepeatingPanel(a);
    cmp.setRepeatingType(a, 'WEEKLY');
    await new Promise((r) => setTimeout(r, 300)); // preview$ debounce
    flushPreview([
      { start: '2026-07-14T10:00:00', end: '2026-07-14T11:00:00', exception: false },
      { start: '2026-07-21T10:00:00', end: '2026-07-21T11:00:00', exception: false },
    ]);
    fixture.detectChanges();

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
      '.rep-preview .occ',
    );
    expect(rows).toHaveLength(2);
    rows[1].click();
    fixture.detectChanges();
    expect(cmp.draft()!.appointments[0].repeating!.exceptions).toEqual(['2026-07-21']);
    // instant local skip marking (server refetch pends)
    expect(rows[1].classList.contains('skipped')).toBe(true);

    // NONE clears the rule entirely
    cmp.setRepeatingType(cmp.draft()!.appointments[0], 'NONE');
    expect(cmp.draft()!.appointments[0].repeating).toBeNull();
  });
});
