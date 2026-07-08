import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Subject, of } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';

import { ViewHostComponent } from './view-host.component';
import { ROW_MENU_PROVIDERS, EventRowMenuProvider } from './row-menu';
import { GraphqlService, type GqlResponse, type ViewMeta } from '../graphql/graphql.service';
import { ViewStateStore } from '../state/view-state-store';
import { FilterStore } from '../state/filter-store';
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
  ],
};

const ROWS: Record<string, unknown>[] = [
  { start: '2026-06-15T08:00:00', name: 'A', reservation: { id: 'e-1', canModify: true } },
  { start: '2026-06-16T08:00:00', name: 'B', reservation: { id: 'e-2', canModify: true } },
  { start: '2026-06-17T08:00:00', name: 'C', reservation: { id: 'e-3', canModify: true } },
  { start: '2026-06-18T08:00:00', name: 'D', reservation: { id: 'e-4', canModify: false } },
  // second block of the SAME event as C — bulk delete must dedupe to one
  { start: '2026-06-19T08:00:00', name: 'E', reservation: { id: 'e-3', canModify: true } },
];

const GQL_STUB = {
  executeView: <T>(): ReturnType<GraphqlService['executeView']> =>
    of({
      data: { appointmentBlocks: ROWS } as unknown as T,
      extensions: { view: META },
    } as GqlResponse<T>),
  mutate: vi.fn(() => of({ kind: 'ok', data: {} })),
};

const dialogOpen = vi.fn();
const dataLoad = vi.fn();
const toastRun = vi.fn();

function loaded(id: string): LoadedEvent {
  const draft: EventDraft = {
    id,
    persisted: true,
    typeKey: 'event',
    values: { name: id },
    appointments: [],
    allocations: [],
    lastChanged: '2026-06-01T10:00:00',
  };
  return { draft, canModify: true };
}

async function makeHost(): Promise<ComponentFixture<ViewHostComponent>> {
  const f = TestBed.createComponent(ViewHostComponent);
  f.componentRef.setInput('viewName', 'Termine');
  f.detectChanges();
  await f.whenStable();
  f.detectChanges();
  return f;
}

function dataRows(f: ComponentFixture<ViewHostComponent>): HTMLTableRowElement[] {
  return Array.from(
    (f.nativeElement as HTMLElement).querySelectorAll<HTMLTableRowElement>(
      'tbody tr:not(.group-row)',
    ),
  );
}

function click(row: HTMLElement, init: MouseEventInit = {}): void {
  row.dispatchEvent(new MouseEvent('click', { bubbles: true, ...init }));
}

function selectedNames(f: ComponentFixture<ViewHostComponent>): string[] {
  return dataRows(f)
    .filter((r) => r.classList.contains('selected'))
    .map((r) => r.cells[1].textContent?.trim() ?? '');
}

describe('ViewHostComponent — table selection (PRD 099 Phase 2)', () => {
  beforeEach(async () => {
    dialogOpen.mockReset();
    dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });
    dataLoad.mockReset();
    dataLoad.mockImplementation((id: string) => of(loaded(id)));
    toastRun.mockReset();
    await TestBed.configureTestingModule({
      imports: [ViewHostComponent],
      providers: [
        { provide: GraphqlService, useValue: GQL_STUB },
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: EventDataService, useValue: { load: dataLoad, save: vi.fn() } },
        { provide: UndoToastService, useValue: { run: toastRun, mutated$: new Subject<void>() } },
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

  it('plain click selects exactly that row', async () => {
    const f = await makeHost();
    click(dataRows(f)[1]);
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['B']);
    click(dataRows(f)[3]);
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['D']);
  });

  it('ctrl-click builds a discontiguous selection', async () => {
    const f = await makeHost();
    click(dataRows(f)[0]);
    click(dataRows(f)[2], { ctrlKey: true });
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['A', 'C']);
  });

  it('shift-click selects the range from the anchor', async () => {
    const f = await makeHost();
    click(dataRows(f)[0]);
    click(dataRows(f)[2], { shiftKey: true });
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['A', 'B', 'C']);
  });

  it('ArrowDown on the table moves the selection; aria tracks the active row', async () => {
    const f = await makeHost();
    click(dataRows(f)[0]);
    f.detectChanges();
    const table = (f.nativeElement as HTMLElement).querySelector('table.grid') as HTMLElement;
    table.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['B']);
    expect(table.getAttribute('aria-activedescendant')).toBe(dataRows(f)[1].id);
    expect(dataRows(f)[1].getAttribute('aria-selected')).toBe('true');
  });

  it('Shift+ArrowDown extends the selection', async () => {
    const f = await makeHost();
    click(dataRows(f)[1]);
    f.detectChanges();
    const table = (f.nativeElement as HTMLElement).querySelector('table.grid') as HTMLElement;
    table.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowDown', shiftKey: true, bubbles: true }),
    );
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['B', 'C']);
  });

  it('right-click on an UNselected row replaces the selection with it', async () => {
    const f = await makeHost();
    click(dataRows(f)[0]);
    click(dataRows(f)[1], { ctrlKey: true });
    dataRows(f)[3].dispatchEvent(
      new MouseEvent('contextmenu', { bubbles: true, cancelable: true }),
    );
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['D']);
  });

  it('right-click on a selected row keeps the multi-selection', async () => {
    const f = await makeHost();
    click(dataRows(f)[0]);
    click(dataRows(f)[1], { ctrlKey: true });
    dataRows(f)[1].dispatchEvent(
      new MouseEvent('contextmenu', { bubbles: true, cancelable: true }),
    );
    f.detectChanges();
    expect(selectedNames(f)).toEqual(['A', 'B']);
  });

  it('double-click with a multi-selection does not open the editor', async () => {
    const f = await makeHost();
    click(dataRows(f)[0]);
    click(dataRows(f)[1], { ctrlKey: true });
    dataRows(f)[1].dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
    f.detectChanges();
    expect(dialogOpen).not.toHaveBeenCalled();
  });
});

