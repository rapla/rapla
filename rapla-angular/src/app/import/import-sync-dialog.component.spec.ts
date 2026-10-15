import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { NewEventOptionsService } from '../event/new-event-options.service';
import { ViewStateStore } from '../state/view-state-store';
import { BindPickService } from './bind-pick.service';
import type { WorklistItem } from './import-models';
import { ImportSyncDialogComponent } from './import-sync-dialog.component';
import { ImportWorklistService } from './import-worklist.service';
import { ParkedEventsService } from './parked-events.service';

/** WP23x C2/C3 dialog logic (review round 18): grouping, "n von m bereit", menu search cap,
 *  candidates follow the dialog's semester (M1). */
describe('ImportSyncDialogComponent', () => {
  const item = (sourceId: string, kind: 'v' | 'p'): WorklistItem => ({
    sourceId,
    kind,
    name: sourceId,
    unit: null,
    semester: 'WiSe 2026/27',
    state: 'OPEN',
    changed: false,
    changedSince: null,
    groupIds: ['k1'],
    groupName: 'STG-WIB25B',
    boundReservationId: null,
    fullName: null,
  });
  const items = [
    item('v:1', 'v'),
    item('v:2', 'v'),
    item('p:1', 'p'),
    { ...item('v:3', 'v'), semester: 'SoSe 2027' },
  ];
  const bindCandidates = vi.fn(() => of([]));

  function create() {
    TestBed.resetTestingModule();
    bindCandidates.mockClear();
    TestBed.configureTestingModule({
      imports: [ImportSyncDialogComponent],
      providers: [
        provideAnimationsAsync(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            groupIds: ['k1'],
            groupLabel: 'STG-WIB25B',
            semester: 'WiSe 2026/27',
            defaultStart: '2026-10-12T08:00:00',
          },
        },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
        { provide: BindPickService, useValue: { arm: vi.fn() } },
        { provide: ParkedEventsService, useValue: { add: vi.fn() } },
        {
          provide: ViewStateStore,
          useValue: {
            window: signal({ from: '2026-10-12T00:00:00', to: '2026-10-19T00:00:00' }),
            setRenderMode: vi.fn(),
          },
        },
        {
          provide: NewEventOptionsService,
          useValue: {
            templates: signal(
              Array.from({ length: 60 }, (_, i) => ({ id: `t${i}`, name: `Vorlage ${i}` })),
            ),
            ensureLoaded: () => of(null),
          },
        },
        {
          provide: ImportWorklistService,
          useValue: {
            sourceName: signal('Dualis'),
            worklist: signal({ groups: [{ id: 'k1', name: 'STG-WIB25B' }], items }),
            linked: signal([]),
            loadFailed: signal(false),
            windowRange: signal(null),
            ensureLoaded: () => of(null),
            defaultTemplates: () =>
              of({ lectureTemplateId: 'lv', lectureTemplateName: 'LV-Vorlage' }),
            templateSuggestions: () => of([]),
            bindCandidates,
          },
        },
      ],
    });
    const f = TestBed.createComponent(ImportSyncDialogComponent);
    f.detectChanges();
    return f;
  }

  beforeEach(() => create());

  it('groups open items into lectures and exams', () => {
    const c = create().componentInstance;
    expect(c.lectures().map((i) => i.sourceId)).toEqual(['v:1', 'v:2']);
    expect(c.exams().map((i) => i.sourceId)).toEqual(['p:1']);
  });

  it('counts only checked items that have a template as ready', () => {
    const c = create().componentInstance;
    c.checked.set(new Set(['v:1', 'p:1']));
    expect(c.readyCount()).toBe(1); // the exam has no template yet
  });

  it('renders at most 50 templates in the menu and filters by the search', () => {
    const c = create().componentInstance;
    expect(c.menuTemplates().shown.length).toBe(50);
    expect(c.menuTemplates().more).toBe(10);
    c.menuQuery.set('vorlage 5');
    expect(c.menuTemplates().shown.map((t) => t.id)).toEqual([
      't5',
      't50',
      't51',
      't52',
      't53',
      't54',
      't55',
      't56',
      't57',
      't58',
      't59',
    ]);
  });

  it('asks for candidates again after a semester step (M1)', async () => {
    const f = create();
    await f.whenStable();
    const before = bindCandidates.mock.calls.length;
    expect(before).toBeGreaterThan(0);
    f.componentInstance.stepSemester(1);
    f.detectChanges();
    await f.whenStable();
    const after = bindCandidates.mock.calls.slice(before) as unknown[][];
    expect(after.map((call) => call[0])).toEqual(['v:3']);
    expect(after[0][1]).toBe('2027-04-01T00:00:00');
    expect(Object.keys(f.componentInstance.candidates())).not.toContain('v:1');
  });

  it('Kurs chips filter the list: an item only of the switched-off Kurs disappears', () => {
    const c = create().componentInstance;
    c.setKurs('k1', false);
    expect(c.lectures()).toEqual([]);
    c.setKurs('k1', true);
    expect(c.lectures().map((i) => i.sourceId)).toEqual(['v:1', 'v:2']);
  });

  it('preselects nothing; select all / none work on the open rows (addendum 1)', () => {
    const c = create().componentInstance;
    expect(c.checked().size).toBe(0);
    c.selectAll();
    expect([...c.checked()].sort()).toEqual(['p:1', 'v:1', 'v:2']);
    c.selectNone();
    expect(c.checked().size).toBe(0);
  });

  it('the group checkbox is tri-state and toggles the whole group (addendum 1)', () => {
    const c = create().componentInstance;
    expect(c.groupState('v')).toBe('none');
    c.toggle('v:1');
    expect(c.groupState('v')).toBe('some');
    c.toggleGroupSelection('v');
    expect(c.groupState('v')).toBe('all');
    c.toggleGroupSelection('v');
    expect(c.groupState('v')).toBe('none');
  });

  it('the group chip sets the template of the checked rows only (addendum 3)', () => {
    const c = create().componentInstance;
    c.toggle('v:1');
    c.menuTarget.set({ group: 'v' });
    c.pickTemplate({ id: 't7', name: 'Vorlage 7' });
    expect(c.rowTemplate().has('v:1')).toBe(true);
    expect(c.rowTemplate().has('v:2')).toBe(false);
    expect(c.selectedIn('p').length).toBe(0);
  });

  it('the semester stepper only visits semesters with entries and stops at the ends (addendum 2)', () => {
    const c = create().componentInstance;
    expect(c.semesters()).toEqual(['WiSe 2026/27', 'SoSe 2027']);
    expect(c.canStep(-1)).toBe(false);
    expect(c.canStep(1)).toBe(true);
    c.stepSemester(1);
    expect(c.semester()).toBe('SoSe 2027');
    expect(c.canStep(1)).toBe(false);
  });

  it('the footer counts only checked rows that are still visible (review 20 M1)', () => {
    const c = create().componentInstance;
    c.selectAll();
    expect(c.checkedVisible()).toBe(3);
    c.setKurs('k1', false);
    expect(c.checkedVisible()).toBe(0);
    expect(c.readyCount()).toBe(0);
  });

  it('keeps the shown semester in the stepper list when its Kurs is switched off (review 20 b)', () => {
    const c = create().componentInstance;
    c.stepSemester(1);
    expect(c.semester()).toBe('SoSe 2027');
    c.setKurs('k1', false);
    expect(c.semesters()).toContain('SoSe 2027');
    expect(c.canStep(-1)).toBe(true);
  });
});
