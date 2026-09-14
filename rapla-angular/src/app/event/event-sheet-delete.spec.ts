import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { EventSheetComponent } from './event-sheet.component';
import { EventDataService } from './event-data.service';
import { UndoToastService } from '../actions/undo-toast.service';
import { newDraft } from './event-draft';
import { DeleteScopeDialogComponent } from '../views/delete-scope-dialog.component';

/**
 * Tier-6 — one action, one confirmation. Deleting an event from the row menu asks
 * („… wirklich löschen?", `DeleteScopeDialogComponent`); the sheet's Löschen button ran the same
 * destructive command with no question at all. Two entry points to the same action must not differ
 * in whether they ask.
 */
describe('EventSheetComponent — delete confirmation', () => {
  const open = vi.fn();
  const run = vi.fn();

  beforeEach(async () => {
    open.mockReset();
    run.mockReset();
    window.history.replaceState({ isNew: false }, '');
    const draft = { ...newDraft('event', new Date('2026-09-01T10:00:00')), persisted: true };
    draft.values['name'] = 'Physik II';

    await TestBed.configureTestingModule({
      imports: [EventSheetComponent],
      providers: [
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open } },
        { provide: UndoToastService, useValue: { run, mutated$: of() } },
        {
          provide: EventDataService,
          useValue: { load: () => of({ draft, canModify: true }), save: () => of({ kind: 'ok' }) },
        },
      ],
    }).compileComponents();
  });

  async function sheet() {
    const fixture = TestBed.createComponent(EventSheetComponent);
    fixture.componentRef.setInput('id', 'e-delete-1');
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .match(() => true)
      .forEach((r) => r.flush({ data: {} }));
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('asks with the SAME dialog the row menu uses, and does not delete on abort', async () => {
    open.mockReturnValue({ afterClosed: () => of(undefined) });
    const fixture = await sheet();

    fixture.componentInstance.deleteEvent();
    await fixture.whenStable();

    expect(open).toHaveBeenCalledTimes(1);
    expect(open.mock.calls[0][0]).toBe(DeleteScopeDialogComponent);
    expect(run).not.toHaveBeenCalled();
  });

  it('deletes once the user confirms', async () => {
    open.mockReturnValue({ afterClosed: () => of('event') });
    const fixture = await sheet();

    fixture.componentInstance.deleteEvent();
    await fixture.whenStable();

    expect(run).toHaveBeenCalledTimes(1);
  });
});
