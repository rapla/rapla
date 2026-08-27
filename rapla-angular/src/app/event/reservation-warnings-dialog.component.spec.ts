import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { describe, expect, it, vi } from 'vitest';

import {
  ReservationWarningsDialogComponent,
  type WarningsDialogData,
} from './reservation-warnings-dialog.component';

/**
 * PRD 105 — the dialog is the ONE presentation for every write path, so two things must hold in the
 * template: a blocking finding offers no way to save, and a CONFLICT names WHAT it clashes with
 * (Swing shows the clashing bookings; a bare "erzeugt Konflikte" is what made the first attempt
 * useless).
 */
describe('ReservationWarningsDialogComponent', () => {
  async function render(data: WarningsDialogData) {
    const close = vi.fn();
    TestBed.configureTestingModule({
      imports: [ReservationWarningsDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    const fixture = TestBed.createComponent(ReservationWarningsDialogComponent);
    await fixture.whenStable();
    return { fixture, close, el: fixture.nativeElement as HTMLElement };
  }

  it('lists the clashing bookings under a CONFLICT finding', async () => {
    const { el } = await render({
      warnings: [{ code: 'CONFLICT', args: [], severity: 'CONFIRMABLE' }],
      conflicts: [
        { allocatableName: 'Raum A66', otherEventName: 'Physik II', when: '2026-09-14T07:00:00' },
        { allocatableName: 'Beamer 04', otherEventName: null, when: '2026-09-15T09:00:00' },
      ],
    });

    const text = el.textContent ?? '';
    expect(text).toContain('Raum A66');
    expect(text).toContain('Physik II');
    // §12: an unreadable counterparty is masked, not hidden — the resource IS busy
    expect(text).toContain('Beamer 04');
    expect(text).toContain('belegt (nicht einsehbar)');
  });

  it('a blocking finding offers no way to save', async () => {
    const { el } = await render({
      warnings: [{ code: 'NO_RESERVATION_NAME', args: [], severity: 'BLOCKING' }],
      conflicts: [],
    });

    const labels = [...el.querySelectorAll('button')].map((b) => b.textContent?.trim());
    expect(labels).toContain('Verstanden');
    expect(labels.some((l) => l?.includes('Trotzdem'))).toBe(false);
  });

  it('confirmable findings can be confirmed, and the answer is the dialog result', async () => {
    const { el, close } = await render({
      warnings: [{ code: 'CONFLICT', args: [], severity: 'CONFIRMABLE' }],
      conflicts: [],
    });

    const save = [...el.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Trotzdem'),
    );
    save?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(close).toHaveBeenCalledWith(true);
  });
});
