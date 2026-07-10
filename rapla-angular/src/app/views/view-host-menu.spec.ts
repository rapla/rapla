import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Subject, of } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';

import { ViewHostComponent } from './view-host.component';
import { ROW_MENU_PROVIDERS, EventRowMenuProvider } from './row-menu';
import { DeleteScopeDialogComponent } from './delete-scope-dialog.component';
import { GraphqlService, type GqlResponse, type ViewMeta } from '../graphql/graphql.service';
import { ViewStateStore } from '../state/view-state-store';
import { FilterStore } from '../state/filter-store';
import { EventSheetComponent } from '../event/event-sheet.component';
import { EventDataService, type LoadedEvent } from '../event/event-data.service';
import { UndoToastService } from '../actions/undo-toast.service';
import type { EventDraft } from '../event/event-draft';

const META: ViewMeta = {
  key: 'Termine',
  title: 'Termine',
  variables: [{ name: 'filter', type: 'ReservationFilter!' }],
  columns: [
    { alias: 'start', header: 'Von', type: 'LocalDateTime', order: 1 },
    { alias: 'name', header: 'Titel', type: 'String', order: 2 },
    { alias: 'reservation', header: '', hidden: true },
    { alias: 'appointmentId', header: '', hidden: true },
    { alias: 'isException', header: '', hidden: true },
  ],
};

const ROWS: Record<string, unknown>[] = [
  {
    start: '2026-06-15T08:00:00',
    name: 'Mathe',
    reservation: { id: 'e-edit', canModify: true },
    appointmentId: 'a-1',
    isException: false,
  },
  {
    start: '2026-06-16T08:00:00',
    name: 'Fremd',
    reservation: { id: 'e-readonly', canModify: false },
    appointmentId: 'a-2',
    isException: false,
  },
  {
    start: '2026-06-17T08:00:00',
    name: 'Ausnahme',
    reservation: { id: 'e-exc', canModify: true },
    appointmentId: 'a-3',
    isException: true,
  },
  { start: '2026-06-18T08:00:00', name: 'Ohne Subjekt' },
];

const GQL_STUB = {
  executeView: <T>(): ReturnType<GraphqlService['executeView']> =>
    of({
      data: { appointmentBlocks: ROWS } as unknown as T,
      extensions: { view: META },
    } as GqlResponse<T>),
  mutate: vi.fn(() => of({ kind: 'ok', data: {} })),
};

const LOADED_DRAFT: EventDraft = {
  id: 'e-edit',
  persisted: true,
  typeKey: 'event',
  values: { name: 'Mathe' },
  appointments: [
    {
      id: 'a-1',
      start: '2026-06-15T08:00:00',
      end: '2026-06-15T09:30:00',
      allDay: false,
      repeating: null,
    },
  ],
  allocations: [],
  lastChanged: '2026-06-01T10:00:00',
};

const dialogOpen = vi.fn();
const dataLoad = vi.fn();
const toastRun = vi.fn();

async function settle(f: ComponentFixture<ViewHostComponent>): Promise<void> {
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
}

async function makeHost(): Promise<ComponentFixture<ViewHostComponent>> {
  const f = TestBed.createComponent(ViewHostComponent);
  f.componentRef.setInput('viewName', 'Termine');
  await settle(f);
  return f;
}

function menuButtons(f: ComponentFixture<ViewHostComponent>): HTMLButtonElement[] {
  return Array.from(
    (f.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button.row-menu-btn'),
  );
}

function openItems(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('button.mat-mdc-menu-item'));
}

function openLabels(f: ComponentFixture<ViewHostComponent>, buttonIndex: number): string[] {
  menuButtons(f)[buttonIndex].click();
  f.detectChanges();
  return openItems().map((b) => (b.textContent ?? '').trim());
}