describe('ViewHostComponent — multi-select actions (PRD 099 Phase 3)', () => {
  beforeEach(async () => {
    dialogOpen.mockReset();
    dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });
    dataLoad.mockReset();
    dataLoad.mockImplementation((id: string) => of(loaded(id)));
    toastRun.mockReset();
    await TestBed.configureTestingModule({
      imports: [ViewHostComponent],
      providers: [
        { provide: GraphqlService, useValue: GQL_STUB },
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: EventDataService, useValue: { load: dataLoad, save: vi.fn() } },
        { provide: UndoToastService, useValue: { run: toastRun, mutated$: new Subject<void>() } },
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

  function contextMenu(f: ComponentFixture<ViewHostComponent>, index: number): string[] {
    dataRows(f)[index].dispatchEvent(
      new MouseEvent('contextmenu', { bubbles: true, cancelable: true }),
    );
    f.detectChanges();
    return Array.from(document.querySelectorAll<HTMLButtonElement>('button.mat-mdc-menu-item')).map(
      (b) => (b.textContent ?? '').trim(),
    );
  }

  it('a multi-selection offers bulk Löschen with the count', async () => {
    const f = await makeHost();
    click(dataRows(f)[0]);
    click(dataRows(f)[1], { ctrlKey: true });
    expect(contextMenu(f, 1)).toEqual(['Löschen (2)']);
  });

  it('OQ1 subset wins: a mixed selection names the deletable subset', async () => {
    const f = await makeHost();
    click(dataRows(f)[2]);
    click(dataRows(f)[3], { ctrlKey: true }); // D is canModify: false
    expect(contextMenu(f, 3)).toEqual(['Löschen (1 von 2)']);
  });

  it('two blocks of the SAME event dedupe to one delete', async () => {
    const f = await makeHost();
    click(dataRows(f)[2]); // C — event e-3
    click(dataRows(f)[4], { ctrlKey: true }); // E — same event e-3
    expect(contextMenu(f, 4)).toEqual(['Löschen (1)']);
  });

  it('confirming bulk Löschen loads the drafts and runs ONE bulk command', async () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of('event') });
    const f = await makeHost();
    click(dataRows(f)[0]);
    click(dataRows(f)[1], { ctrlKey: true });
    const labels = contextMenu(f, 1);
    const items = Array.from(
      document.querySelectorAll<HTMLButtonElement>('button.mat-mdc-menu-item'),
    );
    items[labels.indexOf('Löschen (2)')].click();
    expect(dataLoad).toHaveBeenCalledWith('e-1');
    expect(dataLoad).toHaveBeenCalledWith('e-2');
    expect(toastRun).toHaveBeenCalledTimes(1);
    const command = toastRun.mock.calls[0][0] as { label: string };
    expect(command.label).toBe('2 Veranstaltungen gelöscht');
  });
});
