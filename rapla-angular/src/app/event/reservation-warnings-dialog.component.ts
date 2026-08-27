import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import { isBlocked, warningText, type ReservationWarning } from './reservation-warnings';

/** One clashing booking, as `potentialConflicts` reports it (§12: side 2 may be masked). */
export interface ConflictDetail {
  allocatableName: string;
  otherEventName: string | null;
  when: string;
}

export interface WarningsDialogData {
  warnings: ReservationWarning[];
  conflicts: ConflictDetail[];
}

/**
 * PRD 105 — the ONE presentation of pre-save findings, for every write path: sheet save, drag,
 * resize, bulk actions. Swing does the same (`ReservationControllerImpl.checkEvents` is called from
 * each write path and always opens a dialog); an inline panel would have to be re-invented for the
 * paths that have no sheet to live in.
 *
 * <p>Dumb by design: it renders what it is handed and answers save/cancel. A blocking finding
 * removes the save option — the user cannot confirm their way past it.
 */
@Component({
  selector: 'app-reservation-warnings-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ blocked ? 'Speichern nicht möglich' : 'Vor dem Speichern prüfen' }}</h2>
    <mat-dialog-content>
      <ul class="findings">
        @for (w of data.warnings; track w.code + w.args.join('|')) {
          <li>
            {{ text(w) }}
            @if (w.code === 'CONFLICT' && data.conflicts.length > 0) {
              <ul class="conflicts">
                @for (c of data.conflicts; track c.allocatableName + c.when) {
                  <li>
                    <strong>{{ c.allocatableName }}</strong> — {{ c.when }} —
                    {{ c.otherEventName ?? 'belegt (nicht einsehbar)' }}
                  </li>
                }
              </ul>
            }
          </li>
        }
      </ul>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" (click)="close(false)">
        {{ blocked ? 'Verstanden' : 'Abbrechen' }}
      </button>
      @if (!blocked) {
        <button matButton type="button" class="primary" (click)="close(true)">
          Trotzdem speichern
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [
    `
      .findings {
        margin: 0;
        padding-left: 1.1rem;
      }
      .conflicts {
        margin: 0.25rem 0 0.5rem;
        padding-left: 1rem;
        font-size: 0.85rem;
        color: var(--mat-sys-on-surface-variant, #555);
      }
      button.primary {
        font-weight: 600;
      }
    `,
  ],
})
export class ReservationWarningsDialogComponent {
  readonly data = inject<WarningsDialogData>(MAT_DIALOG_DATA);
  private readonly ref =
    inject<MatDialogRef<ReservationWarningsDialogComponent, boolean>>(MatDialogRef);

  readonly blocked = isBlocked(this.data.warnings);

  text(warning: ReservationWarning): string {
    return warningText(warning);
  }

  close(proceed: boolean): void {
    this.ref.close(proceed);
  }
}
