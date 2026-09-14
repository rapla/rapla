import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import type { BindCandidate } from './import-models';

export interface BindTargetDialogData {
  sourceLabel: string;
  candidates: BindCandidate[];
}

/** Chosen target: a concrete reservation, or 'pick' = click it in the calendar. */
export type BindTargetResult = { reservationId: string; targetName: string } | 'pick';

/**
 * PRD 104 v3 — THE one "verknüpfen" dialog (Option B, user decision 2026-08-11):
 * every bind from an open row goes through this single flow — server proposals as
 * a selectable list, "pick in calendar" as the last option, the overwrite warning
 * inline. Nothing is preselected (proposals are suggestions, never auto-matches),
 * and only the explicit calendar option closes the sync dialog underneath.
 */
@Component({
  selector: 'app-bind-target-dialog',
  imports: [MatDialogModule],
  template: `
    <h2 mat-dialog-title>„{{ data.sourceLabel }}" verknüpfen</h2>
    <mat-dialog-content>
      @if (data.candidates.length > 0) {
        <p class="section">Vorschläge</p>
        @for (c of data.candidates; track c.reservationId) {
          <button
            type="button"
            class="option"
            [class.sel]="selected() === c.reservationId"
            (click)="selected.set(c.reservationId)"
          >
            <span class="name">{{ c.name }}</span>
            @if (c.firstDate) {
              <span class="date">ab {{ c.firstDate.slice(0, 10) }}</span>
            }
          </button>
        }
      } @else {
        <p class="empty">Keine Vorschläge im angezeigten Zeitraum.</p>
      }
      <button
        type="button"
        class="option"
        [class.sel]="selected() === 'pick'"
        (click)="selected.set('pick')"
      >
        <span class="name">Ziel im Kalender anklicken…</span>
      </button>
      <p class="warn">
        Beim Verknüpfen überschreiben die Dualis-Felder (Name, Nummer, Art, …) die bisherigen Werte
        der Ziel-Veranstaltung. Das lässt sich derzeit nicht rückgängig machen.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-dialog-close>Abbrechen</button>
      <button class="primary" [disabled]="!selected()" (click)="confirm()">Verknüpfen</button>
    </mat-dialog-actions>
  `,
  styles: `
    :host {
      display: block;
      min-width: 380px;
      max-width: 520px;
    }
    .section {
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--mat-sys-on-surface-variant);
      margin: 0 0 4px;
    }
    .option {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 10px;
      width: 100%;
      text-align: left;
      font: inherit;
      color: inherit;
      background: none;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 7px;
      padding: 7px 10px;
      margin-bottom: 6px;
      cursor: pointer;
    }
    .option.sel {
      border-color: var(--mat-sys-primary);
      background: var(--mat-sys-primary-container);
    }
    .date {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
      white-space: nowrap;
    }
    .empty {
      font-size: 13px;
      color: var(--mat-sys-on-surface-variant);
    }
    .warn {
      color: var(--mat-sys-error);
      font-size: 12.5px;
      margin-top: 8px;
    }
    mat-dialog-actions button {
      font: inherit;
      border: 1px solid var(--mat-sys-outline-variant);
      background: none;
      color: inherit;
      border-radius: 8px;
      padding: 5px 14px;
      cursor: pointer;
    }
    mat-dialog-actions button.primary {
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      border-color: transparent;
    }
    mat-dialog-actions button:disabled {
      opacity: 0.5;
      cursor: default;
    }
  `,
})
export class BindTargetDialogComponent {
  readonly data = inject<BindTargetDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<BindTargetDialogComponent, BindTargetResult>>(MatDialogRef);

  /** reservationId of a proposal, or 'pick'. */
  readonly selected = signal<string | null>(null);

  confirm(): void {
    const sel = this.selected();
    if (!sel) return;
    if (sel === 'pick') {
      this.dialogRef.close('pick');
      return;
    }
    const c = this.data.candidates.find((x) => x.reservationId === sel);
    if (c) this.dialogRef.close({ reservationId: c.reservationId, targetName: c.name });
  }
}
