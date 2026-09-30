import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import { TPipe } from '../i18n/i18n.service';
import type { DeleteScope, DeleteScopeOption } from './delete-scope';

export interface DeleteScopeDialogData {
  eventName: string;
  options: DeleteScopeOption[];
}

/**
 * PRD 094 Phase 2 — the Swing delete-scope chooser ("Was wollen Sie
 * löschen?"). With exactly one option it degrades to a plain confirm.
 * Closes with the chosen {@link DeleteScope} or undefined on abort.
 */
@Component({
  selector: 'app-delete-scope-dialog',
  imports: [MatDialogModule, MatButtonModule, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ 'delete' | t }}</h2>
    <mat-dialog-content>
      @if (data.options.length > 1) {
        <p class="question">{{ 'view_delete_which' | t: data.eventName }}</p>
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
      } @else {
        <p class="question">{{ 'view_delete_confirm' | t: data.eventName }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton (click)="ref.close(undefined)">{{ 'cancel' | t }}</button>
      <button matButton="filled" class="confirm" (click)="ref.close(selected())">
        {{ 'delete' | t }}
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
export class DeleteScopeDialogComponent {
  readonly data = inject<DeleteScopeDialogData>(MAT_DIALOG_DATA);
  readonly ref =
    inject<MatDialogRef<DeleteScopeDialogComponent, DeleteScope | undefined>>(MatDialogRef);
  readonly selected = signal<DeleteScope>(this.data.options[0]?.scope ?? 'event');
}
