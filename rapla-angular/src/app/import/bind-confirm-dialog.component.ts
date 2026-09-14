import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export interface BindConfirmDialogData {
  sourceLabel: string;
  targetName: string;
  /** Extra consequence line (e.g. "das geparkte Duplikat wird gelöscht"). */
  note?: string;
}

/**
 * PRD 104 v3 "verknüpfen" — confirmation before the bind fires: binding stamps
 * AND overwrites the target's classification with the Dualis field set, there is
 * no undo yet (unbind is future server work) and the target pick is manual —
 * exactly the workflow where same-number mix-ups happen (T3INF3001.2 lesson).
 */
@Component({
  selector: 'app-bind-confirm-dialog',
  imports: [MatDialogModule],
  template: `
    <h2 mat-dialog-title>Verknüpfen bestätigen</h2>
    <mat-dialog-content>
      <p>
        <strong>„{{ data.sourceLabel }}"</strong> (Dualis) mit
        <strong>„{{ data.targetName }}"</strong> (Kalender) verknüpfen?
      </p>
      <p class="warn">
        Die Dualis-Felder (Name, Nummer, Art, …) überschreiben die bisherigen Werte der
        Ziel-Veranstaltung. Das lässt sich derzeit nicht rückgängig machen.
      </p>
      @if (data.note) {
        <p class="warn">{{ data.note }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-dialog-close>Abbrechen</button>
      <button class="primary" [mat-dialog-close]="true">Verknüpfen</button>
    </mat-dialog-actions>
  `,
  styles: `
    :host {
      display: block;
      max-width: 440px;
    }
    .warn {
      color: var(--mat-sys-error);
      font-size: 13px;
    }
    button {
      font: inherit;
      border: 1px solid var(--mat-sys-outline-variant);
      background: none;
      color: inherit;
      border-radius: 8px;
      padding: 5px 14px;
      cursor: pointer;
    }
    button.primary {
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      border-color: transparent;
    }
  `,
})
export class BindConfirmDialogComponent {
  readonly data = inject<BindConfirmDialogData>(MAT_DIALOG_DATA);
}
