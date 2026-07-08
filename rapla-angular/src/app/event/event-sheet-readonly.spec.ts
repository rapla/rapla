import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { EventSheetComponent, type EventSheetDialogData } from './event-sheet.component';

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
