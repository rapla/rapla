import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { EventSheetComponent, type EventSheetDialogData } from './event-sheet.component';
import { EventDataService } from './event-data.service';
import { newDraft } from './event-draft';
import { of } from 'rxjs';

describe('EventSheetComponent — readOnly dialog mode (PRD 094 Phase 1)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventSheetComponent],
      providers: [
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { id: 'e-view-1', readOnly: true } satisfies EventSheetDialogData,
        },
        { provide: MatDialogRef, useValue: { close: () => undefined } },
      ],
    }).compileComponents();
  });

  it('readOnly dialog data forces canModify off before and after load', async () => {
    const fixture = TestBed.createComponent(EventSheetComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.canModify()).toBe(false);
  });
});

/**
 * PRD 119 D9 — a search hit opens the sheet with `{ id, searchHint }` and no readOnly flag; whether it can be edited
 * comes from the loaded event, so an event the caller may read but not change opens read-only.
 */
describe('EventSheetComponent — opened from a search hit (PRD 119 D9)', () => {
  let loadedCanModify = false;

  beforeEach(async () => {
    const draft = { ...newDraft('event', new Date('2026-09-01T10:00:00')), persisted: true };
    await TestBed.configureTestingModule({
      imports: [EventSheetComponent],
      providers: [
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { id: 'e-hit-1', searchHint: true } satisfies EventSheetDialogData,
        },
        { provide: MatDialogRef, useValue: { close: () => undefined } },
        {
          provide: EventDataService,
          useValue: {
            load: () => of({ draft, canModify: loadedCanModify }),
            save: () => of({ kind: 'ok' }),
          },
        },
      ],
    }).compileComponents();
  });

  async function openedCanModify(): Promise<boolean> {
    const fixture = TestBed.createComponent(EventSheetComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.componentInstance.canModify();
  }

  it('an event the caller cannot modify opens read-only', async () => {
    loadedCanModify = false;
    expect(await openedCanModify()).toBe(false);
  });

  it('an event the caller can modify opens editable', async () => {
    loadedCanModify = true;
    expect(await openedCanModify()).toBe(true);
  });
});
