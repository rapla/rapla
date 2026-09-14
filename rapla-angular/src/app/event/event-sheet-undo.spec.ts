import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { EventSheetComponent } from './event-sheet.component';

/**
 * Tier-6 — PRD 091 plan 2c.3: the undo/redo savebar buttons and the
 * host-scoped Ctrl+Z / Ctrl+Y shortcuts. Data layer stays untouched
 * (HttpClientTesting: the type + availability queries pend harmlessly;
 * the isNew path never loads the reservation).
 */
describe('EventSheetComponent undo/redo (D5)', () => {
  beforeEach(async () => {
    window.history.replaceState({ isNew: true }, '');
    await TestBed.configureTestingModule({
      imports: [EventSheetComponent],
      providers: [provideAnimationsAsync(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  const SDL = `
type eventClassification implements Classification & ReservationClassification {
  name: String @displayName(value : "Name") @editView(value : "title")
  beschreibung: String @displayName(value : "Beschreibung")
  type: DynamicType!
  typeKey: String!
}

type meetingClassification implements Classification & ReservationClassification {
  name: String @displayName(value : "Name") @editView(value : "title")
  topic: String @displayName(value : "Thema")
  type: DynamicType!
  typeKey: String!
}
`;

  async function create(sdl = SDL) {
    const fixture = TestBed.createComponent(EventSheetComponent);
    fixture.componentRef.setInput('id', 'e-test-undo-1');
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/graphql/schema').flush(sdl);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  /** Flush the LATEST pending reservationPrototype POST (PRD 099). */
  function flushPrototype(values: Record<string, unknown> | null) {
    const ctl = TestBed.inject(HttpTestingController);
    const reqs = ctl.match(
      (r) =>
        r.url === '/api/graphql' &&
        ((r.body as { query?: string })?.query ?? '').includes('reservationPrototype'),
    );
    expect(reqs.length).toBeGreaterThan(0);
    reqs[reqs.length - 1].flush({
      data: { reservationPrototype: values ? { classification: values } : null },
    });
  }

  it('savebar buttons: disabled when history empty, undo/redo roundtrip a mutation', async () => {
    const fixture = await create();
    const el = fixture.nativeElement as HTMLElement;
    const [undoBtn, redoBtn] = Array.from(
      el.querySelectorAll<HTMLButtonElement>('.savebar button.hist'),
    );
    expect(undoBtn.disabled).toBe(true);
    expect(redoBtn.disabled).toBe(true);

    fixture.componentInstance.addAppointment();
    fixture.detectChanges();
    expect(fixture.componentInstance.draft()!.appointments.length).toBe(2);
    expect(undoBtn.disabled).toBe(false);
    expect(undoBtn.title).toContain('+ Termin');

    undoBtn.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.draft()!.appointments.length).toBe(1);
    expect(undoBtn.disabled).toBe(true);
    expect(redoBtn.disabled).toBe(false);

    redoBtn.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.draft()!.appointments.length).toBe(2);
  });

  it('Ctrl+Z on the host undoes; Ctrl+Z inside a text input is left to the browser', async () => {
    const fixture = await create();
    const el = fixture.nativeElement as HTMLElement;
    fixture.componentInstance.addAppointment();
    fixture.detectChanges();
    expect(fixture.componentInstance.draft()!.appointments.length).toBe(2);

    const section = el.querySelector('section.sec') as HTMLElement;
    section.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.draft()!.appointments.length).toBe(1);

    // redo via Ctrl+Y
    section.dispatchEvent(new KeyboardEvent('keydown', { key: 'y', ctrlKey: true, bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.draft()!.appointments.length).toBe(2);

    // focus in a text input → the shortcut must NOT fire (native undo owns it)
    fixture.componentInstance.openHeader();
    fixture.detectChanges();
    const nameInput = el.querySelector('input[type="text"]') as HTMLInputElement;
    nameInput.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance.draft()!.appointments.length).toBe(2);
  });

  it('type change remaps values via the schema descriptors and is undoable (PRD 096)', async () => {
    const fixture = await create();
    const cmp = fixture.componentInstance;
    cmp.applyClassificationPatch({
      key: 'beschreibung',
      value: 'mit Stativ',
      label: 'Beschreibung',
      coalesceKey: 'values:beschreibung',
    });
    cmp.setTitle('name', 'Kamera-Schulung');
    expect(cmp.draft()!.values).toEqual({ beschreibung: 'mit Stativ', name: 'Kamera-Schulung' });

    cmp.setTypeKey('meeting');
    flushPrototype(null); // prototype unavailable → remap-only fallback
    const after = cmp.draft()!;
    expect(after.typeKey).toBe('meeting');
    // same-key same-type survives, beschreibung is not a meeting attribute → dropped
    expect(after.values).toEqual({ name: 'Kamera-Schulung' });
    expect(cmp.undoLabel()).toBe('Veranstaltungstyp');

    cmp.undo();
    const restored = cmp.draft()!;
    expect(restored.typeKey).toBe('event');
    expect(restored.values).toEqual({ beschreibung: 'mit Stativ', name: 'Kamera-Schulung' });
  });

  it('a type without title attributes renders NO header title field and never invents name (PRD 096 D5)', async () => {
    const fixture = await create(`
type eventClassification implements Classification & ReservationClassification {
  loan: String @displayName(value : "Ausleihe")
  type: DynamicType!
  typeKey: String!
}
`);
    const cmp = fixture.componentInstance;
    cmp.openHeader();
    fixture.detectChanges();
    expect(cmp.titleAttrs()).toEqual([]);
    const header = (fixture.nativeElement as HTMLElement).querySelector('section.sec')!;
    const labels = Array.from(header.querySelectorAll('.lab')).map((l) => l.textContent?.trim());
    expect(labels).not.toContain('Name');
    // the loan attribute comes from the classification component instead
    expect(labels).toContain('Ausleihe');
    // setTitle is inert — nothing may inject a name key the @oneOf input lacks
    cmp.setTitle('name', 'x');
    expect(cmp.draft()!.values['name']).toBeUndefined();
    expect(cmp.displayName(cmp.draft()!)).toBe('ohne Namen');
  });

  it('a composite-nameformat type renders one header field per title attribute (PRD 096 D5)', async () => {
    const fixture = await create(`
type eventClassification implements Classification & ReservationClassification {
  surname: String @displayName(value : "Nachname") @editView(value : "title")
  forename: String @displayName(value : "Vorname") @editView(value : "title")
  hidden: String @displayName(value : "Versteckt") @editView(value : "no-view")
  type: DynamicType!
  typeKey: String!
}
`);
    const cmp = fixture.componentInstance;
    cmp.openHeader();
    fixture.detectChanges();
    expect(cmp.titleAttrs().map((t) => t.key)).toEqual(['surname', 'forename']);
    const header = (fixture.nativeElement as HTMLElement).querySelector('section.sec')!;
    const labels = Array.from(header.querySelectorAll('.lab')).map((l) => l.textContent?.trim());
    expect(labels).toContain('Nachname');
    expect(labels).toContain('Vorname');
    // no-view attributes are admin-hidden — neither header nor component renders them
    expect(labels).not.toContain('Versteckt');

    cmp.setTitle('surname', 'Simpson');
    cmp.setTitle('forename', 'Homer');
    expect(cmp.displayName(cmp.draft()!)).toBe('Simpson Homer');
  });

  it('a new draft is seeded with the server prototype defaults, not dirty, no history (PRD 099)', async () => {
    const fixture = await create();
    const cmp = fixture.componentInstance;
    expect(cmp.draft()!.values).toEqual({});

    // the ngOnInit seeding fetch — server returns the type defaults
    flushPrototype({ name: null, beschreibung: 'Standard-Beschreibung' });
    fixture.detectChanges();

    // non-null defaults are seeded, nulls stay OMITTED (wire: absent = default)
    expect(cmp.draft()!.values).toEqual({ beschreibung: 'Standard-Beschreibung' });
    // baseline includes the seeds — a fresh draft is NOT dirty, history empty
    expect(cmp.dirty()).toBe(false);
    expect(cmp.canUndo()).toBe(false);
  });

  it('type switch gap-fills target-type defaults in ONE undoable step (PRD 099)', async () => {
    const fixture = await create();
    const cmp = fixture.componentInstance;
    cmp.setTitle('name', 'Kamera-Schulung');

    cmp.setTypeKey('meeting');
    flushPrototype({ name: null, topic: 'Standard-Thema' });
    const after = cmp.draft()!;
    expect(after.typeKey).toBe('meeting');
    // remapped value wins, default fills only the gap
    expect(after.values).toEqual({ name: 'Kamera-Schulung', topic: 'Standard-Thema' });
    expect(cmp.undoLabel()).toBe('Veranstaltungstyp');

    // ONE undo step restores the complete pre-switch state
    cmp.undo();
    expect(cmp.draft()!.typeKey).toBe('event');
    expect(cmp.draft()!.values).toEqual({ name: 'Kamera-Schulung' });
  });

  it('a CATEGORY title attribute gets its enum select in the header, not a raw text input (PRD 096 D5)', async () => {
    const fixture = await create(`
enum loan {
  "Geplant"
  planned
  "Ausgeliehen"
  out
}

type eventClassification implements Classification & ReservationClassification {
  loan: loan @displayName(value : "Ausleihe") @editView(value : "title") @rootCategory(path : "loan")
  type: DynamicType!
  typeKey: String!
}
`);
    const cmp = fixture.componentInstance;
    cmp.openHeader();
    fixture.detectChanges();
    expect(cmp.titleAttrs().map((t) => t.key)).toEqual(['loan']);
    const header = (fixture.nativeElement as HTMLElement).querySelector('section.sec')!;
    const select = header.querySelector<HTMLSelectElement>('app-classification-edit select');
    expect(select).not.toBeNull();
    expect(Array.from(select!.options).map((o) => o.textContent?.trim())).toContain('Geplant');
    // no free-text input for the category — only the type select + enum select
    expect(header.querySelector('app-classification-edit input[type="text"]')).toBeNull();

    cmp.applyClassificationPatch({
      key: 'loan',
      value: 'planned',
      label: 'Ausleihe',
      coalesceKey: null,
    });
    // collapsed header shows the enum LABEL, not the raw key
    expect(cmp.displayName(cmp.draft()!)).toBe('Geplant');
  });
});