describe('ViewHostComponent — row context menu (PRD 094 Phase 1+2)', () => {
  beforeEach(async () => {
    dialogOpen.mockReset();
    dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });
    dataLoad.mockReset();
    dataLoad.mockReturnValue(of({ draft: LOADED_DRAFT, canModify: true } satisfies LoadedEvent));
    toastRun.mockReset();
    await TestBed.configureTestingModule({
      imports: [ViewHostComponent],
      providers: [
        { provide: GraphqlService, useValue: GQL_STUB },
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: EventDataService, useValue: { load: dataLoad, save: vi.fn() } },
        {
          provide: UndoToastService,
          useValue: { run: toastRun, mutated$: new Subject<void>() },
        },
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

  it('renders a ⋮ button only for rows with a subject', async () => {
    const f = await makeHost();
    expect(menuButtons(f).length).toBe(3); // fourth row has no reservation field
  });

  it('canModify row: Bearbeiten + Anzeigen + Löschen', async () => {
    const f = await makeHost();
    expect(openLabels(f, 0)).toEqual(['Bearbeiten', 'Anzeigen', 'Löschen']);
  });

  it('read-only row: only Anzeigen', async () => {
    const f = await makeHost();
    expect(openLabels(f, 1)).toEqual(['Anzeigen']);
  });

  it('exception block: no Löschen (Swing parity — occurrence already skipped)', async () => {
    const f = await makeHost();
    expect(openLabels(f, 2)).toEqual(['Bearbeiten', 'Anzeigen']);
  });

  it('Bearbeiten opens the event sheet dialog with the row id', async () => {
    const f = await makeHost();
    openLabels(f, 0);
    openItems()[0].click();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    const [component, config] = dialogOpen.mock.calls[0] as [unknown, { data: unknown }];
    expect(component).toBe(EventSheetComponent);
    expect(config.data).toMatchObject({ id: 'e-edit' });
  });

  it('Anzeigen opens the sheet read-only', async () => {
    const f = await makeHost();
    openLabels(f, 1);
    openItems()[0].click();
    const [, config] = dialogOpen.mock.calls[0] as [unknown, { data: unknown }];
    expect(config.data).toMatchObject({ id: 'e-readonly', readOnly: true });
  });

  it('Löschen loads the event, opens the scope dialog, and runs the command on confirm', async () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of('event') });
    const f = await makeHost();
    const labels = openLabels(f, 0);
    openItems()[labels.indexOf('Löschen')].click();
    expect(dataLoad).toHaveBeenCalledWith('e-edit');
    const [component, config] = dialogOpen.mock.calls[0] as [
      unknown,
      { data: { eventName: string; options: { scope: string }[] } },
    ];
    expect(component).toBe(DeleteScopeDialogComponent);
    expect(config.data.eventName).toBe('Mathe');
    // single non-repeating appointment in a 1-appointment event → only whole-event
    expect(config.data.options.map((o) => o.scope)).toEqual(['event']);
    expect(toastRun).toHaveBeenCalledTimes(1);
    const command = toastRun.mock.calls[0][0] as { label: string };
    expect(command.label).toContain('gelöscht');
  });

  it('aborting the scope dialog runs nothing', async () => {
    const f = await makeHost();
    const labels = openLabels(f, 0);
    openItems()[labels.indexOf('Löschen')].click();
    expect(toastRun).not.toHaveBeenCalled();
  });

  it('right-click on a data row opens the same menu', async () => {
    const f = await makeHost();
    const rows = (f.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    rows[0].dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true }));
    f.detectChanges();
    expect(openItems().length).toBeGreaterThan(0);
  });

  it('double-click on an editable row opens the edit sheet', async () => {
    const f = await makeHost();
    const rows = (f.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    rows[0].dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
    f.detectChanges();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    const [component, config] = dialogOpen.mock.calls[0] as [unknown, { data: unknown }];
    expect(component).toBe(EventSheetComponent);
    expect(config.data).toMatchObject({ id: 'e-edit' });
    expect(config.data).not.toMatchObject({ readOnly: true });
  });

  it('double-click on a read-only row falls back to Anzeigen', async () => {
    const f = await makeHost();
    const rows = (f.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    rows[1].dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
    f.detectChanges();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    const [, config] = dialogOpen.mock.calls[0] as [unknown, { data: unknown }];
    expect(config.data).toMatchObject({ id: 'e-readonly', readOnly: true });
  });

  it('double-click on a subject-less row does nothing', async () => {
    const f = await makeHost();
    const rows = (f.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    rows[3].dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
    f.detectChanges();
    expect(dialogOpen).not.toHaveBeenCalled();
  });
});
