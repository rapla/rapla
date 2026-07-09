import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import type { MoveScope, MoveScopeOption } from './move-scope';

export interface MoveScopeDialogData {
  question: string;
  confirmLabel: string;
  options: MoveScopeOption[];
}

/**
 * PRD 101 Phase 5 — the move/resize scope chooser (the Swing EVENT/SERIE/SINGLE
 * dialog that runs after a drop). Sibling of {@link DeleteScopeDialogComponent};
 * kept separate so its labels ("verschieben" / "Größe ändern") read right.
 * Closes with the chosen {@link MoveScope} or undefined on abort.
 */
@Component({
  selector: 'app-move-scope-dialog',
  imports: [MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.confirmLabel }}</h2>
    <mat-dialog-content>
      <p class="question">{{ data.question }}</p>
      @for (option of data.options; track option.scope) {
        <label class="option">
          <input
            type="radio"
            name="scope"
            [value]="option.scope"
            [checked]="selected() === option.scope"
            (change)="selected.set(option.scope)"
          />
          <span>{{ option.label }}</span>
        </label>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton (click)="ref.close(undefined)">Abbrechen</button>
      <button matButton="filled" class="confirm" (click)="ref.close(selected())">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .question {
        margin: 0 0 0.75rem;
      }
      .option {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.3rem 0;
        cursor: pointer;
      }
    `,
  ],
})
export class MoveScopeDialogComponent {
  readonly data = inject<MoveScopeDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject<MatDialogRef<MoveScopeDialogComponent, MoveScope | undefined>>(MatDialogRef);
  readonly selected = signal<MoveScope>(this.data.options[0]?.scope ?? 'event');
}
