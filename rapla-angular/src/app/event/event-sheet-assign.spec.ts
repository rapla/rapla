import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { EventSheetComponent, type EventSheetDialogData } from './event-sheet.component';
import { EventDataService } from './event-data.service';
import { GraphqlService } from '../graphql/graphql.service';
import { newDraft } from './event-draft';

const room = (i: number) => ({
  id: `r${i}`,
  kind: 'RESOURCE',
  name: `Raum ${String(i).padStart(2, '0')}`,
  classification: { typeKey: 'room', type: { name: 'Raum' } },
});

/**
 * PRD 123 D1/D2 — the sheet's add mode is the shared picker in assign mode: a click assigns and
 * collapses, Ctrl-click assigns and stays, a click on an assigned row unassigns, the
 * "+ Ressource…" button toggles. Availability comes by ids for the rendered rows.
 */
describe('EventSheetComponent — assign from the shared picker (PRD 123)', () => {
  const availabilityCalls: string[][] = [];

  beforeEach(async () => {
    localStorage.clear();
    availabilityCalls.length = 0;
    const draft = { ...newDraft('event', new Date('2026-09-01T10:00:00')), persisted: true };
    const query = vi.fn((q: string, vars?: Record<string, unknown>) => {
      if (q.includes('resourceAvailability')) {
        const ids = (
          (vars?.['input'] as { candidates: { ids: string[] } }).candidates.ids ?? []
        ).slice();
        availabilityCalls.push(ids);
        return of({
          data: {
            resourceAvailability: ids.map((id) => ({
              resource: { id, name: id },
              status: 'AVAILABLE',
              conflictingAppointmentIds: [],
            })),
          },
        });
      }
      if (q.includes('resources')) {
        return of({ data: { resources: [room(1), room(2), room(3)], users: [] } });
      }
      return of({ data: {} });
    });
    await TestBed.configureTestingModule({
      imports: [EventSheetComponent],
      providers: [
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { id: 'e-1' } satisfies EventSheetDialogData },
        { provide: MatDialogRef, useValue: { close: () => undefined } },
        { provide: GraphqlService, useValue: { query, mutate: vi.fn() } },
        {
          provide: EventDataService,
          useValue: { load: () => of({ draft, canModify: true }), save: () => of({ kind: 'ok' }) },
        },
      ],
    }).compileComponents();
  });

  async function open(): Promise<ComponentFixture<EventSheetComponent>> {
    const f = TestBed.createComponent(EventSheetComponent);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    (f.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.addtoggle')!.click();
    await settle(f);
    // PRD 127 D5 — the resources sit in a closed type folder.
    (f.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('app-resource-picker .grouprow .toggle')!
      .click();
    await settle(f);
    return f;
  }

  async function settle(f: ComponentFixture<EventSheetComponent>): Promise<void> {
    f.detectChanges();
    await f.whenStable();
    await new Promise((r) => setTimeout(r, 300)); // past the availability debounce
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
  }

  const rows = (f: ComponentFixture<EventSheetComponent>) =>
    Array.from(
      (f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('app-resource-picker .item'),
    );
  const assigned = (f: ComponentFixture<EventSheetComponent>) =>
    f.componentInstance.draft()?.allocations.map((a) => a.resourceId) ?? [];
  const pickerOpen = (f: ComponentFixture<EventSheetComponent>) =>
    (f.nativeElement as HTMLElement).querySelector('app-resource-picker') !== null;

  it('a click assigns and collapses the picker; the button toggles it back open', async () => {
    const f = await open();
    expect(pickerOpen(f)).toBe(true);
    expect(
      (f.nativeElement as HTMLElement).querySelector('button.addtoggle')?.textContent,
    ).toContain('einklappen');
    rows(f)[0].click();
    await settle(f);
    expect(assigned(f)).toEqual(['r1']);
    expect(pickerOpen(f)).toBe(false);
    expect(
      (f.nativeElement as HTMLElement).querySelector('button.addtoggle')?.textContent,
    ).toContain('+ Ressource');
  });

  it('Ctrl-click assigns and keeps the picker open; a click on the assigned row unassigns', async () => {
    const f = await open();
    rows(f)[0].dispatchEvent(new MouseEvent('click', { ctrlKey: true, bubbles: true }));
    await settle(f);
    rows(f)[1].dispatchEvent(new MouseEvent('click', { ctrlKey: true, bubbles: true }));
    await settle(f);
    expect(assigned(f)).toEqual(['r1', 'r2']);
    expect(pickerOpen(f)).toBe(true);
    expect(rows(f).map((r) => r.classList.contains('selected'))).toEqual([true, true, false]);
    rows(f)[0].click();
    await settle(f);
    expect(assigned(f)).toEqual(['r2']);
    expect(pickerOpen(f)).toBe(true);
  });

  it('availability is fetched by ids for the rendered rows and shown as pills', async () => {
    const f = await open();
    expect(availabilityCalls.at(-1)).toEqual(['r1', 'r2', 'r3']);
    expect(rows(f).map((r) => r.querySelector('.pill')?.textContent?.trim())).toEqual([
      'frei',
      'frei',
      'frei',
    ]);
    expect((f.nativeElement as HTMLElement).querySelector('app-resource-picker .star')).toBeNull();
  });

  it('a reopened picker keeps the folders opened before (user, 2026-10-04)', async () => {
    const f = await open();
    rows(f)[0].click();
    await settle(f);
    expect(pickerOpen(f)).toBe(false);
    (f.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.addtoggle')!.click();
    await settle(f);
    expect(rows(f).length).toBe(3);
  });
});
